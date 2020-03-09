package no.nav.familie.ba.mottak.config

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
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

}

