package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.integrasjoner.RestAnnullerFødsel
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Avvikstype
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import no.nav.familie.prosessering.rest.RestTaskService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = MottaAnnullerFødselTask.TASK_STEP_TYPE,
    beskrivelse = "Motta annuller fødsel",
    maxAntallFeil = 3,
)
class MottaAnnullerFødselTask(
    private val taskService: TaskService,
    private val restTaskService: RestTaskService,
) : AsyncTaskStep {
    val log: Logger = LoggerFactory.getLogger(MottaAnnullerFødselTask::class.java)

    override fun doTask(task: Task) {
        val restAnnullerFødsel = objectMapper.readValue(task.payload, RestAnnullerFødsel::class.java)
        val tidligereHendelseId = restAnnullerFødsel.tidligereHendelseId

        val tasker =
            taskService
                .finnTasksMedStatus(
                    listOf(Status.KLAR_TIL_PLUKK, Status.UBEHANDLET, Status.FEILET),
                    null,
                    Pageable.unpaged(),
                ).filter {
                    it.callId == tidligereHendelseId &&
                        (it.type == MottaFødselshendelseTask.TASK_STEP_TYPE || it.type == SendTilBaSakTask.TASK_STEP_TYPE)
                }

        tasker.forEach {
            restTaskService.avvikshåndterTask(
                taskId = it.id,
                avvikstype = Avvikstype.ANNET,
                årsak = AVVIKSÅRSAK,
                saksbehandlerId = "VL",
            )
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "mottaAnnullerFødsel"
        const val AVVIKSÅRSAK = "Annuller fødselshendelse"
    }
}
