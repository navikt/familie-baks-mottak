package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.søknad.JournalføringService
import no.nav.familie.ba.mottak.søknad.PdfService
import no.nav.familie.ba.mottak.søknad.SøknadRepository
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = JournalførSøknadTask.JOURNALFØR_SØKNAD, beskrivelse = "Journalfør søknad")
class JournalførSøknadTask(private val pdfService: PdfService,
                           private val journalføringService: JournalføringService,
                           private val søknadRepository: SøknadRepository) : AsyncTaskStep {

    override fun doTask(task: Task) {
        try {
            val id = task.payload;
            log.info("Prøver å hente søknadspdf for $id")
            val dbSøknad = søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
            log.info("Generer pdf og journalfør søknad")

            val bokmålPdf = pdfService.lagPdf(dbSøknad)
            log.info("Generert pdf med størrelse ${bokmålPdf.size}")

            if (dbSøknad.hentSøknad().originalSpråk != "nb") {
                val originalspråkPdf = pdfService.lagPdf(dbSøknad, dbSøknad.hentSøknad().originalSpråk)
                journalføringService.journalførSøknad(dbSøknad, bokmålPdf, originalspråkPdf)
            } else {
                journalføringService.journalførSøknad(dbSøknad, bokmålPdf)
            }
        } catch (e: Exception) {
            log.error("Uventet feil ved journalføring av søknad. taskId=${task.id}. Se task eller securelog")
            SECURE_LOGGER.error("Uventet feil ved journalføring søknad $task", e)
            throw e
        }
    }

    companion object {
        const val JOURNALFØR_SØKNAD = "journalførSøknad"
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
    }

}