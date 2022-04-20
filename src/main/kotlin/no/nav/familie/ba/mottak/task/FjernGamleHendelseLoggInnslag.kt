package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.leader.LeaderClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class FjernGamleHendelseLoggInnslag(val hendelsesloggRepository: HendelsesloggRepository) {

    @Scheduled(cron = "0 0 9 * * *")
    fun fjernGamleHendelseLoggInnslag() {
        if (LeaderClient.isLeader() == true) {
            slettHendelserEldreEnn2Måneder()
        }
    }

    @Transactional
    fun slettHendelserEldreEnn2Måneder() {
        val gamleHendelser = hendelsesloggRepository.findAll().filter {
            it.opprettetTidspunkt.isBefore(LocalDateTime.now().minusMonths(2))
        }
        LOG.info("Fjerner gamle hendelser eldre enn måneder fra hendelse_logg")
        hendelsesloggRepository.deleteAll(gamleHendelser)
    }

    companion object {
        val LOG = LoggerFactory.getLogger(FjernGamleHendelseLoggInnslag::class.java)
    }
}
