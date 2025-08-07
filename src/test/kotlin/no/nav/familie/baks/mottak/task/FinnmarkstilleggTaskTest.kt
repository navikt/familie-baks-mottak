package no.nav.familie.baks.mottak.task

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.familie.baks.mottak.integrasjoner.BaSakClient
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.ba.finnmarkstillegg.KommunerIFinnmarkOgNordTroms
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.personopplysning.Bostedsadresse
import no.nav.familie.prosessering.domene.Task
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import kotlin.test.Test

class FinnmarkstilleggTaskTest {
    private val mockBaSakClient: BaSakClient = mockk()
    private val mockPdlClient: PdlClient = mockk()
    private val finnmarkstilleggTask = FinnmarkstilleggTask(mockPdlClient, mockBaSakClient)

    @Test
    fun `ikke send melding om Finnmarkstillegg hvis person ikke har noen løpende saker`() {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd("123") } returns emptyList()

        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, "123")
        finnmarkstilleggTask.doTask(task)

        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(any()) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
        verify(exactly = 0) { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR) }
    }

    @Test
    fun `ìkke send melding om Finnmarkstillegg hvis person ikke har bostedsadresse`() {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd("123") } returns listOf(mockk())
        every { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns emptyList()

        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, "123")
        finnmarkstilleggTask.doTask(task)

        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(any()) }
        verify(exactly = 1) { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @ParameterizedTest
    @EnumSource(KommunerIFinnmarkOgNordTroms::class)
    fun `send melding til ba-sak hvis person flytter fra en kommune som ikke har Finnmarkstillleg til en av kommune som skal ha Finnmarkstillegg`(input: KommunerIFinnmarkOgNordTroms) {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd("123") } returns listOf(mockk())
        every { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2023-01-01"), vegadresse = mockk { every { kommunenummer } returns input.kommunenummer }),
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns "0301" }),
            )
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }

        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, "123")
        finnmarkstilleggTask.doTask(task)

        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(any()) }
        verify(exactly = 1) { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @ParameterizedTest
    @EnumSource(KommunerIFinnmarkOgNordTroms::class)
    fun `send melding til ba-sak hvis person flytter fra en av kommune som skal ha Finnmarkstillegg til en kommune som ikke har Finnmarkstillegg`(input: KommunerIFinnmarkOgNordTroms) {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd("123") } returns listOf(mockk())
        every { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2023-01-01"), vegadresse = mockk { every { kommunenummer } returns "0301" }),
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns input.kommunenummer }),
            )
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }

        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, "123")
        finnmarkstilleggTask.doTask(task)

        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(any()) }
        verify(exactly = 1) { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 1) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding til ba-sak hvis person flytter fra en av kommune som skal ha Finnmarkstillegg til en anne kommune som har Finnmarkstillegg`() {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd("123") } returns listOf(mockk())
        every { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2023-01-01"), vegadresse = mockk { every { kommunenummer } returns KommunerIFinnmarkOgNordTroms.BERLEVÅG.kommunenummer }),
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns KommunerIFinnmarkOgNordTroms.ALTA.kommunenummer }),
            )
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }

        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, "123")
        finnmarkstilleggTask.doTask(task)

        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(any()) }
        verify(exactly = 1) { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }

    @Test
    fun `ikke send melding til ba-sak hvis person flytter fra en av kommune som ikke skal ha Finnmarkstillegg til en anne kommune som ikke har Finnmarkstillegg`() {
        every { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd("123") } returns listOf(mockk())
        every { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR).bostedsadresse } returns
            listOf(
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2023-01-01"), vegadresse = mockk { every { kommunenummer } returns "0301" }),
                Bostedsadresse(gyldigFraOgMed = LocalDate.parse("2022-01-01"), vegadresse = mockk { every { kommunenummer } returns "0301" }),
            )
        justRun { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }

        val task = Task(FinnmarkstilleggTask.TASK_STEP_TYPE, "123")
        finnmarkstilleggTask.doTask(task)

        verify(exactly = 1) { mockBaSakClient.hentFagsakerHvorPersonMottarLøpendeUtvidetEllerOrdinærBarnetrygd(any()) }
        verify(exactly = 1) { mockPdlClient.hentPerson("123", "hentperson-med-bostedsadresse", Tema.BAR) }
        verify(exactly = 0) { mockBaSakClient.sendFinnmarkstilleggTilBaSak(any()) }
    }
}
