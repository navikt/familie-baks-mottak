package no.nav.familie.ba.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstema
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
@TaskStepBeskrivelse(taskStepType = VurderLivshendelseTask.TASK_STEP_TYPE, beskrivelse = "Vurder livshendelse", maxAntallFeil = 3, triggerTidVedFeilISekunder = 3600)
class VurderLivshendelseTask(
        private val oppgaveClient: OppgaveClient,
        private val taskRepository: TaskRepository,
        private val pdlClient: PdlClient,
        private val sakClient: SakClient,
        private val featureToggleService: FeatureToggleService,
        private val aktørClient: AktørClient
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
    val oppgaveOpprettetDødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall.oppgave.opprettet")
    val oppgaveIgnorerteDødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall.oppgave.ignorert")

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        val pdlPersonData = pdlClient.hentPerson(payload.personIdent, "hentperson-relasjon-dødsfall")
        val familierelasjon = pdlPersonData.familierelasjoner
        when (payload.type) {
            VurderLivshendelseType.DØDSFALL -> {
                if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato != null) {

                    //populerer en liste med barn for person. Hvis person har barn, så sjekker man etter løpende sak
                    val listeMedBarn =
                            familierelasjon.filter { it.minRolleForPerson != Familierelasjonsrolle.BARN }
                                    .map { it.relatertPersonsIdent }
                    if (listeMedBarn.isNotEmpty()) {
                        val sak = sakClient.hentPågåendeSakStatus(payload.personIdent, listeMedBarn)
                        opprettOppgaveHvisRolleSøker(sak.baSak, payload.personIdent, task, BESKRIVELSE_DØDSFALL)
                    }

                    //Sjekker om foreldrene til person under 19 har en løpende sak.
                    if (pdlPersonData.fødsel.isEmpty() || pdlPersonData.fødsel.first().fødselsdato.isAfter(LocalDate.now()
                                                                                                                   .minusYears(19))) {
                        val listeMedForeldreForBarn =
                                familierelasjon.filter { it.minRolleForPerson == Familierelasjonsrolle.BARN }
                                        .map { it.relatertPersonsIdent }

                        listeMedForeldreForBarn.forEach {
                            val sak = sakClient.hentPågåendeSakStatus(it, listOf(payload.personIdent))
                            opprettOppgaveHvisRolleSøker(sak.baSak, it, task, BESKRIVELSE_DØDSFALL)
                        }
                    }
                } else {
                    SECURE_LOGGER.info("Har mottatt dødsfallshendelse uten dødsdato $pdlPersonData" )
                    error("Har mottatt dødsfallshendelse uten dødsdato")
                }
            }
            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }

    private fun opprettOppgaveHvisRolleSøker(saksPart: Sakspart?,
                                             personIdent: String,
                                             task: Task,
                                             beskrivelse: String) {
        if (saksPart == Sakspart.SØKER) { //Vi er kun interessert i om sakspart er SØKER
            log.info("Fant løpende sak for person")
            SECURE_LOGGER.info("Fant løpende sak for person $personIdent")
            val fagsak = sakClient.hentRestFagsak(personIdent)
            val restUtvidetBehandling = fagsak.behandlinger.first { it.aktiv }
            if (featureToggleService.isEnabled("familie-ba-mottak.opprettLivshendelseOppgave", false)) {
                val aktørId = aktørClient.hentAktørId(personIdent.trim())
                val vurderLivshendelseOppgaver = oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderHenvendelse)   //TODO Bytt ut til rett OppgaveType

                val oppgave: Oppgave? = vurderLivshendelseOppgaver.firstOrNull{ it.beskrivelse?.contains(BESKRIVELSE_DØDSFALL) == true && (
                        it.status != StatusEnum.FERDIGSTILT || it.status != StatusEnum.FEILREGISTRERT) }

                if (oppgave == null) {
                    oppgaveClient.opprettVurderLivshendelseOppgave(
                            OppgaveVurderLivshendelseDto(aktørClient.hentAktørId(personIdent.trim()),
                                                         beskrivelse,
                                                         fagsak.id.toString(),
                                                         tilBehandlingstema(
                                                                 restUtvidetBehandling),
                                                         restUtvidetBehandling.arbeidsfordelingPåBehandling.behandlendeEnhetId)).also {
                        task.metadata["oppgaveId"] = it.oppgaveId.toString()
                        taskRepository.saveAndFlush(task)
                    }
                    oppgaveOpprettetDødsfallCounter.increment()
                } else {
                    task.metadata["oppgaveId"] = oppgave.id.toString()
                    task.metadata["info"] = "Fant åpen oppgave"
                    taskRepository.saveAndFlush(task)
                    log.info("Fant åpen oppgave på aktørId $aktørId")
                    SECURE_LOGGER.info("Fant åpen oppgave: $oppgave")
                }

            } else {
                log.error("Mottatt dødsfallshendelse, men oppretting av oppgave er midlertidig skrudd av")
                oppgaveIgnorerteDødsfallCounter.increment()
            }
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
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType {
    DØDSFALL,
    SIVILSTAND,
    ADDRESSE
}