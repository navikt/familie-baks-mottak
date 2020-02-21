package no.nav.familie.ba.mottak.config

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import io.mockk.slot
import no.nav.familie.ba.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
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

        return mockAktørClient
    }

    @Bean
    @Primary
    fun mockJournalpostClient(): JournalpostClient {

        val mockJournalpostClient = mockk<JournalpostClient>(relaxed = true)

        val slot = slot<String>()



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

        return mockJournalpostClient
    }

}

