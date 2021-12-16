package no.nav.familie.ba.mottak.task

import no.nav.familie.leader.LeaderClient
import no.nav.familie.prosessering.domene.Avvikstype
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class FjernGamleSøknader(val taskRepository: TaskRepository) {

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    fun fjernGamleFeiledeTasksIPreprod() {
        val isLeader = LeaderClient.isLeader()

        if (isLeader != null && isLeader) {
            LOG.info("Fjerner gamle feilede tasks")

            for (task: Task in taskRepository.finnTasksMedStatus(listOf(Status.FEILET), PageRequest.of(0, 200))) {
                if (task.opprettetTidspunkt.isBefore(LocalDateTime.now().minusMonths(1))) {
                    task.avvikshåndter(avvikstype = Avvikstype.ANNET, årsak = "Rydder", endretAv = "VL")
                    taskRepository.save(task)
                }
            }
        }

    }

    companion object {

        val LOG = LoggerFactory.getLogger(FjernGamleSøknader::class.java)
    }
}