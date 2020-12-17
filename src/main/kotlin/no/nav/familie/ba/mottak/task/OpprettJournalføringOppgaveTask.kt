package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
                     beskrivelse = "Opprett journalføringsoppgave")
class OpprettJournalføringOppgaveTask(private val journalpostClient: JournalpostClient,
                                      private val oppgaveClient: OppgaveClient,
                                      private val sakClient: SakClient,
                                      private val aktørClient: AktørClient,
                                      private val taskRepository: TaskRepository,
                                      private val personClient: PersonClient,
                                      private val infotrygdBarnetrygdClient: InfotrygdBarnetrygdClient) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettJournalføringOppgaveTask::class.java)

    override fun doTask(task: Task) {

        val journalpost = journalpostClient.hentJournalpost(task.payload)
        when (journalpost.journalstatus) {
            Journalstatus.MOTTATT -> {
                val oppgaveTypeForEksisterendeOppgave: Oppgavetype? =
                        if (oppgaveClient.finnOppgaver(journalpost.journalpostId, Oppgavetype.Journalføring).isNotEmpty()) {
                            Oppgavetype.Journalføring
                        } else if (oppgaveClient.finnOppgaver(journalpost.journalpostId, Oppgavetype.Fordeling).isNotEmpty()) {
                            Oppgavetype.Fordeling
                        } else null

                if (oppgaveTypeForEksisterendeOppgave == null) {
                    val nyOppgave = oppgaveClient.opprettJournalføringsoppgave(journalpost = journalpost,
                                                                               beskrivelse = sakssystemMarkering(journalpost))
                    task.metadata["oppgaveId"] = "${nyOppgave.oppgaveId}"
                    taskRepository.saveAndFlush(task)
                    log.info("Oppretter ny journalførings-oppgave med id ${nyOppgave.oppgaveId} for journalpost ${journalpost.journalpostId}")
                } else {
                    log.info("Skipper oppretting av journalførings-oppgave. Fant åpen oppgave av type $oppgaveTypeForEksisterendeOppgave for ${journalpost.journalpostId}")
                }
            }

            Journalstatus.JOURNALFOERT -> log.info("Skipper journalpost ${journalpost.journalpostId} som alt er i status JOURNALFOERT")

            else -> {
                val error = IllegalStateException(
                        "Journalpost ${journalpost.journalpostId} har endret status fra MOTTATT til ${journalpost.journalstatus.name}"
                )
                log.info("OpprettJournalføringOppgaveTask feilet.", error)
                throw error
            }
        }
    }

    private fun sakssystemMarkering(journalpost: Journalpost): String? {
        if (journalpost.bruker == null) return null

        val brukersIdenter = listOf(tilPersonIdent(journalpost.bruker)) // TODO: Legge til historiske identer
        val barnasIdenter = personClient.hentPersonMedRelasjoner(brukersIdenter.first()).familierelasjoner // TODO: Legge til historiske identer
                .filter { it.relasjonsrolle == "BARN" }
                .map { it.personIdent.id }

        val baSakRespons = sakClient.hentPågåendeSakStatus(brukersIdenter.first(), barnasIdenter).baSak
        val infotrygdRespons = infotrygdBarnetrygdClient.hentLøpendeUtbetalinger(brukersIdenter, barnasIdenter).part ?:
                               infotrygdBarnetrygdClient.hentSaker(brukersIdenter, barnasIdenter).part

        return when {
            baSakRespons.finnes() && infotrygdRespons.finnes() -> "Bruker har sak i både Infotrygd og BA-sak"
            baSakRespons.finnes() -> "${baSakRespons!!.part} har sak i BA-sak"
            infotrygdRespons.finnes() -> "${infotrygdRespons!!.part} har sak i Infotrygd"
            else -> null // trenger ingen form for markering. Kan løses av begge systemer
        }

    }

    private fun tilPersonIdent(bruker: Bruker): String {
        return when (bruker.type) {
            BrukerIdType.AKTOERID -> aktørClient.hentPersonident(bruker.id)
            else -> bruker.id
        }
    }

    companion object {

        const val TASK_STEP_TYPE = "opprettJournalføringsoppgave"
    }
}

