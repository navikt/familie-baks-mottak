package no.nav.familie.ba.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.BehandlesAvApplikasjon
import no.nav.familie.ba.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.ba.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.AVSLUTTET
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.ba.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.ba.mottak.integrasjoner.PdlPersonData
import no.nav.familie.ba.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.ba.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.UTFLYTTING
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.StatusEnum
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@TaskStepBeskrivelse(
        taskStepType = VurderLivshendelseTask.TASK_STEP_TYPE,
        beskrivelse = "Vurder livshendelse",
        maxAntallFeil = 3,
        triggerTidVedFeilISekunder = 3600
)
class VurderLivshendelseTask(
        private val oppgaveClient: OppgaveClient,
        private val taskRepository: TaskRepository,
        private val pdlClient: PdlClient,
        private val sakClient: SakClient,
        private val aktørClient: AktørClient
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val secureLog: Logger = LoggerFactory.getLogger("secureLogger")
    val oppgaveOpprettetDødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall.oppgave.opprettet")
    val oppgaveOpprettetUtflyttingCounter: Counter = Metrics.counter("barnetrygd.utflytting.oppgave.opprettet")

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        val personIdent = payload.personIdent

        when (payload.type) {
            DØDSFALL -> {
                secureLog.info("Har mottat dødsfallshendelse for person ${personIdent}")
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-dødsfall")
                secureLog.info("dødsfallshendelse person følselsdato = ${pdlPersonData.fødsel.firstOrNull()}")
                if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato != null) {
                    val berørteBrukereIBaSak = finnBrukereMedSakRelatertTilPerson(personIdent, pdlPersonData)
                    secureLog.info("berørteBrukereIBaSak count = ${berørteBrukereIBaSak.size}, identer = ${
                        berørteBrukereIBaSak.fold("") { identer, it -> identer + " " + it.ident }
                    }")
                    berørteBrukereIBaSak.forEach {
                        if (opprettEllerOppdaterVurderLivshendelseOppgave(DØDSFALL, it, personIdent, task)) {
                            log.error("Mottatt dødsfallshendelse på sak i BA-sak. Få saksbehandler til å se på oppgave av typen VurderLivshendelse") // TODO midlertidig error logg til man har fått dette inn i saksbehandlersrutinen.
                            oppgaveOpprettetDødsfallCounter.increment()
                        }
                    }
                } else {
                    secureLog.info("Har mottatt dødsfallshendelse uten dødsdato $pdlPersonData")
                    error("Har mottatt dødsfallshendelse uten dødsdato")
                }
            }
            UTFLYTTING -> {
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-utflytting")
                finnBrukereMedSakRelatertTilPerson(personIdent, pdlPersonData).forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(UTFLYTTING, it, personIdent, task)) {
                        log.error("Mottatt utflyttingshendelse på sak i BA-sak. Få saksbehandler til å se på oppgave av typen VurderLivshendelse") // TODO midlertidig error logg til man har fått dette inn i saksbehandlersrutinen.
                        oppgaveOpprettetUtflyttingCounter.increment()
                    }
                }
            }
            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }

    private fun finnBrukereMedSakRelatertTilPerson(
            personIdent: String,
            pdlPersonData: PdlPersonData
    ): Set<Bruker> {

        val brukereMedSakRelatertTilPerson = mutableSetOf<Bruker>()

        val familierelasjoner = pdlPersonData.forelderBarnRelasjon

        // Hvis person har barn, så sjekker man etter løpende sak på person
        val personHarBarn = familierelasjoner.filter { it.erBarn }.let { listeMedBarn ->
                secureLog.info("finnBrukereMedSakRelatertTilPerson(): listeMedBarn size = ${listeMedBarn.size} identer = " +
                                       listeMedBarn.fold("") { identer, it -> identer + " " + it.relatertPersonsIdent })
                listeMedBarn.isNotEmpty()
            }

        if (personHarBarn) {
            brukereMedSakRelatertTilPerson += sakClient.hentRestFagsakDeltagerListe(personIdent).filter {
                secureLog.info("finnBrukereMedSakRelatertTilPerson(): Hentet Fagsak for person ${personIdent}: ${it.fagsakId} ${it.fagsakStatus}")
                it.fagsakStatus != AVSLUTTET
            }.map { Bruker(personIdent, it.fagsakId) }
        }

        // Sjekker om foreldrene til person under 19 har en relatert sak.
        if (personErBarn(pdlPersonData)) {
            brukereMedSakRelatertTilPerson += finnForeldreMedLøpendeSak(personIdent, familierelasjoner)
        }

        secureLog.info("finnBrukereMedSakRelatertTilPerson(): brukere.size = ${brukereMedSakRelatertTilPerson.size} " +
                       "identer = ${brukereMedSakRelatertTilPerson.fold("") { identer, it -> identer + " " + it.ident }}")

        if (brukereMedSakRelatertTilPerson.isNotEmpty()) {
            log.info("Fant sak for person")
            secureLog.info("Fant sak for person $personIdent")
        }
        return brukereMedSakRelatertTilPerson
    }

    private fun personErBarn(pdlPersonData: PdlPersonData) = pdlPersonData.fødsel.isEmpty() || // Kan anta barn når data mangler
            pdlPersonData.fødsel.first().fødselsdato.isAfter(LocalDate.now().minusYears(19))

    private fun finnForeldreMedLøpendeSak(
        personIdent: String,
        familierelasjoner: List<PdlForeldreBarnRelasjon>
    ): List<Bruker> {
        return familierelasjoner.filter { !it.erBarn }.also { listeMedForeldreForBarn ->
            secureLog.info("finnForeldreMedLøpendeSak(): listeMedForeldreForBarn.size = ${listeMedForeldreForBarn.size} " +
                           "identer = ${listeMedForeldreForBarn.fold("") { identer, it -> identer + " " + it.relatertPersonsIdent }}")
        }.mapNotNull {
            sakClient.hentRestFagsakDeltagerListe(it.relatertPersonsIdent, barnasIdenter = listOf(personIdent))
                .filter { it.fagsakStatus != AVSLUTTET }
                .groupBy { it.fagsakId }.values
                .firstOrNull { it.inneholderBådeForelderOgBarn }
                ?.find { it.rolle == FORELDER }
        }.map { Bruker(it.ident, it.fagsakId) }
    }

    private fun opprettEllerOppdaterVurderLivshendelseOppgave(
        hendelseType: VurderLivshendelseType,
        bruker: Bruker,
        personIdent: String,
        task: Task
    ): Boolean {
        val aktørId = aktørClient.hentAktørId(bruker.ident)
        val åpenOppgave = søkEtterÅpenOppgavePåAktør(aktørId, hendelseType)

        if (åpenOppgave == null) {
            val beskrivelse = leggTilNyPersonIBeskrivelse(beskrivelse = "${hendelseType.beskrivelse}:",
                                                          personIdent = personIdent,
                                                          personErBruker = personIdent == bruker.ident)

            val oppgave = opprettOppgavePåAktør(aktørId, bruker.fagsakId, beskrivelse)
            task.metadata["oppgaveId"] = oppgave.oppgaveId.toString()
            taskRepository.saveAndFlush(task)
            secureLog.info(
                "Opprettet VurderLivshendelse-oppgave (${oppgave.oppgaveId}) for $hendelseType-hendelse (person ident:  ${bruker.ident})" +
                        ", beskrivelsestekst: $beskrivelse"
            )
            return true
        } else {
            log.info("Fant åpen oppgave på aktørId=$aktørId oppgaveId=${åpenOppgave.id}")
            secureLog.info("Fant åpen oppgave: $åpenOppgave")
            val beskrivelse = leggTilNyPersonIBeskrivelse(beskrivelse = åpenOppgave.beskrivelse!!,
                                                          personIdent = personIdent,
                                                          personErBruker = åpenOppgave.identer?.map { it.ident }?.contains(personIdent))

            if (beskrivelse != åpenOppgave.beskrivelse) {
                secureLog.info("Oppdaterer oppgave (${åpenOppgave.id}) med beskrivelse: $beskrivelse")
                oppgaveClient.oppdaterOppgaveBeskrivelse(åpenOppgave, beskrivelse)
            }
            task.metadata["oppgaveId"] = åpenOppgave.id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            taskRepository.saveAndFlush(task)
            return false
        }
    }

    private fun leggTilNyPersonIBeskrivelse(beskrivelse: String, personIdent: String, personErBruker: Boolean?): String {
        return when (personErBruker){
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
        type: VurderLivshendelseType
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
        beskrivelse: String
    ): OppgaveResponse {
        log.info("Oppretter oppgave for aktørId=$aktørId")
        val minimalRestFagsak = sakClient.hentMinimalRestFagsak(fagsakId)
        secureLog.info("Hentet minimal rest fagsak: $minimalRestFagsak")
        val restMinimalBehandling = minimalRestFagsak.behandlinger.firstOrNull { it.aktiv }

        if (restMinimalBehandling == null) {
            error("Fagsak ${fagsakId} mangler aktiv behandling. Får ikke opprettet VurderLivshendelseOppgave")
        }

        val restFagsak = sakClient.hentRestFagsak(fagsakId)
        val restUtvidetBehandling =
            restFagsak.behandlinger.firstOrNull { it.behandlingId == restMinimalBehandling.behandlingId }

       return oppgaveClient.opprettVurderLivshendelseOppgave(
            OppgaveVurderLivshendelseDto(
                aktørId = aktørId,
                beskrivelse = beskrivelse,
                saksId = fagsakId.toString(),
                behandlingstema = tilBehandlingstema(restUtvidetBehandling),
                behandlesAvApplikasjon = BehandlesAvApplikasjon.BA_SAK.applikasjon
            )
        )
    }

    private fun tilBehandlingstema(restUtvidetBehandling: RestUtvidetBehandling?): String {
        return when {
            restUtvidetBehandling == null -> Behandlingstema.Barnetrygd.value
            restUtvidetBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.BarnetrygdEØS.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd.value
            else -> Behandlingstema.Barnetrygd.value
        }
    }

    private val PdlForeldreBarnRelasjon.erBarn: Boolean
        get() = this.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN

    private val List<RestFagsakDeltager>.inneholderBådeForelderOgBarn: Boolean
        get() = this.map(RestFagsakDeltager::rolle).containsAll(listOf(FORELDER, BARN))

    data class Bruker(val ident: String, val fagsakId: Long)

    companion object {
        const val TASK_STEP_TYPE = "vurderLivshendelseTask"
    }
}
data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType(val beskrivelse: String) {
    DØDSFALL("Dødsfall"),
    SIVILSTAND("Sivilstand"),
    ADDRESSE("Addresse"),
    UTFLYTTING("Utflytting")
}
