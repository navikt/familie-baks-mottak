package no.nav.familie.baks.mottak.config

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.DokarkivClient
import no.nav.familie.baks.mottak.integrasjoner.JournalpostClient
import no.nav.familie.baks.mottak.integrasjoner.OppgaveClient
import no.nav.familie.baks.mottak.integrasjoner.PdfClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.søknad.FamiliePdfClient
import no.nav.familie.http.client.RessursException
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.dokarkiv.ArkiverDokumentResponse
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import no.nav.familie.kontrakter.felles.journalpost.DokumentInfo
import no.nav.familie.kontrakter.felles.journalpost.Journalpost
import no.nav.familie.kontrakter.felles.journalpost.Journalposttype
import no.nav.familie.kontrakter.felles.journalpost.Journalstatus
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException

@Component
class ClientMocks {
    @Bean
    @Primary
    fun mockOppgaveClient(): OppgaveClient {
        val mockOppgaveClient = mockk<OppgaveClient>(relaxed = true)

        every {
            mockOppgaveClient.opprettJournalføringsoppgave(any(), any())
        } returns OppgaveResponse(1L)

        return mockOppgaveClient
    }

    @Bean
    @Primary
    @Profile("mock-dokarkiv")
    fun mockDokarkivClient(): DokarkivClient {
        val mockDokarkivClient = mockk<DokarkivClient>(relaxed = true)

        every {
            mockDokarkivClient.arkiver(any())
        } returns ArkiverDokumentResponse(journalpostId = "123", ferdigstilt = false)
        return mockDokarkivClient
    }

    @Bean
    @Primary
    @Profile("mock-dokarkiv-conflict")
    fun mockDokarkivClientConflict(): DokarkivClient {
        val mockDokarkivClientConflict = mockk<DokarkivClient>(relaxed = true)
        every {
            mockDokarkivClientConflict.arkiver(any())
        } answers {
            throw RessursException(
                Ressurs(status = Ressurs.Status.FEILET, data = null, melding = "", stacktrace = ""),
                HttpClientErrorException.Conflict.create(HttpStatus.CONFLICT, "", HttpHeaders(), null, null),
                HttpStatus.CONFLICT,
            )
        }
        return mockDokarkivClientConflict
    }

    @Bean
    @Primary
    fun mockJournalpostClient(): JournalpostClient {
        val mockJournalpostClient = mockk<JournalpostClient>(relaxed = true)

        every {
            mockJournalpostClient.hentJournalpost("123")
        } returns
            Journalpost(
                journalpostId = "123",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "BAR",
                kanal = "SKAN_NETS",
                behandlingstema = null,
                dokumenter = null,
                journalforendeEnhet = null,
                sak = null,
            )

        every {
            mockJournalpostClient.hentJournalpost("321")
        } returns
            Journalpost(
                journalpostId = "321",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("12345678901", BrukerIdType.FNR),
                tema = "BAR",
                kanal = "SKAN_NETS",
            )

        every { mockJournalpostClient.hentJournalpost("456") } returns
            Journalpost(
                journalpostId = "1456",
                journalposttype = Journalposttype.I,
                journalstatus = Journalstatus.MOTTATT,
                bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                tema = "KON",
                kanal = "NO_NAV",
                behandlingstema = null,
                dokumenter =
                    listOf(
                        DokumentInfo(
                            tittel = "Søknad om kontantstøtte til småbarnsforeldre",
                            brevkode = "34-00.08",
                            dokumentstatus = null,
                            dokumentvarianter = emptyList(),
                            dokumentInfoId = "id",
                        ),
                    ),
                journalforendeEnhet = null,
                sak = null,
            )

        return mockJournalpostClient
    }

    @Bean
    @Profile("mock-pdl")
    fun mockPdlClient(): PdlClient {
        val mockPdlClient = mockk<PdlClient>(relaxed = true)
        return mockPdlClient
    }

    @Bean
    @Primary
    @Profile("mock-dokgen")
    fun mockPdfClient(): PdfClient {
        val mockPdfClient = mockk<PdfClient>()

        every {
            mockPdfClient.lagPdf(any(), any())
        } returns "abc".toByteArray()

        return mockPdfClient
    }

    @Bean
    @Primary
    @Profile("mock-familie-pdf")
    fun mockFamiliePdfClient(): FamiliePdfClient {
        val mockFamiliePdfClient = mockk<FamiliePdfClient>()
        every {
            mockFamiliePdfClient.opprettPdf(any())
        } returns "abc".toByteArray()

        return mockFamiliePdfClient
    }
}
