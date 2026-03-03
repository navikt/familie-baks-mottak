package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.FalskIdentitet
import no.nav.familie.baks.mottak.integrasjoner.IdentInformasjon
import no.nav.familie.baks.mottak.integrasjoner.Identgruppe
import no.nav.familie.baks.mottak.integrasjoner.KsSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.baks.mottak.integrasjoner.PdlPersonData
import no.nav.familie.baks.mottak.integrasjoner.RestFagsakIdOgTilknyttetAktørId
import no.nav.familie.prosessering.domene.Task
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class VurderFalskIdentitetTaskTest {
    private val pdlClientService = mockk<PdlClientService>()
    private val baSakClient = mockk<BaSakClient>()
    private val ksSakClient = mockk<KsSakClient>()

    private val vurderFalskIdentitetTask =
        VurderFalskIdentitetTask(
            pdlClientService = pdlClientService,
            baSakClient = baSakClient,
            ksSakClient = ksSakClient,
        )

    private val task =
        Task(
            type = VurderFalskIdentitetTask.TASK_STEP_TYPE,
            payload = "12345678910",
        )

    @BeforeEach
    fun setup() {
        every { pdlClientService.hentPerson(any(), any(), any()) } answers { PdlPersonData(falskIdentitet = FalskIdentitet(erFalsk = true)) }
        every { pdlClientService.hentIdenter(any(), any()) } answers { listOf(IdentInformasjon(ident = firstArg(), historisk = false, gruppe = Identgruppe.FOLKEREGISTERIDENT.name)) }
    }

    @Test
    fun `skal ikke kaste feil hvis det ikke er falsk identitet`() {
        // Arrange
        every { pdlClientService.hentPerson(any(), any(), any()) } answers { PdlPersonData(falskIdentitet = null) }

        // Act & Assert
        assertDoesNotThrow { vurderFalskIdentitetTask.doTask(task) }
    }

    @Test
    fun `skal ikke kaste feil hvis det ikke er løpende fagsak i ba-sak eller ks-sak`() {
        // Arrange
        every { baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns emptyList()
        every { ksSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns emptyList()

        // Act & Assert
        assertDoesNotThrow { vurderFalskIdentitetTask.doTask(task) }
    }

    @Test
    fun `skal kaste feil hvis det er løpende fagsak i ba-sak`() {
        // Arrange
        every { baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
            listOf(RestFagsakIdOgTilknyttetAktørId(aktørId = "1234567891011", fagsakId = 1))
        every { ksSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns emptyList()

        // Act
        val feilmelding = assertThrows<Exception> { vurderFalskIdentitetTask.doTask(task) }

        // Arrange
        assertThat(feilmelding.message).contains("Løpende fagsaker i ba-sak: 1")
        assertThat(feilmelding.message).doesNotContain("Løpende fagsaker i ks-sak:")
    }

    @Test
    fun `skal kaste feil hvis det er løpende fagsak i ks-sak`() {
        // Arrange
        every { baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns emptyList()
        every { ksSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns
            listOf(RestFagsakIdOgTilknyttetAktørId(aktørId = "1234567891011", fagsakId = 1))

        // Act
        val feilmelding = assertThrows<Exception> { vurderFalskIdentitetTask.doTask(task) }

        // Arrange
        assertThat(feilmelding.message).contains("Løpende fagsaker i ks-sak: 1")
        assertThat(feilmelding.message).doesNotContain("Løpende fagsaker i ba-sak:")
    }

    @Test
    fun `skal kaste feil hvis det er løpende fagsak i ba-sak og ks-sak`() {
        // Arrange
        every { baSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) } returns
            listOf(
                RestFagsakIdOgTilknyttetAktørId(aktørId = "1234567891011", fagsakId = 1),
                RestFagsakIdOgTilknyttetAktørId(aktørId = "1234567891011", fagsakId = 2),
            )
        every { ksSakClient.hentFagsakerHvorPersonErSøkerEllerMottarKontantstøtte(any()) } returns
            listOf(RestFagsakIdOgTilknyttetAktørId(aktørId = "1234567891011", fagsakId = 3))

        // Act
        val feilmelding = assertThrows<Exception> { vurderFalskIdentitetTask.doTask(task) }

        // Arrange
        assertThat(feilmelding.message).contains("Løpende fagsaker i ba-sak: 1, 2")
        assertThat(feilmelding.message).contains("Løpende fagsaker i ks-sak: 3")
    }
}
