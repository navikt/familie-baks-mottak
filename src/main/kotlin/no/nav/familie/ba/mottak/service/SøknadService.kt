package no.nav.familie.ba.mottak.service

import no.nav.familie.ba.mottak.repository.SoknadRepository
import no.nav.familie.ba.mottak.repository.domain.Soknad
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class SøknadService(private val soknadRepository: SoknadRepository) : SøknadServiceInterface {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    override fun motta(soknad: Soknad ): String {
        val søknadDb = SøknadMapper.fromDto(soknad.søknad)
        val lagretSkjema = soknadRepository.save(søknadDb)
        logger.info("Mottatt søknad med id ${lagretSkjema.id}")
        return "Søknad lagret med id ${lagretSkjema.id} er registrert mottatt."
    }

    override fun get(id: String): Soknad {
        return soknadRepository.findByIdOrNull(id) ?: error("Ugyldig primærnøkkel")
    }

    override fun sendTilSak(søknadId: String) {
        val soknad: Soknad = soknadRepository.findByIdOrNull(søknadId) ?: error("")

        if (soknad.dokumenttype == DOKUMENTTYPE_OVERGANGSSTØNAD) {
            val vedlegg = vedleggRepository.findBySøknadId(søknadId)
            val kontraktVedlegg = vedlegg
                    .map { no.nav.familie.kontrakter.ba.søknad.Vedlegg(it.id.toString(), it.navn, it.tittel, null) }
            val sak: SakRequest = SakMapper.toSak(soknad, kontraktVedlegg)
            søknadClient.sendTilSak(sak, vedlegg.map { it.id.toString() to it.innhold.bytes }.toMap())
        } else {
            val skjemasak: Skjemasak = SakMapper.toSkjemasak(soknad)
            søknadClient.sendTilSak(skjemasak)
        }
    }

    @Transactional
    override fun motta(skjemaForArbeidssøker: SkjemaForArbeidssøker): Kvittering {
        val søknadDb = SøknadMapper.fromDto(skjemaForArbeidssøker)
        val lagretSkjema = soknadRepository.save(søknadDb)
        logger.info("Mottatt skjema med id ${lagretSkjema.id}")

        return Kvittering(søknadDb.id, "Skjema er mottatt og lagret med id ${lagretSkjema.id}.")
    }

    override fun lagreSøknad(soknad: Soknad) {
        soknadRepository.save(soknad)
    }
}
