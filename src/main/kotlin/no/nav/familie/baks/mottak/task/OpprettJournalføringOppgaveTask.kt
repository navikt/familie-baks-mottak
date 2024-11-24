package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = OpprettJournalføringOppgaveTask.TASK_STEP_TYPE,
    beskrivelse = "Opprett journalføringsoppgave",
)
class OpprettJournalføringOppgaveTask(
    private val journalpostClient: JournalpostClient,
    private val oppgaveClient: OppgaveClient,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(OpprettJournalføringOppgaveTask::class.java)
    val barnetrygdOppgaverOpprettetCounter: Counter = Metrics.counter("barnetrygd.ruting.oppgave.opprettet")
    val barnetrygdOppgaverOppdatertCounter: Counter = Metrics.counter("barnetrygd.ruting.oppgave.oppdatert")
    val barnetrygdOppgaverSkippetCounter: Counter = Metrics.counter("barnetrygd.ruting.oppgave.skippet")

    val kontantstøtteOppgaverOpprettetCounter: Counter = Metrics.counter("kontantstotte.ruting.oppgave.opprettet")
    val kontantstøtteOppgaverOppdatertCounter: Counter = Metrics.counter("kontantstotte.ruting.oppgave.oppdatert")
    val kontantstøtteOppgaverSkippetCounter: Counter = Metrics.counter("kontantstotte.ruting.oppgave.skippet")

    override fun doTask(task: Task) {
        val sakssystemMarkering = task.payload
        val journalpost = journalpostClient.hentJournalpost(task.metadata["journalpostId"] as String)
        val tema = Tema.valueOf(journalpost.tema!!)
        when (journalpost.journalstatus) {
            Journalstatus.MOTTATT -> {
                val journalføringsOppgaver =
                    oppgaveClient.finnOppgaver(journalpost.journalpostId, Oppgavetype.Journalføring)
                val oppgaveTypeForEksisterendeOppgave: Oppgavetype? =
                    if (journalføringsOppgaver.isNotEmpty()) {
                        Oppgavetype.Journalføring
                    } else if (oppgaveClient
                            .finnOppgaver(journalpost.journalpostId, Oppgavetype.Fordeling)
                            .isNotEmpty()
                    ) {
                        Oppgavetype.Fordeling
                    } else {
                        null
                    }
                if (oppgaveTypeForEksisterendeOppgave == null) {
                    val nyOppgave =
                        oppgaveClient.opprettJournalføringsoppgave(
                            journalpost = journalpost,
                            beskrivelse = task.payload.takeIf { it.isNotEmpty() },
                        )
                    task.metadata["oppgaveId"] = "${nyOppgave.oppgaveId}"
                    log.info("Oppretter ny journalførings-oppgave med id ${nyOppgave.oppgaveId} for journalpost ${journalpost.journalpostId}")
                    incrementOppgaverOpprettet(tema)
                } else {
                    log.info("Skipper oppretting av journalførings-oppgave. Fant åpen oppgave av type $oppgaveTypeForEksisterendeOppgave for ${journalpost.journalpostId}")
                    if (sakssystemMarkering.isNotEmpty()) {
                        journalføringsOppgaver.forEach {
                            it.oppdaterOppgavebeskrivelse(sakssystemMarkering)
                            incrementOppgaverOppdatert(tema)
                        }
                    }
                }
            }

            Journalstatus.JOURNALFOERT -> {
                log.info("Skipper journalpost ${journalpost.journalpostId} som alt er i status JOURNALFOERT")
                incrementOppgaverSkippet(tema)
            }

            else -> {
                val error =
                    IllegalStateException(
                        "Journalpost ${journalpost.journalpostId} har endret status fra MOTTATT til ${journalpost.journalstatus.name}",
                    )
                log.info("OpprettJournalføringOppgaveTask feilet.", error)
                throw error
            }
        }
    }

    private fun incrementOppgaverOpprettet(tema: Tema) {
        when (tema) {
            Tema.BAR -> barnetrygdOppgaverOpprettetCounter.increment()
            Tema.KON -> kontantstøtteOppgaverOpprettetCounter.increment()
            else -> error("Tema $tema støttes ikke")
        }
    }

    private fun incrementOppgaverOppdatert(tema: Tema) {
        when (tema) {
            Tema.BAR -> barnetrygdOppgaverOppdatertCounter.increment()
            Tema.KON -> kontantstøtteOppgaverOppdatertCounter.increment()
            else -> error("Tema $tema støttes ikke")
        }
    }

    private fun incrementOppgaverSkippet(tema: Tema) {
        when (tema) {
            Tema.BAR -> barnetrygdOppgaverSkippetCounter.increment()
            Tema.KON -> kontantstøtteOppgaverSkippetCounter.increment()
            else -> error("Tema $tema støttes ikke")
        }
    }

    private fun Oppgave.oppdaterOppgavebeskrivelse(beskrivelse: String) {
        log.info("Oppdaterer oppgavebeskrivelse for eksisterende $oppgavetype-oppgave $id: $beskrivelse")
        oppgaveClient.oppdaterOppgaveBeskrivelse(this, beskrivelse)
    }

    companion object {
        const val TASK_STEP_TYPE = "opprettJournalføringsoppgave"
    }
}
