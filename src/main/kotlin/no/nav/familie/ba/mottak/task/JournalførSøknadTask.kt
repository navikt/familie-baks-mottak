package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.søknad.JournalføringService
import no.nav.familie.ba.mottak.søknad.PdfService
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = JournalførSøknadTask.JOURNALFØR_SØKNAD, beskrivelse = "Journalfør søknad")
class JournalførSøknadTask(private val pdfService: PdfService,
                           private val journalføringService: JournalføringService) : AsyncTaskStep {

    override fun doTask(task: Task) {
        log.info("Generer pdf og journalfør søknad")
        val pdf = pdfService.lagPdf(task.payload)
        journalføringService.journalførSøknad(task.payload, pdf)
    }

    companion object {
        const val JOURNALFØR_SØKNAD = "journalførSøknad"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

}