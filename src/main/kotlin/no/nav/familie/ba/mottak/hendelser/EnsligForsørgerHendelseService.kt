package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.ef.EnsligForsørgerVedtakhendelse
import no.nav.familie.kontrakter.felles.ef.StønadType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EnsligForsørgerHendelseService(
    val sakClient: SakClient,
    val pdlClient: PdlClient,
    val hendelsesloggRepository: HendelsesloggRepository,
    @Value("\${ef.overgangstonad.sendtilsak:false}") val sendTilSak: Boolean,
) {

    val ensligForsørgerVedtakhendelseOvergangstønadCounter: Counter = Metrics.counter("ef.hendelse.vedtak", "type", "overgangstønad")
    val ensligForsørgerVedtakhendelseAnnetCounter: Counter = Metrics.counter("ef.hendelse.vedtak", "type", "annet")
    val ensligForsørgerInfotrygdVedtakhendelseOvergangstønadCounter: Counter = Metrics.counter("ef.hendelse.infotrygd.vedtak", "type", "overgangstønad")
    val ensligForsørgerInfotrygdVedtakhendelseAnnetCounter: Counter = Metrics.counter("ef.hendelse.infotrygd.vedtak", "type", "annet")

    fun prosesserEfVedtakHendelse(offset: Long, ensligForsørgerVedtakhendelse: EnsligForsørgerVedtakhendelse) {

        when (ensligForsørgerVedtakhendelse.stønadType) {
            StønadType.OVERGANGSSTØNAD -> {
                if (!hendelsesloggRepository.existsByHendelseIdAndConsumer(
                        ensligForsørgerVedtakhendelse.behandlingId.toString(),
                        HendelseConsumer.EF_VEDTAK
                    )
                ) {
                    secureLogger.info("Mottatt vedtak om overgangsstønad hendelse: $ensligForsørgerVedtakhendelse")
                    if (sendTilSak) {
                        sakClient.sendVedtakOmOvergangsstønadHendelseTilSak(ensligForsørgerVedtakhendelse.personIdent)
                    }

                    hendelsesloggRepository.save(
                        Hendelseslogg(
                            offset,
                            ensligForsørgerVedtakhendelse.behandlingId.toString(),
                            HendelseConsumer.EF_VEDTAK,
                            mapOf(
                                "behandlingId" to ensligForsørgerVedtakhendelse.behandlingId.toString(),
                                "stønadstype" to ensligForsørgerVedtakhendelse.stønadType.toString()
                            ).toProperties(),
                            ident = ensligForsørgerVedtakhendelse.personIdent
                        )
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

    fun prosesserEfInfotrygdHendelse(offset: Long, hendelse: InfotrygdHendelse) {
        if (hendelse.typeYtelse.trim() != "EF") {
            logger.info("Ignorerer infotrygdhendelse for hendelseId=${hendelse.hendelseId} fordi ytelsen ikke er EF")
            return
        }
        when(hendelse.typeHendelse.trim()) {
            "INNVILGET" -> {
                if (!hendelsesloggRepository.existsByHendelseIdAndConsumer(
                        hendelse.hendelseId,
                        HendelseConsumer.EF_VEDTAK_INFOTRYGD
                    )
                ) {
                    secureLogger.info("Mottatt infotrygdvedtak om overgangsstønad: $hendelse")

                    val personIdent = pdlClient.hentPersonident(hendelse.aktørId.toString())
                    if (sendTilSak) {
                        sakClient.sendVedtakOmOvergangsstønadHendelseTilSak(personIdent)
                    }

                    hendelsesloggRepository.save(
                        Hendelseslogg(
                            offset,
                            hendelse.hendelseId,
                            HendelseConsumer.EF_VEDTAK_INFOTRYGD,
                            mapOf(
                                "personIdent" to personIdent,
                                "hendelseId" to hendelse.hendelseId,
                                "sats" to hendelse.sats.toString()
                            ).toProperties(),
                            ident = personIdent
                        )
                    )
                    ensligForsørgerInfotrygdVedtakhendelseOvergangstønadCounter.increment()
                }
            }
            else -> ensligForsørgerInfotrygdVedtakhendelseAnnetCounter.increment()
        }
    }

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(EnsligForsørgerHendelseService::class.java)
        private val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")
    }
}
