package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.domene.HendelseConsumer
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
    @Scheduled(cron = "0 0 9 * * *")
    fun fjernGamleHendelseLoggInnslag() {
        if (LeaderClient.isLeader() == true) {
            slettHendelserEldreEnn2MånederFraTopicsMedMindreRetentionTid()
        }
    }

    @Transactional
    fun slettHendelserEldreEnn2MånederFraTopicsMedMindreRetentionTid() {
        val gamleHendelser =
            hendelsesloggRepository
                .findAll()
                .filter {
                    it.opprettetTidspunkt.isBefore(LocalDateTime.now().minusMonths(2))
                }.filterNot { it.consumer == HendelseConsumer.EF_VEDTAK_INFOTRYGD_V1 }

        LOG.info("Fjerner gamle hendelser eldre enn 2 måneder fra hendelse_logg")
        hendelsesloggRepository.deleteAll(gamleHendelser)
    }

    companion object {
        val LOG = LoggerFactory.getLogger(FjernGamleHendelseLoggInnslag::class.java)
    }
}
