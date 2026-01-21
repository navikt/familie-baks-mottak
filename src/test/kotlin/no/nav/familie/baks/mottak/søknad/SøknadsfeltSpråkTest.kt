package no.nav.familie.baks.mottak.søknad

import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.restklient.config.jsonMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@ExtendWith(SpringExtension::class)
@ActiveProfiles("postgres", "testcontainers")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class SøknadsfeltSpråkTest(
    @Autowired val søknadSpråkvelgerService: SøknadSpråkvelgerService,
) {
    @Test
    fun `Kan velge språk for søknadsfelter`() {
        val barnetrygdSøknad: BarnetrygdSøknad = mockk(relaxed = true)
        every { barnetrygdSøknad.originalSpråk } returns "nb"
        every { barnetrygdSøknad.spørsmål } returns
            mapOf(
                "testSpørsmål" to
                    Søknadsfelt(
                        label =
                            mapOf(
                                "nb" to "TestSpørsmål",
                                "en" to "TestQuestion",
                            ),
                        verdi =
                            mapOf(
                                "nb" to "TestSvar",
                                "en" to "TestAnswer",
                            ),
                    ),
            )
        every { barnetrygdSøknad.teksterUtenomSpørsmål } returns mapOf()

        var barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(VersjonertBarnetrygdSøknadV9(barnetrygdSøknad = barnetrygdSøknad), "en")
        var barnetrygdSøknadJsonNode: JsonNode = jsonMapper.valueToTree(barnetrygdSøknadMap)
        assertEquals("TestAnswer", barnetrygdSøknadJsonNode["spørsmål"]["testSpørsmål"]["verdi"].textValue())

        barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(VersjonertBarnetrygdSøknadV9(barnetrygdSøknad = barnetrygdSøknad), "nb")
        barnetrygdSøknadJsonNode = jsonMapper.valueToTree(barnetrygdSøknadMap)
        assertEquals("TestSvar", barnetrygdSøknadJsonNode["spørsmål"]["testSpørsmål"]["verdi"].textValue())
    }

    @Test
    fun `Påvirker ikke global objectmapping`() {
        val barnetrygdSøknad: BarnetrygdSøknad = mockk(relaxed = true)
        every { barnetrygdSøknad.originalSpråk } returns "nb"
        every { barnetrygdSøknad.spørsmål } returns
            mapOf(
                "testSpørsmål" to
                    Søknadsfelt(
                        label =
                            mapOf(
                                "nb" to "TestSpørsmål",
                                "en" to "TestQuestion",
                            ),
                        verdi =
                            mapOf(
                                "nb" to "TestSvar",
                                "en" to "TestAnswer",
                            ),
                    ),
            )

        val asJson = jsonMapper.writeValueAsString(barnetrygdSøknad)
        assertNotEquals(-1, asJson.indexOf("TestSpørsmål"))
        assertNotEquals(-1, asJson.indexOf("TestSvar"))
        assertNotEquals(-1, asJson.indexOf("TestQuestion"))
        assertNotEquals(-1, asJson.indexOf("TestAnswer"))
    }

    @Test
    fun `Håndterer nested SøknadsFelter korrekt`() {
        val barnetrygdSøknad: BarnetrygdSøknad = mockk(relaxed = true)
        every { barnetrygdSøknad.originalSpråk } returns "nb"
        every { barnetrygdSøknad.spørsmål } returns
            mapOf(
                "testSpørsmål" to
                    Søknadsfelt(
                        label =
                            mapOf(
                                "nb" to "TestSpørsmål",
                                "en" to "TestQuestion",
                            ),
                        verdi =
                            mapOf(
                                "nb" to
                                    Søknadsfelt(
                                        label =
                                            mapOf(
                                                "nb" to "TestNøstetLabel",
                                                "en" to "TestNestedLabel",
                                            ),
                                        verdi =
                                            mapOf(
                                                "nb" to "TestNøstetVerdi",
                                                "en" to "TestNestedValue",
                                            ),
                                    ),
                                "en" to
                                    Søknadsfelt(
                                        label =
                                            mapOf(
                                                "nb" to "TestNøstetLabel",
                                                "en" to "TestNestedLabel",
                                            ),
                                        verdi =
                                            mapOf(
                                                "nb" to "TestNøstetVerdi",
                                                "en" to "TestNestedValue",
                                            ),
                                    ),
                            ),
                    ),
            )
        every { barnetrygdSøknad.teksterUtenomSpørsmål } returns mapOf()

        val barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(VersjonertBarnetrygdSøknadV9(barnetrygdSøknad = barnetrygdSøknad), "nb")
        val barnetrygdSøknadJsonNode: JsonNode = jsonMapper.valueToTree(barnetrygdSøknadMap)
        assertEquals("TestNøstetVerdi", barnetrygdSøknadJsonNode["spørsmål"]["testSpørsmål"]["verdi"]["verdi"].textValue())
    }

    @Test
    fun `Håndterer språkvalg for ekstratekster`() {
        val barnetrygdSøknad: BarnetrygdSøknad = mockk(relaxed = true)
        every { barnetrygdSøknad.originalSpråk } returns "nb"
        every { barnetrygdSøknad.teksterUtenomSpørsmål } returns
            mapOf(
                "test.tekst" to
                    mapOf(
                        "nb" to "TestTekst",
                        "en" to "TestText",
                    ),
            )

        val barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(VersjonertBarnetrygdSøknadV9(barnetrygdSøknad = barnetrygdSøknad), "nb")
        val barnetrygdSøknadJsonNode: JsonNode = jsonMapper.valueToTree(barnetrygdSøknadMap)
        assertEquals("TestTekst", barnetrygdSøknadJsonNode["teksterUtenomSpørsmål"]["test.tekst"].textValue())
    }
}
