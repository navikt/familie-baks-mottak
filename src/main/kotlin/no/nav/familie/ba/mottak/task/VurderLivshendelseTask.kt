package no.nav.familie.ba.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.StatusEnum
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
    val oppgaveOpprettetUtflyttingCounter: Counter = Metrics.counter("barnetrygd.utflytting.oppgave.opprette")

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)

        when (payload.type) {
            VurderLivshendelseType.DØDSFALL -> {
                val pdlPersonData = pdlClient.hentPerson(payload.personIdent, "hentperson-relasjon-dødsfall")
                if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato != null) {
                    finnRelatertePersonerMedSak(payload.personIdent, pdlPersonData).forEach {
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
                val pdlPersonData = pdlClient.hentPerson(payload.personIdent, "hentperson-relasjon-utflytting")
                finnRelatertePersonerMedSak(payload.personIdent, pdlPersonData).forEach {
                    if (opprettVurderLivshendelseOppgave(it, task, BESKRIVELSE_UTFLYTTING)) {
                        log.error("Mottatt utflyttingshendelse på sak i BA-sak. Få saksbehandler til å se på oppgave av typen VurderLivshendelse") //TODO midlertidig error logg til man har fått dette inn i saksbehandlersrutinen.
                        oppgaveOpprettetUtflyttingCounter.increment()
                    }
                }
            }
            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }

    private fun finnRelatertePersonerMedSak(personIdent: String,
                                            pdlPersonData: PdlPersonData): Set<String> {

        val personerMedAktivSak = mutableSetOf<String>()

        val familierelasjon = pdlPersonData.forelderBarnRelasjon
        //populerer en liste med barn for person. Hvis person har barn, så sjekker man etter løpende sak
        val listeMedBarn =
                familierelasjon.filter { it.minRolleForPerson != Familierelasjonsrolle.BARN }
                        .map { it.relatertPersonsIdent }
        if (listeMedBarn.isNotEmpty()) {
            sakClient.hentPågåendeSakStatus(personIdent).apply { if (baSak.finnes()) personerMedAktivSak.add(personIdent) }
        }

        //Sjekker om foreldrene til person under 19 har en løpende sak.
        if (pdlPersonData.fødsel.isEmpty() ||
            pdlPersonData.fødsel.first().fødselsdato.isAfter(LocalDate.now().minusYears(19))) {

            val listeMedForeldreForBarn =
                    familierelasjon.filter { it.minRolleForPerson == Familierelasjonsrolle.BARN }
                            .map { it.relatertPersonsIdent }

            listeMedForeldreForBarn.forEach {
                sakClient.hentPågåendeSakStatus(it).apply { if (baSak.finnes()) personerMedAktivSak.add(it) }
            }
        }

        if (personerMedAktivSak.isNotEmpty()) {
            log.info("Fant løpende sak for person")
            secureLog.info("Fant løpende sak for person $personIdent")
        }
        return personerMedAktivSak
    }

    private fun opprettVurderLivshendelseOppgave(personIdent: String,
                                                 task: Task,
                                                 beskrivelse: String): Boolean {

        val (nyopprettet, oppgave) = hentEllerOpprettNyOppgaveForPerson(personIdent, beskrivelse)

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

    private fun hentEllerOpprettNyOppgaveForPerson(personIdent: String,
                                                   beskrivelse: String): Pair<Boolean, Any> {

        val aktørId = aktørClient.hentAktørId(personIdent.trim())
        val vurderLivshendelseOppgaver = oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderLivshendelse)

        val åpenOppgave: Oppgave? = vurderLivshendelseOppgaver.firstOrNull {
            it.beskrivelse?.contains(beskrivelse.substring(0,8)) == true && (
                    it.status != StatusEnum.FERDIGSTILT || it.status != StatusEnum.FEILREGISTRERT)
        }

        return if (åpenOppgave != null) {
            Pair(false, åpenOppgave)
        } else {
            val fagsak = sakClient.hentRestFagsak(personIdent)
            val restUtvidetBehandling = fagsak.behandlinger.first { it.aktiv }

            Pair(true, OppgaveVurderLivshendelseDto(aktørId = aktørClient.hentAktørId(personIdent.trim()),
                                                    beskrivelse = beskrivelse,
                                                    saksId = fagsak.id.toString(),
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

    companion object {

        const val TASK_STEP_TYPE = "vurderLivshendelseTask"
        const val BESKRIVELSE_DØDSFALL = "Dødsfall"
        const val BESKRIVELSE_UTFLYTTING = "Utflytting"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType {
    DØDSFALL,
    SIVILSTAND,
    ADDRESSE,
    UTFLYTTING
}