package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.leader.LeaderClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class FjernGamleHendelseLoggInnslag(
    val hendelsesloggRepository: HendelsesloggRepository,
) {
    @Scheduled(cron = "0 0/5 * * * *")
    @Transactional
    fun fjernGamleHendelseLoggInnslag() {
        if (LeaderClient.isLeader() == true) {
            val grense = LocalDateTime.now().minusMonths(2)
            val antallSlettet = hendelsesloggRepository.slettEldreEnn(grense)

            LOG.info("Fjernet $antallSlettet hendelser eldre enn $grense fra hendelse_logg")
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(FjernGamleHendelseLoggInnslag::class.java)
    }
}
