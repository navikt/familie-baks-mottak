package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import no.nav.familie.baks.mottak.søknad.JournalførteSøknaderRepository
import no.nav.familie.baks.mottak.søknad.Søknadstype
import no.nav.familie.baks.mottak.task.FjernJournalførteSøknader.Companion.BATCHSTØRRELSE
import no.nav.familie.baks.mottak.task.FjernJournalførteSøknader.Companion.MAKS_ANTALL_BATCHER
import no.nav.familie.leader.LeaderClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FjernJournalførteSøknaderTest {
    private val journalførteSøknaderRepository = mockk<JournalførteSøknaderRepository>()
    private val fjernJournalførteSøknader = FjernJournalførteSøknader(journalførteSøknaderRepository)

    @BeforeEach
    fun beforeEach() {
        mockkStatic(LeaderClient::class)
    }

    @AfterEach
    fun afterEach() {
        unmockkStatic(LeaderClient::class)
    }

    @Test
    fun `skal slette i batcher til en batch ikke er full`() {
        // Arrange
        every { LeaderClient.isLeader(any(), any()) } returns true
        every { journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.BARNETRYGD, any(), BATCHSTØRRELSE) } returnsMany
            listOf(BATCHSTØRRELSE, BATCHSTØRRELSE, 3)
        every { journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.KONTANTSTØTTE, any(), BATCHSTØRRELSE) } returns 0

        // Act
        fjernJournalførteSøknader.fjernJournalførteSøknader()

        // Assert
        verify(exactly = 3) { journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.BARNETRYGD, any(), BATCHSTØRRELSE) }
        verify(exactly = 1) { journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.KONTANTSTØTTE, any(), BATCHSTØRRELSE) }
    }

    @Test
    fun `skal ikke slette flere batcher enn maks antall per kjøring`() {
        // Arrange
        every { LeaderClient.isLeader(any(), any()) } returns true
        every { journalførteSøknaderRepository.slettJournalførteSøknader(any(), any(), BATCHSTØRRELSE) } returns BATCHSTØRRELSE

        // Act
        fjernJournalførteSøknader.fjernJournalførteSøknader()

        // Assert
        verify(exactly = MAKS_ANTALL_BATCHER) { journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.BARNETRYGD, any(), BATCHSTØRRELSE) }
        verify(exactly = MAKS_ANTALL_BATCHER) { journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.KONTANTSTØTTE, any(), BATCHSTØRRELSE) }
    }

    @Test
    fun `skal ikke slette noe når poden ikke er leader`() {
        // Arrange
        every { LeaderClient.isLeader(any(), any()) } returns false

        // Act
        fjernJournalførteSøknader.fjernJournalførteSøknader()

        // Assert
        verify(exactly = 0) { journalførteSøknaderRepository.slettJournalførteSøknader(any(), any(), any()) }
    }
}
