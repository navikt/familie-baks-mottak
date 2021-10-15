package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.OppgaveIdentV2
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@TaskStepBeskrivelse(
    taskStepType = OpprettBehandleAnnullerFødselOppgaveTask.TASK_STEP_TYPE,
    beskrivelse = "Opprett \"BehandleAnnullerFødsel\"-oppgave"
)
class OpprettBehandleAnnullerFødselOppgaveTask(
    private val oppgaveClient: OppgaveClient,
    private val sakClient: SakClient,
    private val aktørClient: AktørClient,
    private val taskRepository: TaskRepository
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettBehandleAnnullerFødselOppgaveTask::class.java)

    override fun doTask(task: Task) {
        task.metadata["forsøkTaskId"] = null
        var payload = jacksonObjectMapper().readValue(task.payload, NyBehandling::class.java)
        var fagsak = sakClient.hentRestFagsak(payload.morsIdent)
        if (fagsak == null) {
            retryLater(task)
            return
        }

        var aktivBehandling = fagsak.behandlinger.find { it.aktiv }

        if (aktivBehandling == null) {
            retryLater(task)
            return
        }

        val aktørId = aktørClient.hentAktørId(payload.morsIdent)

        task.metadata["oppgaveId"] =
            "${
                oppgaveClient.opprettBehandleAnnullerFødselOppgave(
                    ident = OppgaveIdentV2(aktørId, IdentGruppe.AKTOERID),
                    saksId = fagsak.id.toString(),
                    enhetsnummer = aktivBehandling.arbeidsfordelingPåBehandling.behandlendeEnhetId,
                    behandlingstema = when (aktivBehandling.underkategori) {
                        BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd.toString()
                        BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd.toString()
                    },
                    behandlingstype = aktivBehandling.type,
                    //TODO: is this the good place for barnasidenter?
                    beskrivelse = "Fødselshendelse med barnsidenter ${payload.barnasIdenter} er annulert"
                ).oppgaveId
            }"
    }

    private fun retryLater(task: Task) {
        if ((task.metadata["antallForsøk"] as Int) >= MAX_ANTALL_FORSØK) {
            return
        }
        task.metadata["antallForsøk"] = task.metadata["antallForsøk"] as Int + 1
        task.metadata["forsøkTaskId"] =
            taskRepository.save(
                Task.nyTask(
                    type = TASK_STEP_TYPE,
                    payload = task.payload,
                    properties = task.metadata,
                ).copy(
                    triggerTid = LocalDateTime.now().plusHours(2),
                )
            ).id
    }

    companion object {

        const val TASK_STEP_TYPE = "OpprettBehandleAnnullerFødselOppgaveTask"
        val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
        const val MAX_ANTALL_FORSØK = 12
    }

}