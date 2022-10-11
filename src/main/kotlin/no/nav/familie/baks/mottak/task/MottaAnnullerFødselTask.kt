package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.RestAnnullerFødsel
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Avvikstype
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = MottaAnnullerFødselTask.TASK_STEP_TYPE,
    beskrivelse = "Motta annuller fødsel",
    maxAntallFeil = 3
)
class MottaAnnullerFødselTask(
    private val taskRepository: TaskRepository
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(MottaAnnullerFødselTask::class.java)

    override fun doTask(task: Task) {
        val restAnnullerFødsel = objectMapper.readValue(task.payload, RestAnnullerFødsel::class.java)
        val tidligereHendelseId = restAnnullerFødsel.tidligereHendelseId

        val tasker =
            taskRepository.findByStatusIn(
                listOf(Status.KLAR_TIL_PLUKK, Status.UBEHANDLET, Status.FEILET),
                Pageable.unpaged()
            )
                .filter {
                    it.callId == tidligereHendelseId &&
                        (it.type == MottaFødselshendelseTask.TASK_STEP_TYPE || it.type == SendTilSakTask.TASK_STEP_TYPE)
                }
        tasker.forEach {
            taskRepository.save(
                taskRepository.findById(it.id).get()
                    .avvikshåndter(avvikstype = Avvikstype.ANNET, årsak = AVVIKSÅRSAK, endretAv = "VL")
            )
        }
    }

    companion object {

        const val TASK_STEP_TYPE = "mottaAnnullerFødsel"
        const val AVVIKSÅRSAK = "Annuller fødselshendelse"
    }
}
