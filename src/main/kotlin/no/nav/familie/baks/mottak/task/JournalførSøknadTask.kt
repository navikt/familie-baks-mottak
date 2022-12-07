package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.søknad.JournalføringService
import no.nav.familie.baks.mottak.søknad.PdfService
import no.nav.familie.baks.mottak.søknad.SøknadRepository
import no.nav.familie.baks.mottak.søknad.domene.DBSøknad
import no.nav.familie.baks.mottak.søknad.domene.SøknadV7
import no.nav.familie.baks.mottak.søknad.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.domene.VersjonertSøknad
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
@TaskStepBeskrivelse(taskStepType = JournalførSøknadTask.JOURNALFØR_SØKNAD, beskrivelse = "Journalfør søknad")
class JournalførSøknadTask(
    private val pdfService: PdfService,
    private val journalføringService: JournalføringService,
    private val søknadRepository: SøknadRepository
) : AsyncTaskStep {

    override fun doTask(task: Task) {
        try {
            val id = task.payload
            log.info("Prøver å hente søknadspdf for $id")
            val dbSøknad: DBSøknad =
                søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
            val versjonertSøknad: VersjonertSøknad = dbSøknad.hentVersjonertSøknad()

            log.info("Generer pdf og journalfør søknad")
            val bokmålPdf = pdfService.lagBarnetrygdPdf(
                versjonertSøknad = versjonertSøknad,
                dbSøknad = dbSøknad,
                språk = "nb"
            )
            log.info("Generert pdf med størrelse ${bokmålPdf.size}")

            val orginalspråk = when (versjonertSøknad) {
                is SøknadV7 -> versjonertSøknad.søknad.originalSpråk
                is SøknadV8 -> versjonertSøknad.søknad.originalSpråk
            }

            val orginalspråkPdf: ByteArray = if (orginalspråk != "nb") {
                pdfService.lagBarnetrygdPdf(versjonertSøknad, dbSøknad, orginalspråk)
            } else {
                ByteArray(0)
            }
            journalføringService.journalførKontantstøtteSøknad(dbSøknad, bokmålPdf, orginalspråkPdf)
        } catch (e: HttpClientErrorException.Conflict) {
            log.error("409 conflict for eksternReferanseId ved journalføring av søknad. taskId=${task.id}. Se task eller securelog")
            SECURE_LOGGER.error("409 conflict for eksternReferanseId ved journalføring søknad $task ${e.responseBodyAsString}", e)
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
