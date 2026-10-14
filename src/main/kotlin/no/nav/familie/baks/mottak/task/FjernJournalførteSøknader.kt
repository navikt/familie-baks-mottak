package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.søknad.JournalførteSøknaderRepository
import no.nav.familie.baks.mottak.søknad.Søknadstype
import no.nav.familie.leader.LeaderClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class FjernJournalførteSøknader(
    private val journalførteSøknaderRepository: JournalførteSøknaderRepository,
) {
    @Scheduled(cron = "0 * * * * *")
    fun fjernJournalførteSøknader() {
        if (LeaderClient.isLeader() != true) return

        val eldreEnn = LocalDateTime.now().minusDays(ANTALL_DAGER_FØR_SLETTING_UTEN_JOURNALPOST_ID)
        Søknadstype.entries.forEach { søknadstype ->
            var antallSlettet = 0
            for (batch in 1..MAKS_ANTALL_BATCHER) {
                val antallSlettetIBatch = journalførteSøknaderRepository.slettJournalførteSøknader(søknadstype, eldreEnn, BATCHSTØRRELSE)
                antallSlettet += antallSlettetIBatch
                if (antallSlettetIBatch < BATCHSTØRRELSE) break
            }
            if (antallSlettet > 0) {
                LOG.info("Fjernet $antallSlettet journalførte søknader om ${søknadstype.name.lowercase()} fra databasen")
            }
        }
    }

    companion object {
        const val BATCHSTØRRELSE = 500
        const val MAKS_ANTALL_BATCHER = 10
        const val ANTALL_DAGER_FØR_SLETTING_UTEN_JOURNALPOST_ID = 14L
        private val LOG = LoggerFactory.getLogger(FjernJournalførteSøknader::class.java)
    }
}
