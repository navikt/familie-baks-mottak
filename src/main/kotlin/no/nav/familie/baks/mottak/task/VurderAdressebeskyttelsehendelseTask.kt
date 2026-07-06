package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BehandlingStatus
import no.nav.familie.baks.mottak.integrasjoner.FagsakStatus
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClientService
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Properties

@Service
@TaskStepBeskrivelse(
    taskStepType = VurderAdressebeskyttelsehendelseTask.TASK_STEP_TYPE,
    beskrivelse = "Oppretter vurder livshendelse oppgave om adressebeskyttelse er opphørt for skjermet barn med løpende fagsak",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 60,
)
class VurderAdressebeskyttelsehendelseTask(
    private val baSakClient: BaSakClient,
    private val pdlClientService: PdlClientService,
    private val oppgaveClient: OppgaveClientService,
) : AsyncTaskStep {
    val secureLogger: Logger = LoggerFactory.getLogger("secureLogger")

    override fun doTask(task: Task) {
        val personIdent = pdlClientService.hentPersonident(task.payload, Tema.BAR)
        val adressebeskyttelser = pdlClientService.hentPerson(personIdent, "hentperson-med-adressebeskyttelse", Tema.BAR, historikk = true).adressebeskyttelse

        val harNåværendeAdressebeskyttelse =
            adressebeskyttelser
                .filter { it.metadata?.historisk != true }
                .any { it.gradering.erStrengtFortrolig() }

        val haddeAdressebeskyttelse =
            adressebeskyttelser
                .filter { it.metadata?.historisk == true }
                .any { it.gradering.erStrengtFortrolig() }

        secureLogger.info(
            "Vurderer adressebeskyttelsehendelse harNåværendeAdressebeskyttelse=$harNåværendeAdressebeskyttelse, haddeAdressebeskyttelse=$haddeAdressebeskyttelse",
        )
        if (harNåværendeAdressebeskyttelse || !haddeAdressebeskyttelse) return

        val løpendeFagsak =
            baSakClient
                .hentFagsakForSkjermetBarn(personIdent)
                .firstOrNull { it.status == FagsakStatus.LØPENDE }
                ?: run {
                    secureLogger.info("Fant ingen løpende fagsak for adressebeskyttelse-hendelsen")
                    return
                }

        val restMinimalFagsak = baSakClient.hentMinimalRestFagsak(løpendeFagsak.id)
        val sisteBehandling =
            restMinimalFagsak.behandlinger
                .filter { it.status == BehandlingStatus.AVSLUTTET }
                .maxByOrNull { it.opprettetTidspunkt }

        oppgaveClient.opprettVurderLivshendelseOppgave(
            OppgaveVurderLivshendelseDto(
                aktørId = task.payload,
                beskrivelse = "Adressebeskyttelse er opphevet",
                saksId = løpendeFagsak.id.toString(),
                tema = Tema.BAR,
                behandlingstema = sisteBehandling.tilBarnetrygdBehandlingstema().value,
                enhetsId = ENHETSNUMMER_VIKAFOSSEN,
            ),
        )
    }

    companion object {
        const val TASK_STEP_TYPE = "vurderAdressebeskyttelsehendelseTask"

        const val ENHETSNUMMER_VIKAFOSSEN = "2103"

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
