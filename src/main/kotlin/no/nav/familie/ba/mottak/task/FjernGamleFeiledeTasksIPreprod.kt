package no.nav.familie.ba.mottak.task

import no.nav.familie.prosessering.domene.Avvikstype
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!prod")
class FjernGamleFeiledeTasksIPreprod(val taskRepository: TaskRepository){

    @Scheduled(cron = "0 0 9 * * 7")
    fun fjernGamleFeiledeTasksIPreprod() {
        LOG.info("Fjerner gamle feilede tasks")

        for(task: Task in taskRepository.finnAlleFeiledeTasks()) {
            task.avvikshåndter(avvikstype = Avvikstype.ANNET, årsak = "Rydder", endretAv = "VL")
            taskRepository.save(task)
        }
    }

    companion object {
        val LOG = LoggerFactory.getLogger(FjernGamleFeiledeTasksIPreprod::class.java)
    }
}