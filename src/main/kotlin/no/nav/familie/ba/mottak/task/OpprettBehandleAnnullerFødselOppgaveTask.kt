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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(
    taskStepType = OpprettBehandleAnnullerFødselOppgaveTask.TASK_STEP_TYPE,
    beskrivelse = "Opprett \"BehandleAnnullerFødsel\"-oppgave"
)
class OpprettBehandleAnnullerFødselOppgaveTask(
    private val oppgaveClient: OppgaveClient,
    private val sakClient: SakClient,
    private val aktørClient: AktørClient
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettBehandleAnnullerFødselOppgaveTask::class.java)

    override fun doTask(task: Task) {
        var payload = jacksonObjectMapper().readValue(task.payload, NyBehandling::class.java)
        var fagsak = sakClient.hentRestFagsak(payload.morsIdent)
        var aktivBehandling = fagsak.behandlinger.find { it.aktiv }
        //TODO: criteria against the behandling/fagsak for creating oppgave
        if (aktivBehandling != null) {
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
                            else -> null
                        },
                        behandlingstype = aktivBehandling.type,
                        //TODO: is this the good place for barnasidenter?
                        beskrivelse = "Fødselshendelse med barnsidenter ${payload.barnasIdenter} er annulert"
                    ).oppgaveId
                }"
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "OpprettBehandleAnnullerFødselOppgaveTask"
    }

}