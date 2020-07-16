package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.søknad.PdfService
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = JournalførSøknadTask.JOURNALFØR_SØKNAD, beskrivelse = "Journalfør søknad")
class JournalførSøknadTask(private val pdfService: PdfService,
                           private val taskRepository: TaskRepository) : AsyncTaskStep {

    override fun doTask(task: Task) {
        pdfService.lagPdf(task.payload)
    }

    companion object {
        const val JOURNALFØR_SØKNAD = "journalførSøknad"
    }

}