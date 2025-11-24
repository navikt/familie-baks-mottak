package no.nav.familie.baks.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.domene.HendelseConsumer
import no.nav.familie.baks.mottak.domene.Hendelseslogg
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.felles.ef.EnsligForsørgerVedtakhendelse
import no.nav.familie.kontrakter.felles.ef.StønadType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EnsligForsørgerHendelseService(
    val baSakClient: BaSakClient,
    val pdlClient: PdlClient,
    val hendelsesloggRepository: HendelsesloggRepository,
) {
    val ensligForsørgerVedtakhendelseOvergangstønadCounter: Counter =
        Metrics.counter("ef.hendelse.vedtak", "type", "overgangstønad")
    val ensligForsørgerVedtakhendelseAnnetCounter: Counter = Metrics.counter("ef.hendelse.vedtak", "type", "annet")

    fun prosesserEfVedtakHendelse(
        offset: Long,
        ensligForsørgerVedtakhendelse: EnsligForsørgerVedtakhendelse,
    ) {
        when (ensligForsørgerVedtakhendelse.stønadType) {
            StønadType.OVERGANGSSTØNAD -> {
                if (!hendelsesloggRepository.existsByHendelseIdAndConsumer(
                        ensligForsørgerVedtakhendelse.behandlingId.toString(),
                        HendelseConsumer.EF_VEDTAK_V1,
                    )
                ) {
                    secureLogger.info("Mottatt vedtak om overgangsstønad hendelse: $ensligForsørgerVedtakhendelse")

                    baSakClient.sendVedtakOmOvergangsstønadHendelseTilBaSak(ensligForsørgerVedtakhendelse.personIdent)

                    hendelsesloggRepository.save(
                        Hendelseslogg(
                            offset,
                            ensligForsørgerVedtakhendelse.behandlingId.toString(),
                            HendelseConsumer.EF_VEDTAK_V1,
                            mapOf(
                                "behandlingId" to ensligForsørgerVedtakhendelse.behandlingId.toString(),
                                "stønadstype" to ensligForsørgerVedtakhendelse.stønadType.toString(),
                            ).toProperties(),
                            ident = ensligForsørgerVedtakhendelse.personIdent,
                        ),
                    )
                    ensligForsørgerVedtakhendelseOvergangstønadCounter.increment()
                }
            }

            else -> {
                logger.info("Ignorerer vedtak av type ${ensligForsørgerVedtakhendelse.stønadType} for behandlingId=${ensligForsørgerVedtakhendelse.behandlingId}")
                ensligForsørgerVedtakhendelseAnnetCounter.increment()
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerHendelseService::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
