package no.nav.familie.ba.mottak.søknad

import org.springframework.stereotype.Service

@Service
class PdfService(private val søknadRepository: SøknadRepository) {

    fun lagPdf(id: String) {
        val dbSøknad = søknadRepository.hentDBSøknad(id.toLong()) ?: error("Kunne ikke finne søknad ($id) i database")
        val feltMap = SøknadTreeWalker.mapSøknadsfelter(dbSøknad.hentSøknad())
    }
}