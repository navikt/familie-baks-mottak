package no.nav.familie.ba.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.BehandlesAvApplikasjon
import no.nav.familie.ba.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.ba.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.BARN
import no.nav.familie.ba.mottak.integrasjoner.FagsakDeltagerRolle.FORELDER
import no.nav.familie.ba.mottak.integrasjoner.FagsakStatus.LØPENDE
import no.nav.familie.ba.mottak.integrasjoner.Identgruppe
import no.nav.familie.ba.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.ba.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.ba.mottak.integrasjoner.PdlClient
import no.nav.familie.ba.mottak.integrasjoner.PdlForeldreBarnRelasjon
import no.nav.familie.ba.mottak.integrasjoner.PdlPersonData
import no.nav.familie.ba.mottak.integrasjoner.RestFagsak
import no.nav.familie.ba.mottak.integrasjoner.RestFagsakDeltager
import no.nav.familie.ba.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.ba.mottak.integrasjoner.Sivilstand
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.SIVILSTAND
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.UTFLYTTING
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.StatusEnum
import no.nav.familie.kontrakter.felles.personopplysning.FORELDERBARNRELASJONROLLE
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND.GIFT
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Service
@TaskStepBeskrivelse(
    taskStepType = VurderLivshendelseTask.TASK_STEP_TYPE,
    beskrivelse = "Vurder livshendelse",
    maxAntallFeil = 3,
    triggerTidVedFeilISekunder = 3600
)
class VurderLivshendelseTask(
    private val oppgaveClient: OppgaveClient,
    private val pdlClient: PdlClient,
    private val sakClient: SakClient,
    private val aktørClient: AktørClient,
    private val infotrygdClient: InfotrygdBarnetrygdClient
) : AsyncTaskStep {

    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val secureLog: Logger = LoggerFactory.getLogger("secureLogger")
    val oppgaveOpprettetDødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall.oppgave.opprettet")
    val oppgaveOpprettetUtflyttingCounter: Counter = Metrics.counter("barnetrygd.utflytting.oppgave.opprettet")
    val oppgaveOpprettetSivilstandCounter: Counter = Metrics.counter("barnetrygd.sivilstand.oppgave.opprettet")

    override fun doTask(task: Task) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        val personIdent = payload.personIdent

        when (payload.type) {
            DØDSFALL -> {
                secureLog.info("Har mottat dødsfallshendelse for person $personIdent")
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-dødsfall")
                secureLog.info("dødsfallshendelse person følselsdato = ${pdlPersonData.fødsel.firstOrNull()}")
                if (pdlPersonData.dødsfall.firstOrNull()?.dødsdato == null) {
                    secureLog.info("Har mottatt dødsfallshendelse uten dødsdato $pdlPersonData")
                    error("Har mottatt dødsfallshendelse uten dødsdato")
                }
                val berørteBrukereIBaSak = finnBrukereMedSakRelatertTilPerson(personIdent, pdlPersonData)
                secureLog.info(
                    "berørteBrukereIBaSak count = ${berørteBrukereIBaSak.size}, identer = ${
                    berørteBrukereIBaSak.fold("") { identer, it -> identer + " " + it.ident }
                    }"
                )
                berørteBrukereIBaSak.forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(DØDSFALL, it, personIdent, task)) {
                        oppgaveOpprettetDødsfallCounter.increment()
                    }
                }
            }
            UTFLYTTING -> {
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-utflytting")
                finnBrukereMedSakRelatertTilPerson(personIdent, pdlPersonData).forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(UTFLYTTING, it, personIdent, task)) {
                        oppgaveOpprettetUtflyttingCounter.increment()
                    }
                }
            }
            SIVILSTAND -> {
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-sivilstand")
                val sivilstand = finnNyesteSivilstandEndring(pdlPersonData) ?: run {
                    secureLog.info("Ignorerer sivilstandhendelse for $personIdent uten dato: $pdlPersonData")
                    return
                }
                if (sivilstand.type != GIFT) {
                    secureLog.info("Endringen til sivilstand GIFT for $personIdent er korrigert/annulert: $pdlPersonData")
                    return
                }
                val aktivFaksak = sakClient.hentRestFagsakDeltagerListe(personIdent).filter {
                    secureLog.info("Hentet Fagsak for person $personIdent: ${it.fagsakId} ${it.fagsakStatus}")
                    it.fagsakStatus == LØPENDE
                }.singleOrNull()?.let { hentRestFagsak(it.fagsakId) }

                if (aktivFaksak != null &&
                    tilBehandlingstema(hentSisteBehandlingSomErIverksatt(aktivFaksak)) == Behandlingstema.UtvidetBarnetrygd &&
                    sjekkOmDatoErEtterEldsteVedtaksdato(sivilstand.dato!!, aktivFaksak, personIdent)
                ) {
                    opprettEllerOppdaterEndringISivilstandOppgave(sivilstand.dato!!, aktivFaksak.id, personIdent, task)
                }
            }
            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }

    private fun sjekkOmDatoErEtterEldsteVedtaksdato(dato: LocalDate, aktivFaksak: RestFagsak, personIdent: String): Boolean {
        val tidligsteVedtakIBaSak = aktivFaksak.behandlinger
            .filter { it.resultat == RESULTAT_INNVILGET && it.steg == STEG_TYPE_BEHANDLING_AVSLUTTET }
            .minByOrNull { it.opprettetTidspunkt } ?: return false

        if (dato.isAfter(tidligsteVedtakIBaSak.opprettetTidspunkt.toLocalDate())) {
            return true
        }

        val erEtterTidligsteInfotrygdVedtak = if (tidligsteVedtakIBaSak.type == BEHANDLING_TYPE_MIGRERING) {
            hentTidligsteVedtaksdatoFraInfotrygd(personIdent)?.isBefore(dato) ?: false
        } else {
            false
        }

        return erEtterTidligsteInfotrygdVedtak
            .also { if (!it) hentOgLoggRelevantInfoOmÅrsakenKanVæreInnflytting(personIdent, dato) }
    }

    private fun hentTidligsteVedtaksdatoFraInfotrygd(personIdent: String): LocalDate? {
        val personIdenter = pdlClient.hentIdenter(personIdent)
            .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
            .map { it.ident }
        val tidligsteInfotrygdVedtak = infotrygdClient.hentVedtak(personIdenter).bruker
            .maxByOrNull { it.iverksattFom ?: "000000" } // maxBy... siden datoen er på "seq"-format
        return tidligsteInfotrygdVedtak?.iverksattFom
            ?.let { YearMonth.parse("${999999 - it.toInt()}", DateTimeFormatter.ofPattern("yyyyMM")) }
            ?.atDay(1)
    }

    private fun hentOgLoggRelevantInfoOmÅrsakenKanVæreInnflytting(personIdent: String, dato: LocalDate) {
        val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-innflytting")
        secureLog.info("Ignorerer sivilstandhendelse med gammel dato ($dato). Se om årsak kan være innflytting: $pdlPersonData")
    }

    private fun finnNyesteSivilstandEndring(pdlPersonData: PdlPersonData): Sivilstand? {
        return pdlPersonData.sivilstand.filter { it.dato != null }.maxByOrNull { it.dato!! }
    }

    private fun finnBrukereMedSakRelatertTilPerson(
        personIdent: String,
        pdlPersonData: PdlPersonData
    ): Set<Bruker> {
        val brukereMedSakRelatertTilPerson = mutableSetOf<Bruker>()

        val familierelasjoner = pdlPersonData.forelderBarnRelasjon

        // Hvis person har barn, så sjekker man etter løpende sak på person
        val personHarBarn = familierelasjoner.filter { it.erBarn }.let { listeMedBarn ->
            secureLog.info(
                "finnBrukereMedSakRelatertTilPerson(): listeMedBarn size = ${listeMedBarn.size} identer = " +
                    listeMedBarn.fold("") { identer, it -> identer + " " + it.relatertPersonsIdent }
            )
            listeMedBarn.isNotEmpty()
        }

        if (personHarBarn) {
            brukereMedSakRelatertTilPerson += sakClient.hentRestFagsakDeltagerListe(personIdent).filter {
                secureLog.info("finnBrukereMedSakRelatertTilPerson(): Hentet Fagsak for person $personIdent: ${it.fagsakId} ${it.fagsakStatus}")
                it.fagsakStatus == LØPENDE
            }.map { Bruker(personIdent, it.fagsakId) }
        }

        // Sjekker om foreldrene til person under 19 har en relatert sak.
        if (personErBarn(pdlPersonData)) {
            brukereMedSakRelatertTilPerson += finnForeldreMedLøpendeSak(personIdent, familierelasjoner)
        }

        secureLog.info(
            "finnBrukereMedSakRelatertTilPerson(): brukere.size = ${brukereMedSakRelatertTilPerson.size} " +
                "identer = ${brukereMedSakRelatertTilPerson.fold("") { identer, it -> identer + " " + it.ident }}"
        )

        if (brukereMedSakRelatertTilPerson.isNotEmpty()) {
            log.info("Fant sak for person")
            secureLog.info("Fant sak for person $personIdent")
        }
        return brukereMedSakRelatertTilPerson
    }

    private fun personErBarn(pdlPersonData: PdlPersonData) = pdlPersonData.fødsel.isEmpty() || // Kan anta barn når data mangler
        pdlPersonData.fødsel.first().fødselsdato.isAfter(LocalDate.now().minusYears(19))

    private fun finnForeldreMedLøpendeSak(
        personIdent: String,
        familierelasjoner: List<PdlForeldreBarnRelasjon>
    ): List<Bruker> {
        return familierelasjoner.filter { !it.erBarn }.also { listeMedForeldreForBarn ->
            secureLog.info(
                "finnForeldreMedLøpendeSak(): listeMedForeldreForBarn.size = ${listeMedForeldreForBarn.size} " +
                    "identer = ${listeMedForeldreForBarn.fold("") { identer, it -> identer + " " + it.relatertPersonsIdent }}"
            )
        }.mapNotNull { forelder ->
            forelder.relatertPersonsIdent?.let {
                sakClient.hentRestFagsakDeltagerListe(it, barnasIdenter = listOf(personIdent))
                    .filter { it.fagsakStatus == LØPENDE }
                    .groupBy { it.fagsakId }.values
                    .firstOrNull { it.inneholderBådeForelderOgBarn }
                    ?.find { it.rolle == FORELDER }
            }
        }.map { Bruker(it.ident, it.fagsakId) }
    }

    private fun opprettEllerOppdaterVurderLivshendelseOppgave(
        hendelseType: VurderLivshendelseType,
        bruker: Bruker,
        personIdent: String,
        task: Task
    ): Boolean {
        val aktørId = aktørClient.hentAktørId(bruker.ident)
        val åpenOppgave = søkEtterÅpenOppgavePåAktør(aktørId, hendelseType)

        if (åpenOppgave == null) {
            val beskrivelse = leggTilNyPersonIBeskrivelse(
                beskrivelse = "${hendelseType.beskrivelse}:",
                personIdent = personIdent,
                personErBruker = personIdent == bruker.ident
            )
            val restFagsak = hentRestFagsak(bruker.fagsakId)
            val restBehandling = hentSisteBehandlingSomErIverksatt(restFagsak) ?: hentAktivBehandling(restFagsak)
            val behandlingstema = tilBehandlingstema(restBehandling)
            val oppgave = opprettOppgavePåAktør(aktørId, bruker.fagsakId, beskrivelse, behandlingstema)
            task.metadata["oppgaveId"] = oppgave.oppgaveId.toString()
            secureLog.info(
                "Opprettet VurderLivshendelse-oppgave (${oppgave.oppgaveId}) for $hendelseType-hendelse (person ident:  ${bruker.ident})" +
                    ", beskrivelsestekst: $beskrivelse"
            )
            return true
        } else {
            log.info("Fant åpen oppgave på aktørId=$aktørId oppgaveId=${åpenOppgave.id}")
            secureLog.info("Fant åpen oppgave: $åpenOppgave")
            val beskrivelse = leggTilNyPersonIBeskrivelse(
                beskrivelse = åpenOppgave.beskrivelse!!,
                personIdent = personIdent,
                personErBruker = åpenOppgave.identer?.map { it.ident }
                    ?.contains(personIdent)
            )

            oppdaterOppgaveMedNyBeskrivelse(åpenOppgave, beskrivelse)
            task.metadata["oppgaveId"] = åpenOppgave.id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            return false
        }
    }

    private fun opprettEllerOppdaterEndringISivilstandOppgave(
        endringsdato: LocalDate,
        fagsakId: Long,
        personIdent: String,
        task: Task
    ) {
        val formatertDato = endringsdato.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale("no"))
        )
        val beskrivelse = SIVILSTAND.beskrivelse + " fra " + (formatertDato ?: "ukjent dato")

        val aktørId = aktørClient.hentAktørId(personIdent)
        val oppgave = søkEtterÅpenOppgavePåAktør(aktørId, SIVILSTAND)
            ?: opprettOppgavePåAktør(aktørId, fagsakId, beskrivelse, Behandlingstema.UtvidetBarnetrygd)

        when (oppgave) {
            is OppgaveResponse -> {
                secureLog.info(
                    "Opprettet VurderLivshendelse-oppgave (${oppgave.oppgaveId}) for $SIVILSTAND-hendelse (person ident:  $personIdent)" +
                        ", beskrivelsestekst: $beskrivelse"
                )
                oppgaveOpprettetSivilstandCounter.increment()
                task.metadata["oppgaveId"] = oppgave.oppgaveId.toString()
            }
            is Oppgave -> {
                log.info("Fant åpen oppgave på aktørId=$aktørId oppgaveId=${oppgave.id}")
                secureLog.info("Fant åpen oppgave: $oppgave")
                oppdaterOppgaveMedNyBeskrivelse(oppgave, beskrivelse)
                task.metadata["oppgaveId"] = oppgave.id.toString()
                task.metadata["info"] = "Fant åpen oppgave"
            }
        }
    }

    private fun leggTilNyPersonIBeskrivelse(beskrivelse: String, personIdent: String, personErBruker: Boolean?): String {
        return when (personErBruker) {
            true -> if (!beskrivelse.contains("bruker")) leggTilBrukerIBeskrivelse(beskrivelse) else beskrivelse
            else -> if (!beskrivelse.contains(personIdent)) leggTilBarnIBeskrivelse(beskrivelse, personIdent) else beskrivelse
        }
    }

    private fun leggTilBrukerIBeskrivelse(beskrivelse: String): String {
        val (livshendelseType, barn) = beskrivelse.split(":")
        return "$livshendelseType: bruker" + if (barn.isNotEmpty()) " og $barn" else ""
    }

    private fun leggTilBarnIBeskrivelse(beskrivelse: String, personIdent: String): String {
        if (!beskrivelse.contains("barn")) {
            val (livshendelseType, bruker) = beskrivelse.split(":")
            return "$livshendelseType:" + (if (bruker.isNotEmpty()) "$bruker og" else "") + " barn $personIdent"
        } else {
            val (tekstenForanBarn, tekstenEtterBarn) = beskrivelse.split(" barn ")
            val indexOfBarnCount =
                tekstenForanBarn.indexOfFirst(Char::isDigit).let { if (it > 0) it else tekstenForanBarn.length }
            val nyBarnCount = tekstenForanBarn.substring(indexOfBarnCount).toIntOrNull()?.plus(1) ?: 2
            val nyTekstForanBarn = tekstenForanBarn.substring(0, indexOfBarnCount).trim() + " $nyBarnCount"
            return "$nyTekstForanBarn barn $tekstenEtterBarn $personIdent"
        }
    }

    private fun søkEtterÅpenOppgavePåAktør(
        aktørId: String,
        type: VurderLivshendelseType
    ): Oppgave? {
        val vurderLivshendelseOppgaver = oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderLivshendelse)
        secureLog.info("Fant følgende oppgaver: $vurderLivshendelseOppgaver")
        return vurderLivshendelseOppgaver.firstOrNull {
            it.beskrivelse?.startsWith(type.beskrivelse) == true && (
                it.status != StatusEnum.FERDIGSTILT || it.status != StatusEnum.FEILREGISTRERT
                )
        }
    }

    private fun opprettOppgavePåAktør(
        aktørId: String,
        fagsakId: Long,
        beskrivelse: String,
        behandlingstema: Behandlingstema
    ): OppgaveResponse {
        log.info("Oppretter oppgave for aktørId=$aktørId")

        return oppgaveClient.opprettVurderLivshendelseOppgave(
            OppgaveVurderLivshendelseDto(
                aktørId = aktørId,
                beskrivelse = beskrivelse,
                saksId = fagsakId.toString(),
                behandlingstema = behandlingstema.value,
                behandlesAvApplikasjon = BehandlesAvApplikasjon.BA_SAK.applikasjon
            )
        )
    }

    private fun oppdaterOppgaveMedNyBeskrivelse(
        oppgave: Oppgave,
        beskrivelse: String
    ) {
        if (oppgave.beskrivelse == beskrivelse) return

        secureLog.info("Oppdaterer oppgave (${oppgave.id}) med beskrivelse: $beskrivelse")
        oppgaveClient.oppdaterOppgaveBeskrivelse(oppgave, beskrivelse)
    }

    private fun hentRestFagsak(fagsakId: Long): RestFagsak {
        return sakClient.hentRestFagsak(fagsakId).also {
            secureLog.info("Hentet rest fagsak: $it")
        }
    }

    private fun hentSisteBehandlingSomErIverksatt(restFagsak: RestFagsak): RestUtvidetBehandling? {
        return restFagsak.behandlinger
            .filter { it.steg == STEG_TYPE_BEHANDLING_AVSLUTTET }
            .maxByOrNull { it.opprettetTidspunkt }
    }

    private fun hentAktivBehandling(restFagsak: RestFagsak): RestUtvidetBehandling {
        return restFagsak.behandlinger.firstOrNull { it.aktiv }
            ?: error("Fagsak ${restFagsak.id} mangler aktiv behandling. Får ikke opprettet VurderLivshendelseOppgave")
    }

    private fun tilBehandlingstema(restUtvidetBehandling: RestUtvidetBehandling?): Behandlingstema {
        return when {
            restUtvidetBehandling == null -> Behandlingstema.Barnetrygd
            restUtvidetBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.BarnetrygdEØS
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd
            restUtvidetBehandling.kategori == BehandlingKategori.NASJONAL && restUtvidetBehandling.underkategori == BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd
            else -> Behandlingstema.Barnetrygd
        }
    }

    private val PdlForeldreBarnRelasjon.erBarn: Boolean
        get() = this.relatertPersonsRolle == FORELDERBARNRELASJONROLLE.BARN

    private val List<RestFagsakDeltager>.inneholderBådeForelderOgBarn: Boolean
        get() = this.map(RestFagsakDeltager::rolle).containsAll(listOf(FORELDER, BARN))

    private val Sivilstand.dato: LocalDate?
        get() = this.gyldigFraOgMed ?: this.bekreftelsesdato

    data class Bruker(val ident: String, val fagsakId: Long)

    companion object {

        const val TASK_STEP_TYPE = "vurderLivshendelseTask"

        const val STEG_TYPE_BEHANDLING_AVSLUTTET = "BEHANDLING_AVSLUTTET"
        const val RESULTAT_INNVILGET = "INNVILGET"
        const val BEHANDLING_TYPE_MIGRERING = "MIGRERING_FRA_INFOTRYGD"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType(val beskrivelse: String) {
    DØDSFALL("Dødsfall"),
    SIVILSTAND("Endring i sivilstand. Bruker er registrert som gift"),
    ADDRESSE("Addresse"),
    UTFLYTTING("Utflytting")
}
