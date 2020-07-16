package no.nav.familie.ba.mottak.service

import no.nav.familie.ba.mottak.mapper.SøknadMapper
import no.nav.familie.ba.mottak.repository.SoknadRepository
import no.nav.familie.ba.mottak.repository.domain.Soknad
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import main.kotlin.no.nav.familie.ba.søknad.Søknad as Søknadskontrakt


@Service
class SøknadService(private val soknadRepository: SoknadRepository) : SøknadServiceInterface {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    override fun motta(soknad: Søknadskontrakt): String {
        val søknadDb = SøknadMapper.fromDto(soknad)
        val lagretSkjema = soknadRepository.save(søknadDb)
        logger.info("Mottatt søknad med id ${lagretSkjema.id}")
        return "Søknad lagret med id ${lagretSkjema.id} er registrert mottatt."
    }

    override fun get(id: String): Soknad {
        return soknadRepository.findByIdOrNull(id) ?: error("Ugyldig primærnøkkel")
    }

    override fun lagreSøknad(soknad: Soknad) {
        soknadRepository.save(soknad)
    }
}
