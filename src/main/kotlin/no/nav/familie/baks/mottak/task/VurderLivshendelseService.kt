package no.nav.familie.baks.mottak.task

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.baks.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.InfotrygdBarnetrygdClient
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveVurderLivshendelseDto
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.PdlNotFoundException
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakIdOgTilknyttetAktørId
import no.nav.familie.baks.mottak.integrasjoner.RestMinimalFagsak
import no.nav.familie.baks.mottak.integrasjoner.RestVisningBehandling
import no.nav.familie.baks.mottak.integrasjoner.Sivilstand
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import no.nav.familie.kontrakter.felles.oppgave.StatusEnum
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.error.RekjørSenereException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Service
class VurderLivshendelseService(
    private val oppgaveClient: OppgaveClient,
    private val pdlClient: PdlClient,
    private val baSakClient: BaSakClient,
    private val ksSakClient: KsSakClient,
    private val infotrygdBarnetrygdClient: InfotrygdBarnetrygdClient,
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val secureLog: Logger = LoggerFactory.getLogger("secureLogger")

    val oppgaveBarnetrygdOpprettetDødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall.oppgave.opprettet")
    val oppgaveBarnetrygdOpprettetUtflyttingCounter: Counter = Metrics.counter("barnetrygd.utflytting.oppgave.opprettet")
    val oppgaveBarnetrygdOpprettetSivilstandCounter: Counter = Metrics.counter("barnetrygd.sivilstand.oppgave.opprettet")

    val oppgaveKontantstøtteOpprettetDødsfallCounter: Counter = Metrics.counter("kontantstotte.dodsfall.oppgave.opprettet")
    val oppgaveKontantstøtteOpprettetUtflyttingCounter: Counter = Metrics.counter("kontantstotte.utflytting.oppgave.opprettet")

    fun vurderLivshendelseOppgave(
        task: Task,
        tema: Tema,
    ) {
        val payload = objectMapper.readValue(task.payload, VurderLivshendelseTaskDTO::class.java)
        val personIdent = payload.personIdent
        val type = payload.type

        try {
            val identer = pdlClient.hentIdenter(personIdent = personIdent, tema)
            if (identer.firstOrNull { it.ident == personIdent }?.gruppe != Identgruppe.FOLKEREGISTERIDENT.name) {
                log.warn("Hendelse ignoreres siden ident ikke er av gruppe FOLKEREGISTERIDENT")
                secureLog.warn("Hendelse ignoreres siden ident ikke er av gruppe FOLKEREGISTERIDENT $identer")
                return
            }
        } catch (e: PdlNotFoundException) {
            log.warn("Hendelse ignoreres siden ident ikke eksisterer")
            secureLog.warn("Hendelse ignoreres siden ident ikke eksisterer for $personIdent")
            return
        }

        when (type) {
            VurderLivshendelseType.DØDSFALL -> {
                secureLog.info("Har mottatt dødsfallshendelse for person $personIdent")
                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-relasjon-dødsfall", tema)
                secureLog.info("dødsfallshendelse person fødselsdato = ${pdlPersonData.fødsel.firstOrNull()}")

                val berørteBrukere = finnBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(personIdent, tema)
                secureLog.info(
                    "berørteBrukere count = ${berørteBrukere.size}, aktørIder = ${
                        berørteBrukere.fold("") { aktørIder, it -> aktørIder + " " + it.aktørId }
                    } tema = $tema ",
                )

                if (berørteBrukere.isNotEmpty() && pdlPersonData.dødsfall.firstOrNull()?.dødsdato == null) {
                    secureLog.info("Har mottatt dødsfallshendelse uten dødsdato $pdlPersonData")
                    throw RekjørSenereException(
                        årsak = "Har mottatt dødsfallshendelse uten dødsdato",
                        triggerTid = LocalDateTime.now().plusDays(1),
                    )
                }

                berørteBrukere.forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(
                            hendelseType = VurderLivshendelseType.DØDSFALL,
                            aktørIdForOppgave = it.aktørId,
                            fagsakIdForOppgave = it.fagsakId,
                            personIdent = personIdent,
                            task = task,
                            tema = tema,
                        )
                    ) {
                        økOpprettetDødsfallCounter(tema)
                    }
                }
            }

            VurderLivshendelseType.UTFLYTTING -> {
                finnBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(personIdent, tema).forEach {
                    if (opprettEllerOppdaterVurderLivshendelseOppgave(
                            hendelseType = VurderLivshendelseType.UTFLYTTING,
                            aktørIdForOppgave = it.aktørId,
                            fagsakIdForOppgave = it.fagsakId,
                            personIdent = personIdent,
                            task = task,
                            tema = tema,
                        )
                    ) {
                        økOpprettetUtflyttingCounter(tema)
                    }
                }
            }

            VurderLivshendelseType.SIVILSTAND -> {
                if (tema != Tema.BAR) {
                    throw RuntimeException("Det er bare tema ${Tema.BAR} som støtter sivilstand hendelser")
                }

                val pdlPersonData = pdlClient.hentPerson(personIdent, "hentperson-sivilstand", tema)
                val sivilstand =
                    finnNyesteSivilstandEndring(pdlPersonData) ?: run {
                        secureLog.info("Ignorerer sivilstandhendelse for $personIdent uten dato: $pdlPersonData")
                        return
                    }
                if (sivilstand.type != SIVILSTAND.GIFT) {
                    secureLog.info("Endringen til sivilstand GIFT for $personIdent er korrigert/annulert: $pdlPersonData")
                    return
                }
                finnBaBrukereBerørtAvSivilstandHendelseForIdent(personIdent).forEach {
                    if (sjekkOmDatoErEtterEldsteVedtaksdato(dato = sivilstand.dato!!, aktivFaksak = hentRestFagsak(it.fagsakId, tema), personIdent = personIdent, tema = tema)) { // Trenger denne sjekken for å unngå "gamle" hendelser som feks kan skyldes innflytting
                        opprettEllerOppdaterEndringISivilstandOppgave(
                            endringsdato = sivilstand.dato!!,
                            fagsakIdForOppgave = it.fagsakId,
                            aktørIdForOppgave = it.aktørId,
                            personIdent = personIdent,
                            task = task,
                            tema = tema,
                        )
                    }
                }
            }

            else -> log.debug("Behandlinger enda ikke livshendelse av type ${payload.type}")
        }
    }

    private fun økOpprettetUtflyttingCounter(tema: Tema) {
        when (tema) {
            Tema.BAR -> oppgaveBarnetrygdOpprettetUtflyttingCounter.increment()
            Tema.KON -> oppgaveKontantstøtteOpprettetUtflyttingCounter.increment()
            Tema.ENF, Tema.OPP -> throw RuntimeException("Tema $tema er ikke støttet ")
        }
    }

    private fun økOpprettetDødsfallCounter(tema: Tema) {
        when (tema) {
            Tema.BAR -> oppgaveBarnetrygdOpprettetDødsfallCounter.increment()
            Tema.KON -> oppgaveKontantstøtteOpprettetDødsfallCounter.increment()
            Tema.ENF, Tema.OPP -> throw RuntimeException("Tema $tema er ikke støttet ")
        }
    }

    private fun finnBaBrukereBerørtAvSivilstandHendelseForIdent(
        personIdent: String,
    ): List<RestFagsakIdOgTilknyttetAktørId> {
        val listeMedFagsakIdOgTilknyttetAktørId = baSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(personIdent)
        secureLog.info("Aktører og fagsaker berørt av hendelse for personIdent=$personIdent: ${listeMedFagsakIdOgTilknyttetAktørId.map { "(aktørId=${it.aktørId}, fagsakId=${it.fagsakId})," }}")
        return listeMedFagsakIdOgTilknyttetAktørId
    }

    private val Sivilstand.dato: LocalDate?
        get() = this.gyldigFraOgMed ?: this.bekreftelsesdato

    private fun finnNyesteSivilstandEndring(pdlPersonData: PdlPersonData): Sivilstand? {
        return pdlPersonData.sivilstand.filter { it.dato != null }.maxByOrNull { it.dato!! }
    }

    private fun opprettEllerOppdaterVurderLivshendelseOppgave(
        hendelseType: VurderLivshendelseType,
        aktørIdForOppgave: String,
        fagsakIdForOppgave: Long,
        personIdent: String,
        task: Task,
        tema: Tema,
    ): Boolean {
        val åpenOppgave = søkEtterÅpenOppgavePåAktør(aktørIdForOppgave, hendelseType, tema)

        if (åpenOppgave == null) {
            val beskrivelse =
                leggTilNyPersonIBeskrivelse(
                    beskrivelse = "${hendelseType.beskrivelse}:",
                    personIdent = personIdent,
                    personErBruker = pdlClient.hentAktørId(personIdent, tema) == aktørIdForOppgave,
                )
            val restFagsak = hentRestFagsak(fagsakIdForOppgave, tema)
            val restBehandling = hentSisteBehandlingSomErIverksatt(restFagsak) ?: hentAktivBehandling(restFagsak)

            val behandlingstema = hentBehandlingstema(tema, restBehandling)

            val oppgave = opprettOppgavePåAktør(aktørIdForOppgave, fagsakIdForOppgave, beskrivelse, behandlingstema, tema)

            task.metadata["oppgaveId"] = oppgave.oppgaveId.toString()
            secureLog.info(
                "Opprettet VurderLivshendelse-oppgave (${oppgave.oppgaveId}) for $hendelseType-hendelse (person aktørId:  $aktørIdForOppgave)" +
                    ", beskrivelsestekst: $beskrivelse",
            )
            return true
        } else {
            log.info("Fant åpen oppgave på aktørId=$aktørIdForOppgave oppgaveId=${åpenOppgave.id}")
            secureLog.info("Fant åpen oppgave: $åpenOppgave")
            val beskrivelse =
                leggTilNyPersonIBeskrivelse(
                    beskrivelse = åpenOppgave.beskrivelse!!,
                    personIdent = personIdent,
                    personErBruker =
                        åpenOppgave.identer?.map { it.ident }
                            ?.contains(personIdent),
                )

            oppdaterOppgaveMedNyBeskrivelse(åpenOppgave, beskrivelse)
            task.metadata["oppgaveId"] = åpenOppgave.id.toString()
            task.metadata["info"] = "Fant åpen oppgave"
            return false
        }
    }

    private fun hentBehandlingstema(
        tema: Tema,
        restBehandling: RestVisningBehandling,
    ) = when (tema) {
        Tema.BAR -> tilBarnetrygdBehandlingstema(restBehandling)
        Tema.KON -> tilKontanstøtteBehandlingstema(restBehandling)
        Tema.ENF, Tema.OPP -> throw RuntimeException("Tema $tema er ikke støttet")
    }

    private fun søkEtterÅpenOppgavePåAktør(
        aktørId: String,
        type: VurderLivshendelseType,
        tema: Tema,
    ): Oppgave? {
        val vurderLivshendelseOppgaver = oppgaveClient.finnOppgaverPåAktørId(aktørId, Oppgavetype.VurderLivshendelse, tema)
        secureLog.info("Fant følgende oppgaver: $vurderLivshendelseOppgaver")
        return vurderLivshendelseOppgaver.firstOrNull {
            it.beskrivelse?.startsWith(type.beskrivelse) == true && (
                it.status != StatusEnum.FERDIGSTILT || it.status != StatusEnum.FEILREGISTRERT
            )
        }
    }

    private fun leggTilNyPersonIBeskrivelse(
        beskrivelse: String,
        personIdent: String,
        personErBruker: Boolean?,
    ): String {
        return when (personErBruker) {
            true -> if (!beskrivelse.contains("bruker")) leggTilBrukerIBeskrivelse(beskrivelse) else beskrivelse
            else -> if (!beskrivelse.contains(personIdent)) leggTilBarnIBeskrivelse(beskrivelse, personIdent) else beskrivelse
        }
    }

    private fun leggTilBrukerIBeskrivelse(beskrivelse: String): String {
        val (livshendelseType, barn) = beskrivelse.split(":")
        return "$livshendelseType: bruker" + if (barn.isNotEmpty()) " og $barn" else ""
    }

    private fun leggTilBarnIBeskrivelse(
        beskrivelse: String,
        personIdent: String,
    ): String {
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

    private fun hentRestFagsak(
        fagsakId: Long,
        tema: Tema,
    ): RestMinimalFagsak {
        return when (tema) {
            Tema.BAR -> baSakClient.hentMinimalRestFagsak(fagsakId)
            Tema.KON -> ksSakClient.hentMinimalRestFagsak(fagsakId)
            Tema.ENF, Tema.OPP -> throw RuntimeException("Tema $tema er ikke støttet")
        }.also {
            secureLog.info("Hentet rest fagsak: $it, tema: $tema")
        }
    }

    private fun hentSisteBehandlingSomErIverksatt(restFagsak: RestMinimalFagsak): RestVisningBehandling? {
        return restFagsak.behandlinger
            .filter { it.status == STATUS_AVSLUTTET }
            .maxByOrNull { it.opprettetTidspunkt }
    }

    private fun hentAktivBehandling(restFagsak: RestMinimalFagsak): RestVisningBehandling {
        return restFagsak.behandlinger.firstOrNull { it.aktiv }
            ?: error("Fagsak ${restFagsak.id} mangler aktiv behandling. Får ikke opprettet VurderLivshendelseOppgave")
    }

    private fun opprettOppgavePåAktør(
        aktørId: String,
        fagsakId: Long,
        beskrivelse: String,
        behandlingstema: Behandlingstema,
        tema: Tema,
    ): OppgaveResponse {
        log.info("Oppretter oppgave for aktørId=$aktørId")

        return oppgaveClient.opprettVurderLivshendelseOppgave(
            OppgaveVurderLivshendelseDto(
                aktørId = aktørId,
                beskrivelse = beskrivelse,
                saksId = fagsakId.toString(),
                behandlingstema = behandlingstema.value,
                tema = tema,
            ),
        )
    }

    private fun oppdaterOppgaveMedNyBeskrivelse(
        oppgave: Oppgave,
        beskrivelse: String,
    ) {
        if (oppgave.beskrivelse == beskrivelse) return

        secureLog.info("Oppdaterer oppgave (${oppgave.id}) med beskrivelse: $beskrivelse")
        oppgaveClient.oppdaterOppgaveBeskrivelse(oppgave, beskrivelse)
    }

    private fun finnBrukereBerørtAvDødsfallEllerUtflyttingHendelseForIdent(
        personIdent: String,
        tema: Tema,
    ): List<RestFagsakIdOgTilknyttetAktørId> {
        val listeMedFagsakIdOgTilknyttetAktør =
            when (tema) {
                Tema.BAR -> baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent)
                Tema.KON -> ksSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(personIdent)
                Tema.ENF, Tema.OPP -> throw RuntimeException("Tema $tema er ikke støttet")
            }

        secureLog.info("Aktører og fagsaker berørt av hendelse for personIdent=$personIdent: ${listeMedFagsakIdOgTilknyttetAktør.map { "(aktørId=${it.aktørId}, fagsakId=${it.fagsakId})," }}, tema: $tema")
        return listeMedFagsakIdOgTilknyttetAktør
    }

    private fun tilBarnetrygdBehandlingstema(restBehandling: RestVisningBehandling?): Behandlingstema {
        return when {
            restBehandling == null -> Behandlingstema.Barnetrygd
            restBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.BarnetrygdEØS
            restBehandling.kategori == BehandlingKategori.NASJONAL && restBehandling.underkategori == BehandlingUnderkategori.ORDINÆR -> Behandlingstema.OrdinærBarnetrygd
            restBehandling.kategori == BehandlingKategori.NASJONAL && restBehandling.underkategori == BehandlingUnderkategori.UTVIDET -> Behandlingstema.UtvidetBarnetrygd
            else -> Behandlingstema.Barnetrygd
        }
    }

    private fun tilKontanstøtteBehandlingstema(restBehandling: RestVisningBehandling?): Behandlingstema =
        when {
            restBehandling == null -> Behandlingstema.Kontantstøtte
            restBehandling.kategori == BehandlingKategori.EØS -> Behandlingstema.KontantstøtteEØS
            else -> Behandlingstema.Kontantstøtte
        }

    private fun sjekkOmDatoErEtterEldsteVedtaksdato(
        dato: LocalDate,
        aktivFaksak: RestMinimalFagsak,
        personIdent: String,
        tema: Tema,
    ): Boolean {
        val tidligsteVedtakIBaSak =
            aktivFaksak.behandlinger
                .filter { it.resultat == RESULTAT_INNVILGET && it.status == STATUS_AVSLUTTET }
                .minByOrNull { it.opprettetTidspunkt } ?: return false

        if (dato.isAfter(tidligsteVedtakIBaSak.opprettetTidspunkt.toLocalDate())) {
            return true
        }

        val erEtterTidligsteInfotrygdVedtak =
            if (tidligsteVedtakIBaSak.type == BEHANDLING_TYPE_MIGRERING) {
                hentTidligsteVedtaksdatoFraInfotrygd(personIdent, tema)?.isBefore(dato) ?: false
            } else {
                false
            }

        return erEtterTidligsteInfotrygdVedtak
    }

    private fun opprettEllerOppdaterEndringISivilstandOppgave(
        endringsdato: LocalDate,
        fagsakIdForOppgave: Long,
        aktørIdForOppgave: String,
        personIdent: String,
        task: Task,
        tema: Tema,
    ) {
        val formatertDato =
            endringsdato.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(Locale.of("no")),
            ) ?: "ukjent dato"

        val initiellBeskrivelse =
            hentInitiellBeskrivelseForSivilstandOppgave(
                personErBruker = pdlClient.hentAktørId(personIdent, tema) == aktørIdForOppgave,
                formatertDato = formatertDato,
                personIdent = personIdent,
            )

        val oppgave =
            søkEtterÅpenOppgavePåAktør(aktørIdForOppgave, VurderLivshendelseType.SIVILSTAND, tema)
                ?: opprettOppgavePåAktør(
                    aktørId = aktørIdForOppgave,
                    fagsakId = fagsakIdForOppgave,
                    beskrivelse = initiellBeskrivelse,
                    behandlingstema = Behandlingstema.UtvidetBarnetrygd,
                    tema = tema,
                )

        when (oppgave) {
            is OppgaveResponse -> {
                secureLog.info(
                    "Opprettet VurderLivshendelse-oppgave (${oppgave.oppgaveId}) for ${VurderLivshendelseType.SIVILSTAND}-hendelse (person i hendelse:  $personIdent, oppgave på person: $aktørIdForOppgave)" +
                        ", beskrivelsestekst: $initiellBeskrivelse",
                )
                oppgaveBarnetrygdOpprettetSivilstandCounter.increment()
                task.metadata["oppgaveId"] = oppgave.oppgaveId.toString()
            }

            is Oppgave -> {
                log.info("Fant åpen oppgave på aktørId=$aktørIdForOppgave oppgaveId=${oppgave.id}")
                secureLog.info("Fant åpen oppgave: $oppgave")
                oppdaterOppgaveMedNyBeskrivelse(oppgave = oppgave, beskrivelse = "${VurderLivshendelseType.SIVILSTAND.beskrivelse}: Bruker eller barn er registrert som gift")
                task.metadata["oppgaveId"] = oppgave.id.toString()
                task.metadata["info"] = "Fant åpen oppgave"
            }
        }
    }

    private fun hentTidligsteVedtaksdatoFraInfotrygd(
        personIdent: String,
        tema: Tema,
    ): LocalDate? {
        val personIdenter =
            pdlClient.hentIdenter(personIdent, tema)
                .filter { it.gruppe == Identgruppe.FOLKEREGISTERIDENT.name }
                .map { it.ident }
        val tidligsteInfotrygdVedtak =
            infotrygdBarnetrygdClient.hentVedtak(personIdenter).bruker
                .maxByOrNull { it.iverksattFom ?: "000000" } // maxBy... siden datoen er på "seq"-format
        return tidligsteInfotrygdVedtak?.iverksattFom
            ?.let { YearMonth.parse("${999999 - it.toInt()}", DateTimeFormatter.ofPattern("yyyyMM")) }
            ?.atDay(1)
    }

    private fun hentInitiellBeskrivelseForSivilstandOppgave(
        personErBruker: Boolean,
        formatertDato: String,
        personIdent: String,
    ): String =
        "${VurderLivshendelseType.SIVILSTAND.beskrivelse}: ${if (personErBruker) "bruker" else "barn $personIdent"} er registrert som gift fra $formatertDato"

    companion object {
        const val STATUS_AVSLUTTET = "AVSLUTTET"
        const val RESULTAT_INNVILGET = "INNVILGET"
        const val BEHANDLING_TYPE_MIGRERING = "MIGRERING_FRA_INFOTRYGD"
    }
}

data class VurderLivshendelseTaskDTO(val personIdent: String, val type: VurderLivshendelseType)

enum class VurderLivshendelseType(val beskrivelse: String) {
    DØDSFALL("Dødsfall"),
    SIVILSTAND("Endring i sivilstand"),
    ADDRESSE("Addresse"),
    UTFLYTTING("Utflytting"),
}
