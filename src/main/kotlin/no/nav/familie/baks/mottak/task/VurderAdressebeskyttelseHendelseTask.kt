package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClientService
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.springframework.stereotype.Service
import java.util.Properties

@Service
@TaskStepBeskrivelse(
    taskStepType = VurderAdressebeskyttelseHendelseTask.TASK_STEP_TYPE,
    beskrivelse = "Oppretter vurder livshendelse oppgave om adressebeskyttelse er opphørt for skjermet barn med løpende fagsak",
    settTilManuellOppfølgning = true,
    maxAntallFeil = 3,
)
class VurderAdressebeskyttelseHendelseTask(
    private val baSakClient: BaSakClient,
    private val pdlClientService: PdlClientService,
    private val oppgaveClient: OppgaveClientService,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val personIdent = pdlClientService.hentPersonident(task.payload, Tema.BAR)
        val adressebeskyttelse = pdlClientService.hentPerson(personIdent, "hentperson-med-adressebeskyttelse", Tema.BAR, historikk = true).adressebeskyttelse

        val harNåværendeAdressebeskyttelse =
            adressebeskyttelse
                .filter { it.metadata?.historisk != true }
                .any { it.gradering.erFortrolig() || it.gradering.erStrengtFortrolig() }

        val haddeAdressebeskyttelse =
            adressebeskyttelse
                .filter { it.metadata?.historisk == true }
                .any { it.gradering.erFortrolig() || it.gradering.erStrengtFortrolig() }

        if (harNåværendeAdressebeskyttelse || !haddeAdressebeskyttelse) return

        val løpendeFagsak =
            baSakClient
                .hentFagsakForSkjermetBarn(personIdent)
                .firstOrNull { it.status == FagsakStatus.LØPENDE }
                ?: return

        oppgaveClient.opprettVurderLivshendelseOppgave(
            OppgaveVurderLivshendelseDto(
                aktørId = task.payload,
                beskrivelse = "Adressebeskyttelse er opphørt for skjermet barn",
                saksId = løpendeFagsak.id.toString(),
                tema = Tema.BAR,
                behandlingstema = Behandlingstema.Barnetrygd.value,
                enhetsId = "2103",
            ),
        )
    }

    companion object {
        const val TASK_STEP_TYPE = "vurderAdressehendelseTask"

        fun opprettTask(pdlHendelse: PdlHendelse): Task =
            Task(
                type = TASK_STEP_TYPE,
                payload = pdlHendelse.gjeldendeAktørId,
                properties =
                    Properties().apply {
                        this["ident"] = pdlHendelse.hentPersonident()
                        this["callId"] = pdlHendelse.hendelseId
                    },
            )
    }
}
