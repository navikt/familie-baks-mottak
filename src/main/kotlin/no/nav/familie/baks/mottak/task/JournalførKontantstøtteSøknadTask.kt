package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.søknad.JournalføringService
import no.nav.familie.baks.mottak.søknad.PdfService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.familie.http.client.RessursException
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
@TaskStepBeskrivelse(
    taskStepType = JournalførKontantstøtteSøknadTask.JOURNALFØR_KONTANTSTØTTE_SØKNAD,
    beskrivelse = "Journalfør søknad om kontantstøtte"
)
class JournalførKontantstøtteSøknadTask(
    private val pdfService: PdfService,
    private val journalføringService: JournalføringService,
    private val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository
) : AsyncTaskStep {

    override fun doTask(task: Task) {
        try {
            val id = task.payload
            logger.info("Prøver å hente søknadspdf for $id")
            val dbKontantstøtteSøknad: DBKontantstøtteSøknad =
                kontantstøtteSøknadRepository.hentSøknad(id.toLong())
                    ?: error("Kunne ikke finne søknad ($id) i database")
            val kontantstøtteSøknad = dbKontantstøtteSøknad.hentSøknad()

            logger.info("Generer pdf og journalfør søknad")
            val bokmålPdf = pdfService.lagKontantstøttePdf(
                kontantstøtteSøknad = kontantstøtteSøknad,
                dbKontantstøtteSøknad = dbKontantstøtteSøknad,
                språk = "nb"
            )
            logger.info("Generert pdf med størrelse ${bokmålPdf.size}")

            val orginalspråk = kontantstøtteSøknad.originalSpråk
            val orginalspråkPdf: ByteArray = if (orginalspråk != "nb") {
                pdfService.lagKontantstøttePdf(
                    kontantstøtteSøknad = kontantstøtteSøknad,
                    dbKontantstøtteSøknad = dbKontantstøtteSøknad,
                    språk = orginalspråk
                )
            } else {
                ByteArray(0)
            }
            journalføringService.journalførKontantstøtteSøknad(dbKontantstøtteSøknad, bokmålPdf, orginalspråkPdf)
        } catch (e: RessursException) {
            if (e.httpStatus == HttpStatus.CONFLICT) {
                // Dersom søknaden allerede er journalført får vi 409-Conflict. Vi ønsker ikke å feile tasken når dette skjer.
                logger.error("409 conflict for eksternReferanseId ved journalføring av søknad. taskId=${task.id}. Se task eller securelog")
                SECURE_LOGGER.error(
                    "409 conflict for eksternReferanseId ved journalføring søknad $task ${(e.cause as HttpClientErrorException.Conflict).responseBodyAsString}",
                    e
                )
            } else throw e
        } catch (e: Exception) {
            logger.error("Uventet feil ved journalføring av søknad. taskId=${task.id}. Se task eller securelog")
            SECURE_LOGGER.error("Uventet feil ved journalføring søknad $task", e)
            throw e
        }
    }

    companion object {
        const val JOURNALFØR_KONTANTSTØTTE_SØKNAD = "journalførKontantstøtteSøknad"
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
