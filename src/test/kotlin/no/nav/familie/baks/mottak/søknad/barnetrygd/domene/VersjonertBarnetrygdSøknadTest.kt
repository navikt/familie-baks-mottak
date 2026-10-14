package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import no.nav.familie.baks.mottak.søknad.SøknadTestData
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v7.Søknadsvedlegg
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VersjonertBarnetrygdSøknadTest {
    @Test
    fun `skal ha dokumentasjonsbehov uten vedlegg når et dokumentasjonsbehov hverken har opplastet vedlegg eller er sendt inn tidligere`() {
        // Arrange
        val søknad = lagSøknad(lagDokumentasjon(Dokumentasjonsbehov.AVTALE_DELT_BOSTED))

        // Act
        val harDokumentasjonsbehovUtenVedlegg = søknad.harDokumentasjonsbehovUtenVedlegg()

        // Assert
        assertThat(harDokumentasjonsbehovUtenVedlegg).isTrue()
    }

    @Test
    fun `skal ikke ha dokumentasjonsbehov uten vedlegg når dokumentasjonsbehovet har opplastet vedlegg`() {
        // Arrange
        val søknad = lagSøknad(lagDokumentasjon(Dokumentasjonsbehov.AVTALE_DELT_BOSTED, opplastedeVedlegg = listOf(lagVedlegg())))

        // Act
        val harDokumentasjonsbehovUtenVedlegg = søknad.harDokumentasjonsbehovUtenVedlegg()

        // Assert
        assertThat(harDokumentasjonsbehovUtenVedlegg).isFalse()
    }

    @Test
    fun `skal ha dokumentasjonsbehov uten vedlegg når dokumentasjonen er sendt inn tidligere uten at vedlegg er lastet opp`() {
        // Arrange
        val søknad = lagSøknad(lagDokumentasjon(Dokumentasjonsbehov.AVTALE_DELT_BOSTED, harSendtInn = true))

        // Act
        val harDokumentasjonsbehovUtenVedlegg = søknad.harDokumentasjonsbehovUtenVedlegg()

        // Assert
        assertThat(harDokumentasjonsbehovUtenVedlegg).isTrue()
    }

    @Test
    fun `skal ikke ha dokumentasjonsbehov uten vedlegg når kun annen dokumentasjon mangler vedlegg`() {
        // Arrange
        val søknad = lagSøknad(lagDokumentasjon(Dokumentasjonsbehov.ANNEN_DOKUMENTASJON))

        // Act
        val harDokumentasjonsbehovUtenVedlegg = søknad.harDokumentasjonsbehovUtenVedlegg()

        // Assert
        assertThat(harDokumentasjonsbehovUtenVedlegg).isFalse()
    }

    @Test
    fun `skal ha dokumentasjonsbehov uten vedlegg når ett av flere dokumentasjonsbehov mangler vedlegg`() {
        // Arrange
        val søknad =
            lagSøknad(
                lagDokumentasjon(Dokumentasjonsbehov.MEKLINGSATTEST, opplastedeVedlegg = listOf(lagVedlegg())),
                lagDokumentasjon(Dokumentasjonsbehov.BOR_FAST_MED_SØKER),
            )

        // Act
        val harDokumentasjonsbehovUtenVedlegg = søknad.harDokumentasjonsbehovUtenVedlegg()

        // Assert
        assertThat(harDokumentasjonsbehovUtenVedlegg).isTrue()
    }

    private fun lagSøknad(vararg dokumentasjon: Søknaddokumentasjon) = VersjonertBarnetrygdSøknadV9(barnetrygdSøknad = SøknadTestData.barnetrygdSøknad(dokumentasjon = dokumentasjon.toList()))

    private fun lagDokumentasjon(
        dokumentasjonsbehov: Dokumentasjonsbehov,
        harSendtInn: Boolean = false,
        opplastedeVedlegg: List<Søknadsvedlegg> = emptyList(),
    ) = Søknaddokumentasjon(
        dokumentasjonsbehov = dokumentasjonsbehov,
        harSendtInn = harSendtInn,
        opplastedeVedlegg = opplastedeVedlegg,
        dokumentasjonSpråkTittel = mapOf("nb" to dokumentasjonsbehov.name),
    )

    private fun lagVedlegg() =
        Søknadsvedlegg(
            dokumentId = "en-slags-uuid",
            navn = "vedlegg.pdf",
            tittel = Dokumentasjonsbehov.AVTALE_DELT_BOSTED,
        )
}
