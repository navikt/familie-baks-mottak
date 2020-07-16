package no.nav.familie.ba.mottak.søknad

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PdfService(private val soknadRepository: SoknadRepository) {

    fun lagPdf(id: String) {
        val dbSøknad = soknadRepository.hentSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
        val feltMap = SøknadTreeWalker.mapSøknadsfelter(dbSøknad.hentSøknad())
        LoggerFactory.getLogger(this::class.java).info(feltMap.toString())
    }
}