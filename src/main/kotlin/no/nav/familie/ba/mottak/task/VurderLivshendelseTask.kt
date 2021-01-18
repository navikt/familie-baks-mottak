package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@TaskStepBeskrivelse(taskStepType = VurderLivshendelseTask.TASK_STEP_TYPE, beskrivelse = "Vurder livshendelse")
class VurderLivshendelseTask(
    private val oppgaveClient: OppgaveClient,
    private val taskRepository: TaskRepository,
    private val pdlClient: PdlClient,
    private val sakClient: SakClient,
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(OpprettBehandleSakOppgaveTask::class.java)

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        //hent familierelasjoner
        val pdlPersonData = pdlClient.hentPerson(payload.personIdent, "hentperson-relasjon-dødsfall")
        val familierelasjon = pdlPersonData.familierelasjoner
        when(payload.type) {
            VurderLivshendelseType.DØDSFALL -> {
                if (pdlPersonData.dødsfall?.first().dødsdato != null) {
                    //Skal man gjøre spesifikk filtrering med OR for å sikre at det ikke kommer en ny relasjonstype
                    val listeMedBarn =
                        familierelasjon.filter { it.minRolleForPerson != Familierelasjonsrolle.BARN }.map { it.relatertPersonsIdent }
                    if (listeMedBarn.isNotEmpty()) {
                        //Her er vi kun interessert i om den som dør er SØKER. Er Sakspart ANNEN, så er det annen part som har
                        //søkt og er mest sannsynlig levende
                        val sak = sakClient.hentPågåendeSakStatus(payload.personIdent, listeMedBarn)
                        if (sak.baSak == Sakspart.SØKER) {
                            val fagsak = sakClient.hentRestFagsak(payload.personIdent)
                            val restUtvidetBehandling = fagsak.behandlinger.first { it.aktiv }
                            val oppgave = oppgaveClient.opprettVurderLivshendelseOppgave(payload.personIdent, "Søker har aktiv sak", fagsak.id.toString(), tilBehandlingstema(restUtvidetBehandling))
                            task.metadata["oppgaveId"] = oppgave.oppgaveId
                            taskRepository.saveAndFlush(task)
                        }
                    }

                    val listeMedForeldre =
                        familierelasjon.filter { it.minRolleForPerson == Familierelasjonsrolle.BARN }.map { it.relatertPersonsIdent }

                    listeMedForeldre.forEach {
                        val sak = sakClient.hentPågåendeSakStatus(it, listOf(payload.personIdent))
                        if (sak.baSak == Sakspart.SØKER) {
                            val fagsak = sakClient.hentRestFagsak(it)
                            val restUtvidetBehandling = fagsak.behandlinger.first { it.aktiv }
                            val oppgave = oppgaveClient.opprettVurderLivshendelseOppgave(it, "Barn har aktiv sak", fagsak.id.toString(), tilBehandlingstema(restUtvidetBehandling))
                            task.metadata["oppgaveId"] = oppgave.oppgaveId
                            taskRepository.saveAndFlush(task)
                        }
                    }
                }
            }
            else -> log.debug("Ikke behandlet livshendelse ${payload.type}")
        }
    }

    private fun tilBehandlingstema(restUtvidetBehandling: RestUtvidetBehandling ): String {
        return when {
            restUtvidetBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.BarnetrygdEØS.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd.value
            else -> Behandlingstema.Barnetrygd.value
        }
    }

    enum class Behandlingstema(val value: String) {
        Barnetrygd("ab0270"),
        BarnetrygdEØS("ab0058"),
        OrdinærBarnetrygd("ab0180"),
        UtvidetBarnetrygd("ab0096"),
        Skolepenger("ab0177"),
        Barnetilsyn("ab0028"),
        Overgangsstønad("ab0071")
    }

    companion object {

        const val TASK_STEP_TYPE = "vurderLivshendelseTask"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType {
    DØDSFALL,
    SIVILSTAND,
    ADDRESSE
}