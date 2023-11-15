package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.RestFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakIdOgTilknyttetAktørId
import no.nav.familie.baks.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.StatusEnum
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.error.RekjørSenereException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@TaskStepBeskrivelse(
    taskStepType = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
    beskrivelse = "Vurder kontanstøtte livshendelse",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 3600,
)
class VurderKontantstøtteLivshendelseTask(
    private val oppgaveClient: OppgaveClient,
    private val pdlClient: PdlClient,
    private val ksSakClient: KsSakClient,
) : AsyncTaskStep {
    private val tema = Tema.KON

    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val secureLog: Logger = LoggerFactory.getLogger("secureLogger")
    val oppgaveOpprettetDødsfallCounter: Counter = Metrics.counter("kontantstotte.dodsfall.oppgave.opprettet")
    val oppgaveOpprettetUtflyttingCounter: Counter = Metrics.counter("kontantstotte.utflytting.oppgave.opprettet")

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderKontantstøtteLivshendelseTaskDTO::class.java)
        val personIdent = payload.personIdent

        when (payload.type) {
            VurderKontantstøtteLivshendelseType.DØDSFALL -> {
                secureLog.info("Har mottat dødsfallshendelse for person $personIdent")
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-dødsfall", tema)
                secureLog.info("dødsfallshendelse person følselsdato = ${pdlPersonData.fødsel.firstOrNull()}")
                if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato == null) {
                    secureLog.info("Har mottatt dødsfallshendelse uten dødsdato $pdlPersonData")
                    throw RekjørSenereException(
                        årsak = "Har mottatt dødsfallshendelse uten dødsdato",
                        triggerTid = LocalDateTime.now().plusDays(1),
                    )
                }

                val berørteBrukereIKsSak = finnKsBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(personIdent)
                secureLog.info(
                    "berørteBrukereIKsSak count = ${berørteBrukereIKsSak.size}, aktørIder = ${
                        berørteBrukereIKsSak.fold("") { aktørIder, it -> aktørIder + " " + it.aktørId }
                    }",
                )
                berørteBrukereIKsSak.forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(
                            hendelseType = VurderKontantstøtteLivshendelseType.DØDSFALL,
                            aktørIdForOppgave = it.aktørId,
                            fagsakIdForOppgave = it.fagsakId,
                            personIdent = personIdent,
                            task = task,
                        )
                    ) {
                        oppgaveOpprettetDødsfallCounter.increment()
                    }
                }
            }

            VurderKontantstøtteLivshendelseType.UTFLYTTING -> {
                finnKsBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(personIdent).forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(
                            hendelseType = VurderKontantstøtteLivshendelseType.UTFLYTTING,
                            aktørIdForOppgave = it.aktørId,
                            fagsakIdForOppgave = it.fagsakId,
                            personIdent = personIdent,
                            task = task,
                        )
                    ) {
                        oppgaveOpprettetUtflyttingCounter.increment()
                    }
                }
            }
        }
    }

    private fun finnKsBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(
        personIdent: String,
    ): List<RestFagsakIdOgTilknyttetAktørId> {
        val listeMedFagsakIdOgTilknyttetAktør = ksSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(personIdent)
        secureLog.info("Aktører og fagsaker berørt av hendelse for personIdent=$personIdent: ${listeMedFagsakIdOgTilknyttetAktør.map { "(aktørId=${it.aktørId}, fagsakId=${it.fagsakId})," }}")
        return listeMedFagsakIdOgTilknyttetAktør
    }

    private fun opprettEllerOppdaterVurderLivshendelseOppgave(
        hendelseType: VurderKontantstøtteLivshendelseType,
        aktørIdForOppgave: String,
        fagsakIdForOppgave: Long,
        personIdent: String,
        task: Task,
    ): Boolean {
        val åpenOppgave = søkEtterÅpenOppgavePåAktør(aktørIdForOppgave, hendelseType)

        if (åpenOppgave == null) {
            val beskrivelse =
                leggTilNyPersonIBeskrivelse(
                    beskrivelse = "${hendelseType.beskrivelse}:",
                    personIdent = personIdent,
                    personErBruker = pdlClient.hentAktørId(personIdent, tema) == aktørIdForOppgave,
                )
            val restFagsak = hentRestFagsak(fagsakIdForOppgave)
            val restBehandling = hentSisteBehandlingSomErIverksatt(restFagsak) ?: hentAktivBehandling(restFagsak)
            val behandlingstema = tilBehandlingstema(restBehandling)
            val oppgave = opprettOppgavePåAktør(aktørIdForOppgave, fagsakIdForOppgave, beskrivelse, behandlingstema)
            task.metadata["oppgaveId"] = oppgave.oppgaveId.toString()
            secureLog.info(
                "Opprettet VurderLivshendelse-oppgave (${oppgave.oppgaveId}) for $hendelseType-hendelse (person aktørId:  $aktørIdForOppgave)" +
                    ", beskrivelsestekst: $beskrivelse",
            )
            return true
        } else {
            log.info("Fant åpen oppgave på aktørId=$aktørIdForOppgave oppgaveId=${åpenOppgave.id}")
            secureLog.info("Fant åpen oppgave: $åpenOppgave")
            val beskrivelse =
                leggTilNyPersonIBeskrivelse(
                    beskrivelse = åpenOppgave.beskrivelse!!,
                    personIdent = personIdent,
                    personErBruker =
                        åpenOppgave.identer?.map { it.ident }
                            ?.contains(personIdent),
                )

            oppdaterOppgaveMedNyBeskrivelse(åpenOppgave, beskrivelse)
            task.metadata["oppgaveId"] = åpenOppgave.id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            return false
        }
    }

    private fun leggTilNyPersonIBeskrivelse(
        beskrivelse: String,
        personIdent: String,
        personErBruker: Boolean?,
    ): String {
        return when (personErBruker) {
            true -> if (!beskrivelse.contains("bruker")) leggTilBrukerIBeskrivelse(beskrivelse) else beskrivelse
            else -> if (!beskrivelse.contains(personIdent)) leggTilBarnIBeskrivelse(beskrivelse, personIdent) else beskrivelse
        }
    }

    private fun leggTilBrukerIBeskrivelse(beskrivelse: String): String {
        val (livshendelseType, barn) = beskrivelse.split(":")
        return "$livshendelseType: bruker" + if (barn.isNotEmpty()) " og $barn" else ""
    }

    private fun leggTilBarnIBeskrivelse(
        beskrivelse: String,
        personIdent: String,
    ): String {
        if (!beskrivelse.contains("barn")) {
            val (livshendelseType, bruker) = beskrivelse.split(":")
            return "$livshendelseType:" + (if (bruker.isNotEmpty()) "$bruker og" else "") + " barn $personIdent"
        } else {
            val (tekstenForanBarn, tekstenEtterBarn) = beskrivelse.split(" barn ")
            val indexOfBarnCount =
                tekstenForanBarn.indexOfFirst(Char::isDigit).let { if (it > 0) it else tekstenForanBarn.length }
            val nyBarnCount = tekstenForanBarn.substring(indexOfBarnCount).toIntOrNull()?.plus(1) ?: 2
            val nyTekstForanBarn = tekstenForanBarn.substring(0, indexOfBarnCount).trim() + " $nyBarnCount"
            return "$nyTekstForanBarn barn $tekstenEtterBarn $personIdent"
        }
    }

    private fun søkEtterÅpenOppgavePåAktør(
        aktørId: String,
        type: VurderKontantstøtteLivshendelseType,
    ): Oppgave? {
        val vurderLivshendelseOppgaver = oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderLivshendelse, Tema.KON)
        secureLog.info("Fant følgende oppgaver: $vurderLivshendelseOppgaver")
        return vurderLivshendelseOppgaver.firstOrNull {
            it.beskrivelse?.startsWith(type.beskrivelse) == true && (
                it.status != StatusEnum.FERDIGSTILT || it.status != StatusEnum.FEILREGISTRERT
            )
        }
    }

    private fun opprettOppgavePåAktør(
        aktørId: String,
        fagsakId: Long,
        beskrivelse: String,
        behandlingstema: Behandlingstema,
    ): OppgaveResponse {
        log.info("Oppretter oppgave for aktørId=$aktørId")

        return oppgaveClient.opprettVurderLivshendelseOppgave(
            OppgaveVurderLivshendelseDto(
                aktørId = aktørId,
                beskrivelse = beskrivelse,
                saksId = fagsakId.toString(),
                behandlingstema = behandlingstema.value,
            ),
        )
    }

    private fun oppdaterOppgaveMedNyBeskrivelse(
        oppgave: Oppgave,
        beskrivelse: String,
    ) {
        if (oppgave.beskrivelse == beskrivelse) return

        secureLog.info("Oppdaterer oppgave (${oppgave.id}) med beskrivelse: $beskrivelse")
        oppgaveClient.oppdaterOppgaveBeskrivelse(oppgave, beskrivelse)
    }

    private fun hentRestFagsak(fagsakId: Long): RestFagsak =
        ksSakClient.hentRestFagsak(fagsakId).also { secureLog.info("Hentet rest fagsak: $it") }

    private fun hentSisteBehandlingSomErIverksatt(restFagsak: RestFagsak): RestUtvidetBehandling? =
        restFagsak.behandlinger
            .filter { it.steg == STEG_TYPE_BEHANDLING_AVSLUTTET }
            .maxByOrNull { it.opprettetTidspunkt }

    private fun hentAktivBehandling(restFagsak: RestFagsak): RestUtvidetBehandling =
        restFagsak.behandlinger.firstOrNull { it.aktiv }
            ?: error("Fagsak ${restFagsak.id} mangler aktiv behandling. Får ikke opprettet VurderLivshendelseOppgave")

    private fun tilBehandlingstema(restUtvidetBehandling: RestUtvidetBehandling?): Behandlingstema =
        when {
            restUtvidetBehandling == null -> Behandlingstema.Kontantstøtte
            restUtvidetBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.KontantstøtteEØS
            else -> Behandlingstema.Kontantstøtte
        }

    data class Bruker(val ident: String, val fagsakId: Long)

    companion object {
        const val TASK_STEP_TYPE = "vurderKontantstøtteLivshendelseTask"
        const val STEG_TYPE_BEHANDLING_AVSLUTTET = "BEHANDLING_AVSLUTTET"
    }
}

data class VurderKontantstøtteLivshendelseTaskDTO(val personIdent: String, val type: VurderKontantstøtteLivshendelseType)

enum class VurderKontantstøtteLivshendelseType(val beskrivelse: String) {
    DØDSFALL("Dødsfall"),
    UTFLYTTING("Utflytting"),
}
