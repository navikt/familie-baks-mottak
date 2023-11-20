package no.nav.familie.baks.mottak.task

import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE,
    beskrivelse = "Vurder barnetrygd livshendelse",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 3600,
)
class VurderBarnetrygdLivshendelseTask(
    private val vurderLivshendelseService: VurderLivshendelseService,
) : AsyncTaskStep {
    private val tema = Tema.BAR

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun doTask(task: Task) {
        vurderLivshendelseService.vurderLivshendelseOppgave(task, tema)
    }

    companion object {
        const val TASK_STEP_TYPE = "vurderBarnetrygdLivshendelseTask"
    }
}
