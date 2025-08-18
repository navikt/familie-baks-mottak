package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleConfig
import no.nav.familie.baks.mottak.config.featureToggle.UnleashNextMedContextService
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.ba.finnmarkstillegg.KommunerIFinnmarkOgNordTroms
import no.nav.familie.kontrakter.ba.finnmarkstillegg.KommunerIFinnmarkOgNordTroms.ALTA
import no.nav.familie.kontrakter.ba.finnmarkstillegg.KommunerIFinnmarkOgNordTroms.BERLEVÅG
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.prosessering.domene.Task
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import kotlin.test.Test

class FinnmarkstilleggTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk()
    private val mockUnleashNextMedContextService: UnleashNextMedContextService = mockk(relaxed = true)
    private val finnmarkstilleggTask = FinnmarkstilleggTask(mockPdlClient, mockBaSakClient, mockUnleashNextMedContextService)
    private val personIdent = "123"
    private val osloKommunenummer = "0301"

    @BeforeEach
    fun setUp() {
        every { mockUnleashNextMedContextService.isEnabled(any()) } returns true
    }

    @Test
    fun `ikke send melding om Finnmarkstillegg hvis bostedskommune fra hendelse er null`() {
        // Arrange
        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, null, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 0) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding om Finnmarkstillegg hvis bostedskommuneFomDato fra hendelse er null`() {
        // Arrange
        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, null)
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 0) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ìkke send melding om Finnmarkstillegg hvis person ikke har bostedsadresse`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns emptyList()

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding til ba-sak hvis person flytter fra en kommune som skal ha Finnmarkstillegg til en annen kommune som har Finnmarkstillegg`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns ALTA.kommunenummer }))

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, BERLEVÅG.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding til ba-sak hvis person flytter fra en av kommune som ikke skal ha Finnmarkstillegg til en annen kommune som ikke har Finnmarkstillegg`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns osloKommunenummer }))

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, osloKommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding om Finnmarkstillegg hvis person ikke har noen løpende saker`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns osloKommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns emptyList()

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding om Finnmarkstillegg hvis toggle er skrudd av`() {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns osloKommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        every { mockUnleashNextMedContextService.isEnabled(FeatureToggleConfig.SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK) } returns false

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, ALTA.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockUnleashNextMedContextService.isEnabled(FeatureToggleConfig.SEND_BOSTEDSADRESSE_HENDELSER_TIL_BA_SAK) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @ParameterizedTest
    @EnumSource(KommunerIFinnmarkOgNordTroms::class)
    fun `send melding til ba-sak hvis person flytter fra en kommune som ikke har Finnmarkstillleg til en av kommune som skal ha Finnmarkstillegg`(input: KommunerIFinnmarkOgNordTroms) {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns osloKommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, input.kommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }
    }

    @ParameterizedTest
    @EnumSource(KommunerIFinnmarkOgNordTroms::class)
    fun `send melding til ba-sak hvis person flytter fra en av kommune som skal ha Finnmarkstillegg til en kommune som ikke har Finnmarkstillegg`(input: KommunerIFinnmarkOgNordTroms) {
        // Arrange
        every { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns input.kommunenummer }))
        every { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) } returns listOf(mockk())
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }

        val taskDto = VurderFinnmarkstillleggTaskDTO(personIdent, osloKommunenummer, LocalDate.now())
        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, objectMapper.writeValueAsString(taskDto))

        // Act
        finnmarkstilleggTask.doTask(task)

        // Assert
        verify(exactly = 1) { mockPdlClient.hentPerson(personIdent, "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonErSøkerEllerMottarOrdinærBarnetrygd(personIdent) }
        verify(exactly = 1) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(personIdent) }
    }
}
