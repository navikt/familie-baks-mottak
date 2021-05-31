package no.nav.familie.ba.mottak.config

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.dokarkiv.ArkiverDokumentResponse
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

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
    @Profile("mock-dokgen")
    fun mockPdfClient(): PdfClient {
        val mockPdfClient = mockk<PdfClient>()


        every {
            mockPdfClient.lagPdf(any())
        } returns "abc".toByteArray()

        return mockPdfClient
    }
}

