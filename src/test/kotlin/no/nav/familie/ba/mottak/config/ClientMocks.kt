package no.nav.familie.ba.mottak.config

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.ba.mottak.søknad.PdfService
import no.nav.familie.ba.mottak.søknad.SøknadService
import no.nav.familie.ba.mottak.søknad.SøknadTestData
import no.nav.familie.ba.mottak.søknad.domene.DBVedlegg
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.kontrakter.felles.dokarkiv.ArkiverDokumentResponse
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
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
            mockOppgaveClient.opprettJournalføringsoppgave(any())
        } returns OppgaveResponse(1L)

        return mockOppgaveClient
    }


    @Bean
    @Primary
    fun mockAktørClient(): AktørClient {

        val mockAktørClient = mockk<AktørClient>(relaxed = true)

        every {
            mockAktørClient.hentAktørId(any())
        } returns "42"

        every {
            mockAktørClient.hentPersonident(any())
        } returns "12345678910"

        return mockAktørClient
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
            throw HttpClientErrorException.Conflict.create(HttpStatus.CONFLICT, null, null, null, null)
        }
        return mockDokarkivClientConflict
    }

    @Bean
    @Primary
    @Profile("mock-søknad-service")
    fun mockSøknadService(): SøknadService {
        val mockSøknadService = mockk<SøknadService>(relaxed = true)
        val søknad = SøknadTestData.søknad()
        val dbSøknad = søknad.tilDBSøknad()

        every {
            mockSøknadService.hentDBSøknad(any())
        } returns dbSøknad

        every {
            mockSøknadService.lagreDBSøknad(any())
        } answers {
            dbSøknad
        }

        every {
            mockSøknadService.hentLagredeVedlegg(any())
        } returns mapOf<String, DBVedlegg>()

        return mockSøknadService
    }
    @Bean
    @Primary
    @Profile("mock-pdf-service")
    fun mockPdfService(): PdfService {
        val mockPdfService = mockk<PdfService>(relaxed = true)
        every {
            mockPdfService.lagPdf(any())
        } returns "11223344".toByteArray()
        return mockPdfService
    }

    @Bean
    @Primary
    fun mockJournalpostClient(): JournalpostClient {

        val mockJournalpostClient = mockk<JournalpostClient>(relaxed = true)

        every {
            mockJournalpostClient.hentJournalpost("123")
        } returns Journalpost(journalpostId = "123",
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("123456789012", BrukerIdType.AKTOERID),
                              tema = "BAR",
                              kanal = "SKAN_NETS",
                              behandlingstema = null,
                              dokumenter = null,
                              journalforendeEnhet = null,
                              sak = null
        )

        every {
            mockJournalpostClient.hentJournalpost("321")
        } returns Journalpost(journalpostId = "321",
                              journalposttype = Journalposttype.I,
                              journalstatus = Journalstatus.MOTTATT,
                              bruker = Bruker("12345678901", BrukerIdType.FNR),
                              tema = "BAR",
                              kanal = "SKAN_NETS"
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
    fun mockFeatureToggleService(): FeatureToggleService {
        val mockFeatureToggleClient = mockk<FeatureToggleService>(relaxed = true)

        every {
            mockFeatureToggleClient.isEnabled(any())
        } returns true

        every {
            mockFeatureToggleClient.isEnabled(any(), any())
        } returns true

        return mockFeatureToggleClient
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
}

