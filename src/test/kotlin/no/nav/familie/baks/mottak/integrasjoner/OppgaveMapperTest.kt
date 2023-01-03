package no.nav.familie.baks.mottak.integrasjoner

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.DevLauncher
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.OppgaveIdentV2
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(classes = [DevLauncher::class])
@ActiveProfiles("dev", "mock-pdl")
class OppgaveMapperTest(

    @Autowired
    private val journalpostClient: JournalpostClient,

    @Autowired
    private val mockPdlClient: PdlClient
) {

    private val mockHentEnhetClient: HentEnhetClient = mockk(relaxed = true)

    @Test
    fun `skal kaste exception dersom dokumentlisten er tom`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        Assertions.assertThrows(IllegalStateException::class.java) {
            oppgaveMapper.mapTilOpprettOppgave(
                Oppgavetype.Journalføring,
                journalpostClient.hentJournalpost("123")
                    .copy(dokumenter = listOf())
            )
        }
    }

    @Test
    fun `skal kaste exception dersom brukerid ikke er satt når oppgavetype er BehandleSak`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        Assertions.assertThrows(IllegalStateException::class.java) {
            oppgaveMapper.mapTilOpprettOppgave(
                Oppgavetype.BehandleSak,
                journalpostClient.hentJournalpost("123")
                    .copy(
                        dokumenter = listOf(
                            DokumentInfo(
                                tittel = null,
                                brevkode = "",
                                dokumentstatus = null,
                                dokumentvarianter = null
                            )
                        ),
                        bruker = null
                    )
            )
        }
    }

    @Test
    fun `skal sette brukerid til null dersom bruker ikke finnes i PDL når oppgavetype er Journalføring`() {
        every { mockPdlClient.hentIdenter(any()) } throws IntegrasjonException("Fant ikke person")

        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("321")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "NAV 33-00.07",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    )
                )
        )
        assertNull(oppgave.ident)
    }

    @Test
    fun `skal ikke kaste exception selvom brukerid mangler når oppgavetype er Journalføring`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        Assertions.assertDoesNotThrow {
            oppgaveMapper.mapTilOpprettOppgave(
                Oppgavetype.Journalføring,
                journalpostClient.hentJournalpost("123")
                    .copy(
                        dokumenter = listOf(
                            DokumentInfo(
                                tittel = null,
                                brevkode = "",
                                dokumentstatus = null,
                                dokumentvarianter = null
                            )
                        ),
                        bruker = null
                    )
            )
        }
    }

    @Test
    fun `skal sette behandlingstema Ordinær`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "NAV 33-00.07",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    )
                )
        )
        assertEquals(Behandlingstema.OrdinærBarnetrygd.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema Ordinær uavhengig av journalpost`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = null,
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertEquals(Behandlingstema.OrdinærBarnetrygd.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema EØS`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = null,
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    bruker = Bruker(
                        id = "42345678910",
                        type = BrukerIdType.FNR
                    )
                )
        )
        assertNull(oppgave.behandlingstype)
        assertEquals(Behandlingstema.BarnetrygdEØS.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstype Utland`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = null,
                            dokumentstatus = null,
                            dokumentvarianter = null
                        ),
                        DokumentInfo(
                            tittel = null,
                            brevkode = "NAV 33-00.15",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = Behandlingstema.OrdinærBarnetrygd.value
                )
        )
        assertNull(oppgave.behandlingstema)
        assertEquals(Behandlingstype.Utland.value, oppgave.behandlingstype)
    }

    @Test
    fun `skal sette beskrivelse til kun tittel på journalpost når beskrivelse i input er null, brevkode på journalpost er satt og dokumentet har tittel`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = "Whatever",
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                ),
            beskrivelse = null
        )
        assertEquals("Whatever", oppgave.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til tom når beskrivelse i input er null, brevkode på journalpost er ikke satt og dokumentet har tittel`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)

        val oppgaveUtenBeskrivelse1 = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = "Whatever",
                            brevkode = null,
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertEquals("", oppgaveUtenBeskrivelse1.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til tom når beskrivelse i input er null, brevkode på journalpost er ikke satt og dokumentet ikke har tittel`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgaveUtenBeskrivelse2 = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertEquals("", oppgaveUtenBeskrivelse2.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til tittel og beskrivelse når beskrivelse i input er satt, brevkode på journalpost er satt og dokumentet har tittel`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgaveUtenBeskrivelse2 = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = "Whatever",
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                ),
            "beskrivelsefelt"
        )
        assertEquals("Whatever - beskrivelsefelt", oppgaveUtenBeskrivelse2.beskrivelse)
    }

    @Test
    fun `skal sette beskrivelse til kun beskrivelse fra input når dokumentet mangler tittel`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgaveUtenBeskrivelse2 = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                ),
            "beskrivelsefelt"
        )
        assertEquals("beskrivelsefelt", oppgaveUtenBeskrivelse2.beskrivelse)
    }

    @Test
    fun `skal sette enhet 4806 hvis enhet på journalpost er 2101`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    journalforendeEnhet = "2101",
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertThat(oppgave.enhetsnummer).isEqualTo("4806")
    }

    @Test
    fun `skal sette enhet null hvis enhet på journalpost er null`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    journalforendeEnhet = null,
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertThat(oppgave.enhetsnummer).isNull()
    }

    @Test
    fun `skal sette enhet fra journalpost hvis enhet kan behandle oppgaver`() {
        every { mockHentEnhetClient.hentEnhet("4") } returns Enhet("4", "enhetnavn", true, "Aktiv")
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    journalforendeEnhet = "4",
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertThat(oppgave.enhetsnummer).isEqualTo("4")
    }

    @Test
    fun `skal sette enhet null hvis enhet ikke kan behandle oppgaver`() {
        every { mockHentEnhetClient.hentEnhet("5") } returns Enhet("4", "enhetnavn", false, "Aktiv")
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    journalforendeEnhet = "5",
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertThat(oppgave.enhetsnummer).isNull()
    }

    @Test
    fun `skal sette enhet null hvis enhet er nedlagt`() {
        every { mockHentEnhetClient.hentEnhet("5") } returns Enhet("4", "enhetnavn", true, "Nedlagt")
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    journalforendeEnhet = "5",
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertThat(oppgave.enhetsnummer).isNull()
    }

    @Test
    fun `skal sette bruker null hvis Orgnr  er 000000000`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    journalforendeEnhet = "5",
                    bruker = Bruker("000000000", BrukerIdType.ORGNR),
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertThat(oppgave.ident).isNull()
    }

    @Test
    fun `skal sette orgnr hvis Orgnr  er satt`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("123")
                .copy(
                    journalforendeEnhet = "5",
                    bruker = Bruker("900000000", BrukerIdType.ORGNR),
                    dokumenter = listOf(
                        DokumentInfo(
                            tittel = null,
                            brevkode = "kode",
                            dokumentstatus = null,
                            dokumentvarianter = null
                        )
                    ),
                    behandlingstema = "btema"
                )
        )
        assertThat(oppgave.ident).isEqualTo(OppgaveIdentV2("900000000", IdentGruppe.ORGNR))
    }

    @Test
    fun `skal kaste feil dersom tema ikke er satt`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val exception = assertThrows<RuntimeException> {
            oppgaveMapper.mapTilOpprettOppgave(
                Oppgavetype.Journalføring,
                journalpostClient.hentJournalpost("456")
                    .copy(
                        tema = null
                    )
            )
        }
        assertEquals(
            "Feil ved mapping til OpprettOppgaveRequest. Tema for journalpost er tomt eller ugyldig: ${null}",
            exception.message
        )
    }

    @Test
    fun `skal kaste feil dersom tema ikke er finnes i Tema enum`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val exception = assertThrows<RuntimeException> {
            oppgaveMapper.mapTilOpprettOppgave(
                Oppgavetype.Journalføring,
                journalpostClient.hentJournalpost("456")
                    .copy(
                        tema = "UGYLDIG"
                    )
            )
        }
        assertEquals(
            "Feil ved mapping til OpprettOppgaveRequest. Tema for journalpost er tomt eller ugyldig: UGYLDIG",
            exception.message
        )
    }

    @Test
    fun `skal sette riktig tema og behandlingstema på OpprettOppgaveRequest for kontantstøtte`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val opprettOppgaveRequest = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("456")
        )
        assertEquals(Tema.KON, opprettOppgaveRequest.tema)
        assertEquals(Behandlingstema.Kontantstøtte.value, opprettOppgaveRequest.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema KontantstøtteEØS dersom tema er KON og bruker id er Dnummer`() {
        val oppgaveMapper = OppgaveMapper(mockHentEnhetClient, mockPdlClient)
        val opprettOppgaveRequest = oppgaveMapper.mapTilOpprettOppgave(
            Oppgavetype.Journalføring,
            journalpostClient.hentJournalpost("456").copy(
                bruker = Bruker(
                    id = "42345678910",
                    type = BrukerIdType.FNR
                )
            )
        )
        assertEquals(Tema.KON, opprettOppgaveRequest.tema)
        assertEquals(Behandlingstema.KontantstøtteEØS.value, opprettOppgaveRequest.behandlingstema)
    }
}
