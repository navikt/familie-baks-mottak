package no.nav.familie.ba.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.BehandlesAvApplikasjon
import no.nav.familie.ba.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.ba.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.AVSLUTTET
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.ba.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.PdlPersonData
import no.nav.familie.ba.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.ba.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
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
            VurderLivshendelseType.DØDSFALL -> {
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-dødsfall")
                if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato != null) {
                    finnRelatertePersonerMedSak(personIdent, pdlPersonData).forEach {
                        if (opprettVurderLivshendelseOppgave(it, task, BESKRIVELSE_DØDSFALL)) {
                            log.error("Mottatt dødsfallshendelse på sak i BA-sak. Få saksbehandler til å se på oppgave av typen VurderLivshendelse") //TODO midlertidig error logg til man har fått dette inn i saksbehandlersrutinen.
                            oppgaveOpprettetDødsfallCounter.increment()
                        }
                    }
                } else {
                    secureLog.info("Har mottatt dødsfallshendelse uten dødsdato $pdlPersonData")
                    error("Har mottatt dødsfallshendelse uten dødsdato")
                }
            }
            VurderLivshendelseType.UTFLYTTING -> {
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-utflytting")
                finnRelatertePersonerMedSak(personIdent, pdlPersonData).forEach {
                    if (opprettVurderLivshendelseOppgave(it, task, lagUtflyttingoppgavebeskrivelse(it.ident, personIdent))) {
                        log.error("Mottatt utflyttingshendelse på sak i BA-sak. Få saksbehandler til å se på oppgave av typen VurderLivshendelse") //TODO midlertidig error logg til man har fått dette inn i saksbehandlersrutinen.
                        oppgaveOpprettetUtflyttingCounter.increment()
                    }
                }
            }
            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }


    private fun finnRelatertePersonerMedSak(personIdent: String,
                                            pdlPersonData: PdlPersonData): Set<RestFagsakDeltager> {

        val personerMedSak = mutableSetOf<RestFagsakDeltager>()

        val familierelasjon = pdlPersonData.forelderBarnRelasjon
        //populerer en liste med barn for person. Hvis person har barn, så sjekker man etter løpende sak
        val listeMedBarn =
                familierelasjon.filter { it.minRolleForPerson != FORELDERBARNRELASJONROLLE.BARN }
                        .map { it.relatertPersonsIdent }
        if (listeMedBarn.isNotEmpty()) {
            personerMedSak += sakClient.hentRestFagsakDeltagerListe(personIdent).filter { it.fagsakStatus != AVSLUTTET }
        }

        //Sjekker om foreldrene til person under 19 har en løpende sak.
        if (pdlPersonData.fødsel.isEmpty() ||
            pdlPersonData.fødsel.first().fødselsdato.isAfter(LocalDate.now().minusYears(19))) {

            val listeMedForeldreForBarn =
                    familierelasjon.filter { it.minRolleForPerson == FORELDERBARNRELASJONROLLE.BARN }
                            .map { it.relatertPersonsIdent }

            listeMedForeldreForBarn.forEach {
                personerMedSak += sakClient.hentRestFagsakDeltagerListe(it).filter { it.fagsakStatus != AVSLUTTET }
            }
        }

        if (personerMedSak.isNotEmpty()) {
            log.info("Fant sak for person")
            secureLog.info("Fant sak for person $personIdent")
        }
        return personerMedSak
    }

    private fun opprettVurderLivshendelseOppgave(fagsakPerson: RestFagsakDeltager,
                                                 task: Task,
                                                 beskrivelse: String): Boolean {

        val (nyopprettet, oppgave) = hentEllerOpprettNyOppgaveForPerson(fagsakPerson, beskrivelse)

        if (nyopprettet) {
            oppgaveClient.opprettVurderLivshendelseOppgave(oppgave as OppgaveVurderLivshendelseDto).also {
                task.metadata["oppgaveId"] = it.oppgaveId.toString()
                taskRepository.saveAndFlush(task)
            }
            return true
        } else {
            task.metadata["oppgaveId"] = (oppgave as Oppgave).id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            taskRepository.saveAndFlush(task)
            log.info("Fant åpen oppgave på aktørId ${oppgave.aktoerId}")
            secureLog.info("Fant åpen oppgave: $oppgave")
            return false
        }
    }

    private fun hentEllerOpprettNyOppgaveForPerson(fagsakPerson: RestFagsakDeltager,
                                                   beskrivelse: String): Pair<Boolean, Any> {
        secureLog.info("hentEllerOpprettNyOppgaveForPerson for ${fagsakPerson.ident} med beskrivelse $beskrivelse")

        val aktørId = aktørClient.hentAktørId(fagsakPerson.ident)
        val vurderLivshendelseOppgaver = oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderLivshendelse)
        secureLog.info("Fant følgende oppgaver: $vurderLivshendelseOppgaver")

        val åpenOppgave: Oppgave? = vurderLivshendelseOppgaver.firstOrNull {
            it.beskrivelse?.startsWith(beskrivelse.substring(0,8)) == true && (
                    it.status != StatusEnum.FERDIGSTILT || it.status != StatusEnum.FEILREGISTRERT)
        }

        return if (åpenOppgave != null) {
            log.info("Fant åpen vurderLivshendelse oppgave for aktørId=$aktørId oppgaveId=${åpenOppgave.id}")
            Pair(false, åpenOppgave)
        } else {
            log.info("Oppretter oppgave for aktørId=$aktørId")
            val restFagsak = sakClient.hentRestFagsak(fagsakPerson.fagsakId)
            secureLog.info("Hentet restBehandling $restFagsak")
            val restUtvidetBehandling = restFagsak.behandlinger.firstOrNull { it.aktiv }

            if (restUtvidetBehandling == null) {
                error("Fagsak ${restFagsak.id} mangler aktiv behandling. Får ikke opprettet VurderLivshendelseOppgave")
            }

            Pair(true, OppgaveVurderLivshendelseDto(aktørId = aktørId,
                                                    beskrivelse = beskrivelse,
                                                    saksId = fagsakPerson.fagsakId.toString(),
                                                    behandlingstema = tilBehandlingstema(restUtvidetBehandling),
                                                    enhetsId = restUtvidetBehandling.arbeidsfordelingPåBehandling.behandlendeEnhetId,
                                                    behandlesAvApplikasjon = BehandlesAvApplikasjon.BA_SAK.applikasjon))
        }
    }

    private fun tilBehandlingstema(restUtvidetBehandling: RestUtvidetBehandling): String {
        return when {
            restUtvidetBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.BarnetrygdEØS.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd.value
            else -> Behandlingstema.Barnetrygd.value
        }
    }

    private fun lagUtflyttingoppgavebeskrivelse(fagsakPerson: String, utflyttetPerson: String) =
            BESKRIVELSE_UTFLYTTING.format(if (utflyttetPerson == fagsakPerson) "bruker" else "barn $utflyttetPerson")

    companion object {

        const val TASK_STEP_TYPE = "vurderLivshendelseTask"
        const val BESKRIVELSE_DØDSFALL = "Dødsfall"
        const val BESKRIVELSE_UTFLYTTING = "Utflytting: %s"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType {
    DØDSFALL,
    SIVILSTAND,
    ADDRESSE,
    UTFLYTTING
}