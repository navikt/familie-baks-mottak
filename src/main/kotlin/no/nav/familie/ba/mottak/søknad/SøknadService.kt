package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad


@Service
class SøknadService(private val søknadRepository: SøknadRepository) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun motta(søknad: Søknad): String {
        val lagretSkjema = lagreSøknad(søknad)
        logger.info("Mottatt søknad med id ${lagretSkjema.id}")
        return "Søknad lagret med id ${lagretSkjema.id} er registrert mottatt."
    }

    fun lagreSøknad(søknad: Søknad): DBSøknad {
        return søknadRepository.save(søknad.tilDBSøknad())
    }

    fun hentDBSøknad(søknadId: Long): DBSøknad? {
        return søknadRepository.hentDBSøknad(søknadId)
    }
}
