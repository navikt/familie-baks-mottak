package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import main.kotlin.no.nav.familie.ba.søknad.Søknad


@Service
class SøknadService(private val soknadRepository: SoknadRepository) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun motta(søknad: Søknad): String {
        val lagretSkjema = soknadRepository.save(søknad.tilDBSøknad())
        logger.info("Mottatt søknad med id ${lagretSkjema.id}")
        return "Søknad lagret med id ${lagretSkjema.id} er registrert mottatt."
    }
}
