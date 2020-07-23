package no.nav.familie.ba.mottak.søknad

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SøknadTreeWalkerTest {

    val søknad = SøknadTestData.søknad()
    val mapSøknadsfelter = SøknadTreeWalker.mapSøknadsfelter(søknad)
    val verdiliste = mapSøknadsfelter["verdiliste"] as List<Map<String, Any?>>

    @Test
    fun `mapSøknadsfelter returnerer en map-struktur med feltene fra søknaden`() {
        assertTrue(mapSøknadsfelter.isNotEmpty())
        assertEquals("Søknad barnetrygd - NAV 33-00.07", mapSøknadsfelter["label"])
        assertEquals(3, verdiliste.size) // tre verdilister: søker, barn og søknadstype
    }

    @Test
    fun `Test at alle labels har en tilhørende verdi (alle verdiers klasse i endNodes)`() {
        assertTrue(
                verdiliste.none { it.containsKey("verdiliste") && (it["verdiliste"] as List<*>).isEmpty() }
        )
    }

    @Test
    fun `Test at verdier bevares og holdes sammen`() {
        val barneliste = verdiliste[2]["verdiliste"] as List<Map<String, Any?>>
        val barn1 = toMap(barneliste[0]["verdiliste"])
        val barn2 = toMap(barneliste[1]["verdiliste"])
        assertEquals(
                Pair("barn1", "4 år"),
                Pair(barn1["Barnets fulle navn"], barn1["alder"])
        )
        assertEquals(
                Pair("barn2", "1 år"),
                Pair(barn2["Barnets fulle navn"], barn2["alder"])
        )
    }

    /** Gjør om liste av maps med nøkler: ("label", "verdi") til én map: ("label"-verdi -> "verdi"-verdi) */
    fun toMap(list: Any?): Map<String, Any?> {
        return (list as List<Map<String, Any?>>).map { it["label"] as String to it["verdi"] }.toMap() // Må ha liste av par for å kjøpre toMap
    }
}