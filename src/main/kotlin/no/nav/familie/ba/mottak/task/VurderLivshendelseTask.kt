package no.nav.familie.ba.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.BehandlesAvApplikasjon
import no.nav.familie.ba.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.ba.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.AVSLUTTET
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.ba.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.ba.mottak.integrasjoner.PdlPersonData
import no.nav.familie.ba.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.ba.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.StatusEnum
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@TaskStepBeskrivelse(
        taskStepType = VurderLivshendelseTask.TASK_STEP_TYPE,
        beskrivelse = "Vurder livshendelse",
        maxAntallFeil = 3,
        triggerTidVedFeilISekunder = 3600
)
class VurderLivshendelseTask(
        private val oppgaveClient: OppgaveClient,
        private val taskRepository: TaskRepository,
        private val pdlClient: PdlClient,
        private val sakClient: SakClient,
        private val aktørClient: AktørClient
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val secureLog: Logger = LoggerFactory.getLogger("secureLogger")
    val oppgaveOpprettetDødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall.oppgave.opprettet")
    val oppgaveOpprettetUtflyttingCounter: Counter = Metrics.counter("barnetrygd.utflytting.oppgave.opprettet")

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        val personIdent = payload.personIdent

        when (payload.type) {
            VurderLivshendelseType.DØDSFALL -> {
                secureLog.info("Har mottat dødsfallshendelse for person ${personIdent}")
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-dødsfall")
                secureLog.info("dødsfallshendelse person følselsdato = ${pdlPersonData.fødsel.firstOrNull()}")
                if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato != null) {
                    val berørteBrukereIBaSak = finnBrukereMedLøpendeSakInvolverendePerson(personIdent, pdlPersonData)
                    secureLog.info("berørteBrukereIBaSak count = ${berørteBrukereIBaSak.size}, identer = ${
                        berørteBrukereIBaSak.fold("") { identer, it -> identer + " " + it.ident }
                    }")
                    berørteBrukereIBaSak.forEach {
                        if (opprettEllerOppdaterDødsfallshendelseOppgave(it, task)) {
                            log.error("Mottatt dødsfallshendelse på sak i BA-sak. Få saksbehandler til å se på oppgave av typen VurderLivshendelse") // TODO midlertidig error logg til man har fått dette inn i saksbehandlersrutinen.
                            oppgaveOpprettetDødsfallCounter.increment()
                        }
                    }
                } else {
                    secureLog.info("Har mottatt dødsfallshendelse uten dødsdato $pdlPersonData")
                    error("Har mottatt dødsfallshendelse uten dødsdato")
                }
            }
            VurderLivshendelseType.UTFLYTTING -> {
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-utflytting")
                finnBrukereMedLøpendeSakInvolverendePerson(personIdent, pdlPersonData).forEach {
                    if (opprettVurderLivshendelseOppgave(
                                    it,
                                    task,
                                    lagUtflyttingoppgavebeskrivelse(it.ident, personIdent)
                            )
                    ) {
                        log.error("Mottatt utflyttingshendelse på sak i BA-sak. Få saksbehandler til å se på oppgave av typen VurderLivshendelse") // TODO midlertidig error logg til man har fått dette inn i saksbehandlersrutinen.
                        oppgaveOpprettetUtflyttingCounter.increment()
                    }
                }
            }
            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }

    private fun finnBrukereMedLøpendeSakInvolverendePerson(
            personIdent: String,
            pdlPersonData: PdlPersonData
    ): Set<RestFagsakDeltager> {

        val brukereMedLøpendeSakInvolverendePerson = mutableSetOf<RestFagsakDeltager>()

        val familierelasjoner = pdlPersonData.forelderBarnRelasjon

        // Hvis person har barn, så sjekker man etter løpende sak på person
        val personHarBarn = familierelasjoner.filter { it.erBarn }.let {
                secureLog.info("finnBrukereMedLøpendeSakInvolverendePerson(): listeMedBarn size = ${it.size} identer = " +
                                       it.fold("") { identer, it -> identer + " " + it.relatertPersonsIdent })
                it.isNotEmpty()
            }

        if (personHarBarn) {
            brukereMedLøpendeSakInvolverendePerson += sakClient.hentRestFagsakDeltagerListe(personIdent).filter {
                secureLog.info("finnBrukereMedLøpendeSakInvolverendePerson(): Hentet Fagsak for person ${personIdent}: ${it.fagsakId} ${it.fagsakStatus}")
                it.fagsakStatus != AVSLUTTET
            }
        }

        // Sjekker om foreldrene til person under 19 har en relatert sak.
        if (personErBarn(pdlPersonData)) {
            brukereMedLøpendeSakInvolverendePerson += finnForeldreMedLøpendeSak(personIdent, familierelasjoner)
        }

        secureLog.info("finnBrukereMedLøpendeSakInvolverendePerson(): brukere.size = ${brukereMedLøpendeSakInvolverendePerson.size}" +
                       "identer = ${brukereMedLøpendeSakInvolverendePerson.fold("") { identer, it -> identer + " " + it.ident }}")

        if (brukereMedLøpendeSakInvolverendePerson.isNotEmpty()) {
            log.info("Fant sak for person")
            secureLog.info("Fant sak for person $personIdent")
        }
        return brukereMedLøpendeSakInvolverendePerson
    }

    private fun personErBarn(pdlPersonData: PdlPersonData) = pdlPersonData.fødsel.isEmpty() ||
            pdlPersonData.fødsel.first().fødselsdato.isAfter(LocalDate.now().minusYears(19))

    private fun finnForeldreMedLøpendeSak(
        personIdent: String,
        familierelasjoner: List<PdlForeldreBarnRelasjon>
    ): List<RestFagsakDeltager> {
        return familierelasjoner.filter { !it.erBarn }.map { it.relatertPersonsIdent }.mapNotNull { forelder ->
            sakClient.hentRestFagsakDeltagerListe(forelder, barnasIdenter = listOf(personIdent))
                .filter { it.fagsakStatus != AVSLUTTET }
                .groupBy { it.fagsakId }.values
                .firstOrNull { it.inneholderBådeForelderOgBarn }
                ?.find { it.rolle == FORELDER }
        }
    }

    private fun lagDødsfallOppgaveBeskrivelse(fagsakPerson: RestFagsakDeltager): String {
        val pdlPersonData = pdlClient.hentPerson(fagsakPerson.ident, "hentperson-relasjon-dødsfall")
        val dødsfallBarnList = pdlPersonData.forelderBarnRelasjon.filter {
            it.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN &&
            pdlClient.hentPerson(
                    it.relatertPersonsIdent,
                    "hentperson-relasjon-dødsfall"
            ).dødsfall.firstOrNull()?.dødsdato != null
        }
        val tekstBruker = if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato != null) "bruker" else ""
        val tekstOg = if (tekstBruker.isNotEmpty() && dødsfallBarnList.isNotEmpty()) " og " else ""
        val tekstBarn = if (dødsfallBarnList.count() > 1) dødsfallBarnList.count()
                                                                  .toString() + " barn" else if (dødsfallBarnList.isNotEmpty()) "barn" else ""
        val tekstBarnIdenter = dødsfallBarnList.fold("") { identList, it -> "$identList ${it.relatertPersonsIdent}" }
        val beskrivelsesTekst = "$BESKRIVELSE_DØDSFALL: " + tekstBruker + tekstOg + tekstBarn + tekstBarnIdenter
        return beskrivelsesTekst
    }

    private fun opprettEllerOppdaterDødsfallshendelseOppgave(
            fagsakPerson: RestFagsakDeltager,
            task: Task
    ): Boolean {
        val (nyopprettet, oppgave) = hentEllerOpprettNyOppgaveForPerson(fagsakPerson, BESKRIVELSE_DØDSFALL)
        val beskrivelsesTekst = lagDødsfallOppgaveBeskrivelse(fagsakPerson)

        if (nyopprettet) {
            oppgaveClient.opprettVurderLivshendelseOppgave(
                    (oppgave as OppgaveVurderLivshendelseDto).copy(
                            beskrivelse = beskrivelsesTekst
                    )
            ).also {
                task.metadata["oppgaveId"] = it.oppgaveId.toString()
                taskRepository.saveAndFlush(task)
                secureLog.info(
                        "Opprett oppgave (${it.oppgaveId}) for dødsfallshendelse (person ident:  ${fagsakPerson.ident})" +
                        ", beskrivelsestekst: $beskrivelsesTekst"
                )
            }
            return true
        } else {
            oppgaveClient.oppdaterOppgaveBeskrivelse(oppgave as Oppgave, beskrivelsesTekst)
            task.metadata["oppgaveId"] = (oppgave as Oppgave).id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            taskRepository.saveAndFlush(task)
            log.info("Fant åpen oppgave på aktørId ${oppgave.aktoerId}")
            secureLog.info("Fant åpen oppgave: $oppgave")
            secureLog.info("Oppdater oppgave ($oppgave.id) beskrivelsestekster: $beskrivelsesTekst")
            return false
        }
    }

    private fun opprettVurderLivshendelseOppgave(
            fagsakPerson: RestFagsakDeltager,
            task: Task,
            beskrivelse: String
    ): Boolean {

        val (nyopprettet, oppgave) = hentEllerOpprettNyOppgaveForPerson(fagsakPerson, beskrivelse)

        if (nyopprettet) {
            oppgaveClient.opprettVurderLivshendelseOppgave(oppgave as OppgaveVurderLivshendelseDto).also {
                task.metadata["oppgaveId"] = it.oppgaveId.toString()
                taskRepository.saveAndFlush(task)
            }
            return true
        } else {
            task.metadata["oppgaveId"] = (oppgave as Oppgave).id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            taskRepository.saveAndFlush(task)
            log.info("Fant åpen oppgave på aktørId ${oppgave.aktoerId}")
            secureLog.info("Fant åpen oppgave: $oppgave")
            return false
        }
    }

    private fun hentEllerOpprettNyOppgaveForPerson(
            fagsakPerson: RestFagsakDeltager,
            beskrivelse: String
    ): Pair<Boolean, Any> {
        secureLog.info("hentEllerOpprettNyOppgaveForPerson for ${fagsakPerson.ident} med beskrivelse $beskrivelse")

        val aktørId = aktørClient.hentAktørId(fagsakPerson.ident)
        val vurderLivshendelseOppgaver =
                oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderLivshendelse)
        secureLog.info("Fant følgende oppgaver: $vurderLivshendelseOppgaver")

        val åpenOppgave: Oppgave? = vurderLivshendelseOppgaver.firstOrNull {
            it.beskrivelse?.startsWith(beskrivelse.substring(0, 8)) == true && (
                    it.status != StatusEnum.FERDIGSTILT || it.status != StatusEnum.FEILREGISTRERT
                                                                               )
        }

        return if (åpenOppgave != null) {
            log.info("Fant åpen vurderLivshendelse oppgave for aktørId=$aktørId oppgaveId=${åpenOppgave.id}")
            Pair(false, åpenOppgave)
        } else {
            log.info("Oppretter oppgave for aktørId=$aktørId")
            val minimalRestFagsak = sakClient.hentMinimalRestFagsak(fagsakPerson.fagsakId)
            secureLog.info("Hentet minimal rest fagsak: $minimalRestFagsak")
            val restMinimalBehandling = minimalRestFagsak.behandlinger.firstOrNull { it.aktiv }

            if (restMinimalBehandling == null) {
                error("Fagsak ${fagsakPerson.fagsakId} mangler aktiv behandling. Får ikke opprettet VurderLivshendelseOppgave")
            }

            val restFagsak = sakClient.hentRestFagsak(fagsakPerson.fagsakId)
            val restUtvidetBehandling =
                    restFagsak.behandlinger.firstOrNull { it.behandlingId == restMinimalBehandling.behandlingId }

            Pair(
                    true,
                    OppgaveVurderLivshendelseDto(
                            aktørId = aktørId,
                            beskrivelse = beskrivelse,
                            saksId = fagsakPerson.fagsakId.toString(),
                            behandlingstema = tilBehandlingstema(restUtvidetBehandling),
                            behandlesAvApplikasjon = BehandlesAvApplikasjon.BA_SAK.applikasjon
                    )
            )
        }
    }

    private fun tilBehandlingstema(restUtvidetBehandling: RestUtvidetBehandling?): String {
        return when {
            restUtvidetBehandling == null -> Behandlingstema.Barnetrygd.value
            restUtvidetBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.BarnetrygdEØS.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd.value
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd.value
            else -> Behandlingstema.Barnetrygd.value
        }
    }

    private fun lagUtflyttingoppgavebeskrivelse(fagsakPerson: String, utflyttetPerson: String) =
            BESKRIVELSE_UTFLYTTING.format(if (utflyttetPerson == fagsakPerson) "bruker" else "barn $utflyttetPerson")

    private val PdlForeldreBarnRelasjon.erBarn: Boolean
        get() = this.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN

    private val List<RestFagsakDeltager>.inneholderBådeForelderOgBarn: Boolean
        get() = this.map(RestFagsakDeltager::rolle).containsAll(listOf(FORELDER, BARN))

    companion object {

        const val TASK_STEP_TYPE = "vurderLivshendelseTask"
        const val BESKRIVELSE_DØDSFALL = "Dødsfall"
        const val BESKRIVELSE_UTFLYTTING = "Utflytting: %s"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType {
    DØDSFALL,
    SIVILSTAND,
    ADDRESSE,
    UTFLYTTING
}
