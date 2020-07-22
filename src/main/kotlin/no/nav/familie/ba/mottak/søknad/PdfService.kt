package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.integrasjoner.PdfClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileOutputStream
import java.io.OutputStream


@Service
class PdfService(private val søknadRepository: SøknadRepository, private val pdfClient: PdfClient) {

    fun lagPdf(id: String) {
        val dbSøknad = søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
        val feltMap = SøknadTreeWalker.mapSøknadsfelter(dbSøknad.hentSøknad())
        log.info("Hentet søknadsfelt fra Treewalker, sender til dokgen...")
        val søknadPdf = pdfClient.lagPdf(feltMap)
        log.info("PDF mottatt!")
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}