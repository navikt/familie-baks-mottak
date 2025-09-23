package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.OppholdAnnetSted
import no.nav.familie.kontrakter.felles.personopplysning.Oppholdsadresse
import no.nav.familie.prosessering.domene.Task
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import kotlin.test.Test

class SvalbardtilleggTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk()
    private val mockUnleashNextMedContextService: UnleashNextMedContextService = mockk(relaxed = true)
    private val svalbardtilleggTask = SvalbardtilleggTask(mockPdlClient, mockBaSakClient, mockUnleashNextMedContextService)
    private val personIdent = "123"

    @BeforeEach
    fun setUp() {
        every { mockUnleashNextMedContextService.isEnabled(any()) } returns true
    }

    @Test
    fun `ìkke send melding om Svalbardtillegg hvis person ikke har oppholdsadresse`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns emptyList()

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendSvalbardtilleggTilBaSak(any()) }
    }

    @Test
    fun `ìkke send melding om Svalbardtillegg hvis person har oppholdsadresse annet sted enn Svalbard`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), oppholdAnnetSted = OppholdAnnetSted.UTENRIKS.name))

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendSvalbardtilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding om Svalbardtillegg hvis person ikke har noen løpende saker`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns emptyList()

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 0) { mockBaSakClient.sendSvalbardtilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding om Svalbardtillegg hvis toggle er skrudd av`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        every { mockUnleashNextMedContextService.isEnabled(FeatureToggleConfig.SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK) } returns false

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockUnleashNextMedContextService.isEnabled(FeatureToggleConfig.SEND_OPPHOLDSADRESSE_HENDELSER_TIL_BA_SAK) }
        verify(exactly = 0) { mockBaSakClient.sendSvalbardtilleggTilBaSak(any()) }
    }

    @Test
    fun `send melding til ba-sak hvis person flytter til Svalbard`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        justRun { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }
    }

    @Test
    fun `send melding til ba-sak hvis person flytter fra Svalbard`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR).oppholdsadresse } returns
            listOf(Oppholdsadresse(gyldigFraOgMed = LocalDate.of(2025, 1, 1), gyldigTilOgMed = LocalDate.of(2025, 8, 31), oppholdAnnetSted = OppholdAnnetSted.PAA_SVALBARD.name))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        justRun { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }

        val task = Task(SvalbardtilleggTask.TASK_STEP_TYPE, personIdent)

        // Act
        svalbardtilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-oppholdsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockBaSakClient.sendSvalbardtilleggTilBaSak(personIdent) }
    }
}
