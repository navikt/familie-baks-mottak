package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.RestFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakIdOgTilknyttetAktørId
import no.nav.familie.baks.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.baks.mottak.integrasjoner.SakClient
import no.nav.familie.baks.mottak.integrasjoner.Sivilstand
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.SIVILSTAND
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.UTFLYTTING
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.StatusEnum
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND.GIFT
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.error.RekjørSenereException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Service
@TaskStepBeskrivelse(
    taskStepType = VurderLivshendelseTask.TASK_STEP_TYPE,
    beskrivelse = "Vurder livshendelse",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 3600,
)
class VurderLivshendelseTask(
    private val oppgaveClient: OppgaveClient,
    private val pdlClient: PdlClient,
    private val sakClient: SakClient,
    private val infotrygdClient: InfotrygdBarnetrygdClient,
) : AsyncTaskStep {

    private val tema = Tema.BAR

    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val secureLog: Logger = LoggerFactory.getLogger("secureLogger")
    val oppgaveOpprettetDødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall.oppgave.opprettet")
    val oppgaveOpprettetUtflyttingCounter: Counter = Metrics.counter("barnetrygd.utflytting.oppgave.opprettet")
    val oppgaveOpprettetSivilstandCounter: Counter = Metrics.counter("barnetrygd.sivilstand.oppgave.opprettet")

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        val personIdent = payload.personIdent

        when (payload.type) {
            DØDSFALL -> {
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

                val berørteBrukereIBaSak = finnBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(personIdent)
                secureLog.info(
                    "berørteBrukereIBaSak count = ${berørteBrukereIBaSak.size}, aktørIder = ${
                        berørteBrukereIBaSak.fold("") { aktørIder, it -> aktørIder + " " + it.aktørId }
                    }",
                )
                berørteBrukereIBaSak.forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(
                            hendelseType = DØDSFALL,
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
            UTFLYTTING -> {
                finnBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(personIdent).forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(
                            hendelseType = UTFLYTTING,
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
            SIVILSTAND -> {
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-sivilstand", tema)
                val sivilstand = finnNyesteSivilstandEndring(pdlPersonData) ?: run {
                    secureLog.info("Ignorerer sivilstandhendelse for $personIdent uten dato: $pdlPersonData")
                    return
                }
                if (sivilstand.type != GIFT) {
                    secureLog.info("Endringen til sivilstand GIFT for $personIdent er korrigert/annulert: $pdlPersonData")
                    return
                }
                finnBrukereBerørtAvSivilstandHendelseForIdent(personIdent).forEach {
                    if (sjekkOmDatoErEtterEldsteVedtaksdato(dato = sivilstand.dato!!, aktivFaksak = hentRestFagsak(it.fagsakId), personIdent = personIdent)) { // Trenger denne sjekken for å unngå "gamle" hendelser som feks kan skyldes innflytting
                        opprettEllerOppdaterEndringISivilstandOppgave(
                            endringsdato = sivilstand.dato!!,
                            fagsakIdForOppgave = it.fagsakId,
                            aktørIdForOppgave = it.aktørId,
                            personIdent = personIdent,
                            task = task,
                        )
                    }
                }
            }
            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }

    private fun sjekkOmDatoErEtterEldsteVedtaksdato(dato: LocalDate, aktivFaksak: RestFagsak, personIdent: String): Boolean {
        val tidligsteVedtakIBaSak = aktivFaksak.behandlinger
            .filter { it.resultat == RESULTAT_INNVILGET && it.steg == STEG_TYPE_BEHANDLING_AVSLUTTET }
            .minByOrNull { it.opprettetTidspunkt } ?: return false

        if (dato.isAfter(tidligsteVedtakIBaSak.opprettetTidspunkt.toLocalDate())) {
            return true
        }

        val erEtterTidligsteInfotrygdVedtak = if (tidligsteVedtakIBaSak.type == BEHANDLING_TYPE_MIGRERING) {
            hentTidligsteVedtaksdatoFraInfotrygd(personIdent)?.isBefore(dato) ?: false
        } else {
            false
        }

        return erEtterTidligsteInfotrygdVedtak
    }

    private fun hentTidligsteVedtaksdatoFraInfotrygd(personIdent: String): LocalDate? {
        val personIdenter = pdlClient.hentIdenter(personIdent, tema)
            .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
            .map { it.ident }
        val tidligsteInfotrygdVedtak = infotrygdClient.hentVedtak(personIdenter).bruker
            .maxByOrNull { it.iverksattFom ?: "000000" } // maxBy... siden datoen er på "seq"-format
        return tidligsteInfotrygdVedtak?.iverksattFom
            ?.let { YearMonth.parse("${999999 - it.toInt()}", DateTimeFormatter.ofPattern("yyyyMM")) }
            ?.atDay(1)
    }

    private fun finnNyesteSivilstandEndring(pdlPersonData: PdlPersonData): Sivilstand? {
        return pdlPersonData.sivilstand.filter { it.dato != null }.maxByOrNull { it.dato!! }
    }

    private fun finnBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(
        personIdent: String,
    ): List<RestFagsakIdOgTilknyttetAktørId> {
        val listeMedFagsakIdOgTilknyttetAktør = sakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent)
        secureLog.info("Aktører og fagsaker berørt av hendelse for personIdent=$personIdent: ${listeMedFagsakIdOgTilknyttetAktør.map { "(aktørId=${it.aktørId}, fagsakId=${it.fagsakId}),"}}")
        return listeMedFagsakIdOgTilknyttetAktør
    }

    private fun finnBrukereBerørtAvSivilstandHendelseForIdent(
        personIdent: String,
    ): List<RestFagsakIdOgTilknyttetAktørId> {
        val listeMedFagsakIdOgTilknyttetAktørId = sakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(personIdent)
        secureLog.info("Aktører og fagsaker berørt av hendelse for personIdent=$personIdent: ${listeMedFagsakIdOgTilknyttetAktørId.map { "(aktørId=${it.aktørId}, fagsakId=${it.fagsakId})," }}")
        return listeMedFagsakIdOgTilknyttetAktørId
    }

    private fun opprettEllerOppdaterVurderLivshendelseOppgave(
        hendelseType: VurderLivshendelseType,
        aktørIdForOppgave: String,
        fagsakIdForOppgave: Long,
        personIdent: String,
        task: Task,
    ): Boolean {
        val åpenOppgave = søkEtterÅpenOppgavePåAktør(aktørIdForOppgave, hendelseType)

        if (åpenOppgave == null) {
            val beskrivelse = leggTilNyPersonIBeskrivelse(
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
            val beskrivelse = leggTilNyPersonIBeskrivelse(
                beskrivelse = åpenOppgave.beskrivelse!!,
                personIdent = personIdent,
                personErBruker = åpenOppgave.identer?.map { it.ident }
                    ?.contains(personIdent),
            )

            oppdaterOppgaveMedNyBeskrivelse(åpenOppgave, beskrivelse)
            task.metadata["oppgaveId"] = åpenOppgave.id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            return false
        }
    }

    private fun hentInitiellBeskrivelseForSivilstandOppgave(personErBruker: Boolean, formatertDato: String, personIdent: String): String =
        "${SIVILSTAND.beskrivelse}: ${if (personErBruker) "bruker" else "barn $personIdent"} er registrert som gift fra $formatertDato"

    private fun opprettEllerOppdaterEndringISivilstandOppgave(
        endringsdato: LocalDate,
        fagsakIdForOppgave: Long,
        aktørIdForOppgave: String,
        personIdent: String,
        task: Task,
    ) {
        val formatertDato = endringsdato.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale("no")),
        ) ?: "ukjent dato"

        val initiellBeskrivelse = hentInitiellBeskrivelseForSivilstandOppgave(
            personErBruker = pdlClient.hentAktørId(personIdent, tema) == aktørIdForOppgave,
            formatertDato = formatertDato,
            personIdent = personIdent,
        )

        val oppgave = søkEtterÅpenOppgavePåAktør(aktørIdForOppgave, SIVILSTAND)
            ?: opprettOppgavePåAktør(
                aktørId = aktørIdForOppgave,
                fagsakId = fagsakIdForOppgave,
                beskrivelse = initiellBeskrivelse,
                behandlingstema = Behandlingstema.UtvidetBarnetrygd,
            )

        when (oppgave) {
            is OppgaveResponse -> {
                secureLog.info(
                    "Opprettet VurderLivshendelse-oppgave (${oppgave.oppgaveId}) for $SIVILSTAND-hendelse (person i hendelse:  $personIdent, oppgave på person: $aktørIdForOppgave)" +
                        ", beskrivelsestekst: $initiellBeskrivelse",
                )
                oppgaveOpprettetSivilstandCounter.increment()
                task.metadata["oppgaveId"] = oppgave.oppgaveId.toString()
            }
            is Oppgave -> {
                log.info("Fant åpen oppgave på aktørId=$aktørIdForOppgave oppgaveId=${oppgave.id}")
                secureLog.info("Fant åpen oppgave: $oppgave")
                oppdaterOppgaveMedNyBeskrivelse(oppgave = oppgave, beskrivelse = "${SIVILSTAND.beskrivelse}: Bruker eller barn er registrert som gift")
                task.metadata["oppgaveId"] = oppgave.id.toString()
                task.metadata["info"] = "Fant åpen oppgave"
            }
        }
    }

    private fun leggTilNyPersonIBeskrivelse(beskrivelse: String, personIdent: String, personErBruker: Boolean?): String {
        return when (personErBruker) {
            true -> if (!beskrivelse.contains("bruker")) leggTilBrukerIBeskrivelse(beskrivelse) else beskrivelse
            else -> if (!beskrivelse.contains(personIdent)) leggTilBarnIBeskrivelse(beskrivelse, personIdent) else beskrivelse
        }
    }

    private fun leggTilBrukerIBeskrivelse(beskrivelse: String): String {
        val (livshendelseType, barn) = beskrivelse.split(":")
        return "$livshendelseType: bruker" + if (barn.isNotEmpty()) " og $barn" else ""
    }

    private fun leggTilBarnIBeskrivelse(beskrivelse: String, personIdent: String): String {
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
        type: VurderLivshendelseType,
    ): Oppgave? {
        val vurderLivshendelseOppgaver = oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderLivshendelse)
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

    private fun hentRestFagsak(fagsakId: Long): RestFagsak {
        return sakClient.hentRestFagsak(fagsakId).also {
            secureLog.info("Hentet rest fagsak: $it")
        }
    }

    private fun hentSisteBehandlingSomErIverksatt(restFagsak: RestFagsak): RestUtvidetBehandling? {
        return restFagsak.behandlinger
            .filter { it.steg == STEG_TYPE_BEHANDLING_AVSLUTTET }
            .maxByOrNull { it.opprettetTidspunkt }
    }

    private fun hentAktivBehandling(restFagsak: RestFagsak): RestUtvidetBehandling {
        return restFagsak.behandlinger.firstOrNull { it.aktiv }
            ?: error("Fagsak ${restFagsak.id} mangler aktiv behandling. Får ikke opprettet VurderLivshendelseOppgave")
    }

    private fun tilBehandlingstema(restUtvidetBehandling: RestUtvidetBehandling?): Behandlingstema {
        return when {
            restUtvidetBehandling == null -> Behandlingstema.Barnetrygd
            restUtvidetBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.BarnetrygdEØS
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd
            else -> Behandlingstema.Barnetrygd
        }
    }

    private val PdlForeldreBarnRelasjon.erBarn: Boolean
        get() = this.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN

    private val Sivilstand.dato: LocalDate?
        get() = this.gyldigFraOgMed ?: this.bekreftelsesdato

    data class Bruker(val ident: String, val fagsakId: Long)

    companion object {

        const val TASK_STEP_TYPE = "vurderLivshendelseTask"

        const val STEG_TYPE_BEHANDLING_AVSLUTTET = "BEHANDLING_AVSLUTTET"
        const val RESULTAT_INNVILGET = "INNVILGET"
        const val BEHANDLING_TYPE_MIGRERING = "MIGRERING_FRA_INFOTRYGD"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType(val beskrivelse: String) {
    DØDSFALL("Dødsfall"),
    SIVILSTAND("Endring i sivilstand"),
    ADDRESSE("Addresse"),
    UTFLYTTING("Utflytting"),
}
