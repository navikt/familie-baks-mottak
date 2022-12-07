package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.domene.SøknadSpråkvelgerService
import no.nav.familie.baks.mottak.søknad.domene.SøknadV8
import no.nav.familie.baks.mottak.util.DbContainerInitializer
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsfelt
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad
import no.nav.familie.kontrakter.felles.objectMapper
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class SøknadsfeltSpråkTest(
    @Autowired val søknadSpråkvelgerService: SøknadSpråkvelgerService
) {
    @Test
    fun `Kan velge språk for søknadsfelter`() {
        val søknad: Søknad = mockk()
        every { søknad.originalSpråk } returns "nb"
        every { søknad.spørsmål } returns mapOf(
            "testSpørsmål" to Søknadsfelt(
                label = mapOf(
                    "nb" to "TestSpørsmål",
                    "en" to "TestQuestion"
                ),
                verdi = mapOf(
                    "nb" to "TestSvar",
                    "en" to "TestAnswer"
                )
            )
        )
        every { søknad.teksterUtenomSpørsmål } returns mapOf()

        var barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(SøknadV8(søknad = søknad), "en")
        var barnetrygdSøknadJsonNode = objectMapper.valueToTree<JsonNode>(barnetrygdSøknadMap)
        assertEquals("TestAnswer", barnetrygdSøknadJsonNode["spørsmål"]["testSpørsmål"]["verdi"].textValue())

        barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(SøknadV8(søknad = søknad), "nb")
        barnetrygdSøknadJsonNode = objectMapper.valueToTree<JsonNode>(barnetrygdSøknadMap)
        assertEquals("TestSvar", barnetrygdSøknadJsonNode["spørsmål"]["testSpørsmål"]["verdi"].textValue())
    }

    @Test
    fun `Påvirker ikke global objectmapping`() {
        val søknad: Søknad = mockk()
        every { søknad.originalSpråk } returns "nb"
        every { søknad.spørsmål } returns mapOf(
            "testSpørsmål" to Søknadsfelt(
                label = mapOf(
                    "nb" to "TestSpørsmål",
                    "en" to "TestQuestion"
                ),
                verdi = mapOf(
                    "nb" to "TestSvar",
                    "en" to "TestAnswer"
                )
            )
        )

        val asJson = objectMapper.writeValueAsString(søknad)
        assertNotEquals(-1, asJson.indexOf("TestSpørsmål"))
        assertNotEquals(-1, asJson.indexOf("TestSvar"))
        assertNotEquals(-1, asJson.indexOf("TestQuestion"))
        assertNotEquals(-1, asJson.indexOf("TestAnswer"))
    }

    @Test
    fun `Håndterer nested SøknadsFelter korrekt`() {
        val søknad: Søknad = mockk()
        every { søknad.originalSpråk } returns "nb"
        every { søknad.spørsmål } returns mapOf(
            "testSpørsmål" to Søknadsfelt(
                label = mapOf(
                    "nb" to "TestSpørsmål",
                    "en" to "TestQuestion"
                ),
                verdi = mapOf(
                    "nb" to Søknadsfelt(
                        label = mapOf(
                            "nb" to "TestNøstetLabel",
                            "en" to "TestNestedLabel"
                        ),
                        verdi = mapOf(
                            "nb" to "TestNøstetVerdi",
                            "en" to "TestNestedValue"
                        )
                    ),
                    "en" to Søknadsfelt(
                        label = mapOf(
                            "nb" to "TestNøstetLabel",
                            "en" to "TestNestedLabel"
                        ),
                        verdi = mapOf(
                            "nb" to "TestNøstetVerdi",
                            "en" to "TestNestedValue"
                        )
                    )
                )
            )
        )
        every { søknad.teksterUtenomSpørsmål } returns mapOf()

        val barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(SøknadV8(søknad = søknad), "nb")
        val barnetrygdSøknadJsonNode = objectMapper.valueToTree<JsonNode>(barnetrygdSøknadMap)
        assertEquals("TestNøstetVerdi", barnetrygdSøknadJsonNode["spørsmål"]["testSpørsmål"]["verdi"]["verdi"].textValue())
    }

    @Test
    fun `Håndterer språkvalg for ekstratekster`() {
        val søknad: Søknad = mockk()
        every { søknad.originalSpråk } returns "nb"
        every { søknad.teksterUtenomSpørsmål } returns mapOf(
            "test.tekst" to mapOf(
                "nb" to "TestTekst",
                "en" to "TestText"
            )
        )

        val barnetrygdSøknadMap = søknadSpråkvelgerService.konverterBarnetrygdSøknadTilMapForSpråk(SøknadV8(søknad = søknad), "nb")
        val barnetrygdSøknadJsonNode = objectMapper.valueToTree<JsonNode>(barnetrygdSøknadMap)
        assertEquals("TestTekst", barnetrygdSøknadJsonNode["teksterUtenomSpørsmål"]["test.tekst"].textValue())
    }
}
