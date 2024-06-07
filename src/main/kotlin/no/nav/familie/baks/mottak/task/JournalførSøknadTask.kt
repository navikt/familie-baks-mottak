package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.søknad.JournalføringService
import no.nav.familie.baks.mottak.søknad.PdfService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertBarnetrygdSøknad
import no.nav.familie.http.client.RessursException
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
    private val søknadRepository: SøknadRepository,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        try {
            val id = task.payload
            log.info("Prøver å hente søknadspdf for $id")
            val dbBarnetrygdSøknad: DBBarnetrygdSøknad =
                søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
            val versjonertBarnetrygdSøknad: VersjonertBarnetrygdSøknad = dbBarnetrygdSøknad.hentVersjonertSøknad()

            val søknadstype =
                when (versjonertBarnetrygdSøknad) {
                    is SøknadV8 -> versjonertBarnetrygdSøknad.søknad.søknadstype
                }
            log.info("Generer pdf og journalfør søknad om ${søknadstype.name.lowercase()} barnetrygd")
            val bokmålPdf =
                pdfService.lagBarnetrygdPdf(
                    versjonertBarnetrygdSøknad = versjonertBarnetrygdSøknad,
                    dbBarnetrygdSøknad = dbBarnetrygdSøknad,
                    språk = "nb",
                )
            log.info("Generert pdf med størrelse ${bokmålPdf.size}")

            val orginalspråk =
                when (versjonertBarnetrygdSøknad) {
                    is SøknadV8 -> versjonertBarnetrygdSøknad.søknad.originalSpråk
                }

            val orginalspråkPdf: ByteArray =
                if (orginalspråk != "nb") {
                    pdfService.lagBarnetrygdPdf(versjonertBarnetrygdSøknad, dbBarnetrygdSøknad, orginalspråk)
                } else {
                    ByteArray(0)
                }
            journalføringService.journalførBarnetrygdSøknad(dbBarnetrygdSøknad, bokmålPdf, orginalspråkPdf)
        } catch (e: RessursException) {
            when (e.cause) {
                is HttpClientErrorException.Conflict -> {
                    // Dersom søknaden allerede er journalført får vi 409-Conflict. Vi ønsker ikke å feile tasken når dette skjer.
                    log.error("409 conflict for eksternReferanseId ved journalføring av søknad. taskId=${task.id}. Se task eller securelog")
                    SECURE_LOGGER.error(
                        "409 conflict for eksternReferanseId ved journalføring søknad $task ${(e.cause as HttpClientErrorException.Conflict).responseBodyAsString}",
                        e,
                    )
                }

                else -> throw e
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
