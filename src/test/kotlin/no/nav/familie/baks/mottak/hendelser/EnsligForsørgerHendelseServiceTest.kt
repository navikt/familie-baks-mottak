package no.nav.familie.baks.mottak.hendelser

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.domene.HendelseConsumer
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.baks.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.ef.EnsligForsørgerVedtakhendelse
import no.nav.familie.kontrakter.felles.ef.StønadType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnsligForsørgerHendelseServiceTest {
    lateinit var mockHendelsesloggRepository: HendelsesloggRepository
    lateinit var mockSakClient: SakClient
    lateinit var mockPdlClient: PdlClient

    lateinit var service: EnsligForsørgerHendelseService

    @BeforeEach
    internal fun setUp() {
        mockHendelsesloggRepository = mockk(relaxed = true)
        mockSakClient = mockk(relaxed = true)
        mockPdlClient = mockk(relaxed = true)
        service = EnsligForsørgerHendelseService(mockSakClient, mockPdlClient, mockHendelsesloggRepository)
        clearAllMocks()
    }

    @Test
    fun `Skal gjøre kall mot ba-sak hvis det er en overgangstønad som ikke er prosessert før`() {
        service.prosesserEfVedtakHendelse(42, EnsligForsørgerVedtakhendelse(100, "01020300110", StønadType.OVERGANGSSTØNAD))

        verify(exactly = 1) {
            mockSakClient.sendVedtakOmOvergangsstønadHendelseTilSak("01020300110")
        }

        verify(exactly = 1) {
            mockHendelsesloggRepository.save(any())
        }
    }

    @Test
    fun `Skal ignorere hendelse fra ef hvis alt lest`() {
        every { mockHendelsesloggRepository.existsByHendelseIdAndConsumer("200", HendelseConsumer.EF_VEDTAK_V1) } returns true

        service.prosesserEfVedtakHendelse(42, EnsligForsørgerVedtakhendelse(200, "01020300110", StønadType.OVERGANGSSTØNAD))

        verify(exactly = 0) {
            mockSakClient.sendVedtakOmOvergangsstønadHendelseTilSak("01020300110")
        }
        verify(exactly = 0) {
            mockHendelsesloggRepository.save(any())
        }
    }
}
