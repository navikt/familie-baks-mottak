package no.nav.familie.baks.mottak.task

import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.springframework.stereotype.Service
import java.util.Properties

@Service
@TaskStepBeskrivelse(
    taskStepType = VurderFalskIdentitetTask.TASK_STEP_TYPE,
    beskrivelse = "Settes til manuell oppfølging hvis den falske identiteten er knyttet til løpende fagsak",
    settTilManuellOppfølgning = true,
    maxAntallFeil = 3,
)
class VurderFalskIdentitetTask(
    private val baSakClient: BaSakClient,
    private val ksSakClient: KsSakClient,
    private val pdlClientService: PdlClientService,
) : AsyncTaskStep {
    override fun doTask(task: Task) {
        val erFalskIdentitet = pdlClientService.hentPerson(task.payload, "hent-falsk-identitet", Tema.BAR).falskIdentitet?.erFalsk == true
        if (!erFalskIdentitet) {
            return
        }

        val ident = pdlClientService.hentIdenter(task.payload, Tema.BAR).first { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }.ident
        val løpendeFagsakerIBaSak = baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(ident)
        val løpendeFagsakerIKsSak = ksSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(ident)

        if (løpendeFagsakerIBaSak.isNotEmpty() || løpendeFagsakerIKsSak.isNotEmpty()) {
            var feilmelding = "Person med falsk identitet har løpende fagsak i ba-sak eller ks-sak. Fagsakene må behandles av en funksjonell på team BAKS. Send fagsakene til en funksjonell og avvikshåndter tasken."

            if (løpendeFagsakerIBaSak.isNotEmpty()) {
                feilmelding += "\nLøpende fagsaker i ba-sak: ${løpendeFagsakerIBaSak.map { it.fagsakId }.joinToString(", ")}"
            }

            if (løpendeFagsakerIKsSak.isNotEmpty()) {
                feilmelding += "\nLøpende fagsaker i ks-sak: ${løpendeFagsakerIKsSak.map { it.fagsakId }.joinToString(", ")}"
            }

            throw FalskIdentitetException(feilmelding)
        }
    }

    companion object {
        const val TASK_STEP_TYPE = "vurderFalskIdentitetTask"

        private class FalskIdentitetException(
            message: String,
        ) : Exception(message, null, true, false) {
            override fun toString(): String = message!!
        }

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
