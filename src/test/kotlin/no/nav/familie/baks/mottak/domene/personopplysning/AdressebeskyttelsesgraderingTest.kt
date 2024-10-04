package no.nav.familie.baks.mottak.domene.personopplysning

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AdressebeskyttelsesgraderingTest {

    @Nested
    inner class ErStrengtFortrolig {

        @ParameterizedTest
        @EnumSource(value = Adressebeskyttelsesgradering::class, names = ["STRENGT_FORTROLIG"], mode = EnumSource.Mode.EXCLUDE)
        fun `skal returnere false dersom adressebeskyttelsegradering ikke er STRENGT_FORTROLIG`(adressebeskyttelsesgradering: Adressebeskyttelsesgradering) {
            // Act & Assert
            assertThat(adressebeskyttelsesgradering.erStrengtFortrolig()).isFalse
        }

        @Test
        fun `skal returnere true dersom adressebeskyttelsegradering er STRENGT_FORTROLIG`() {
            // Act & Assert
            assertThat(Adressebeskyttelsesgradering.STRENGT_FORTROLIG.erStrengtFortrolig()).isTrue
        }

    }

    @Nested
    inner class ErStregtFortroligUtland {

        @ParameterizedTest
        @EnumSource(value = Adressebeskyttelsesgradering::class, names = ["STRENGT_FORTROLIG_UTLAND"], mode = EnumSource.Mode.EXCLUDE)
        fun `skal returnere false dersom adressebeskyttelsegradering ikke er STRENGT_FORTROLIG_UTLAND`(adressebeskyttelsesgradering: Adressebeskyttelsesgradering) {
            // Act & Assert
            assertThat(adressebeskyttelsesgradering.erStrengtFortroligUtland()).isFalse
        }

        @Test
        fun `skal returnere true dersom adressebeskyttelsegradering er STRENGT_FORTROLIG_UTLAND`() {
            // Act & Assert
            assertThat(Adressebeskyttelsesgradering.STRENGT_FORTROLIG_UTLAND.erStrengtFortroligUtland()).isTrue
        }
    }

    @Nested
    inner class ErFortrolig {

        @ParameterizedTest
        @EnumSource(value = Adressebeskyttelsesgradering::class, names = ["FORTROLIG"], mode = EnumSource.Mode.EXCLUDE)
        fun `skal returnere false dersom adressebeskyttelsegradering ikke er FORTROLIG`(adressebeskyttelsesgradering: Adressebeskyttelsesgradering) {
            // Act & Assert
            assertThat(adressebeskyttelsesgradering.erFortrolig()).isFalse
        }

        @Test
        fun `skal returnere true dersom adressebeskyttelsegradering er FORTROLIG`() {
            // Act & Assert
            assertThat(Adressebeskyttelsesgradering.FORTROLIG.erFortrolig()).isTrue
        }

    }

    @Nested
    inner class ErUgradert {

        @ParameterizedTest
        @EnumSource(value = Adressebeskyttelsesgradering::class, names = ["UGRADERT"], mode = EnumSource.Mode.EXCLUDE)
        fun `skal returnere false dersom adressebeskyttelsegradering ikke er UGRADERT`(adressebeskyttelsesgradering: Adressebeskyttelsesgradering) {
            // Act & Assert
            assertThat(adressebeskyttelsesgradering.erUgradert()).isFalse
        }

        @Test
        fun `skal returnere true dersom adressebeskyttelsegradering er UGRADERT`() {
            // Act & Assert
            assertThat(Adressebeskyttelsesgradering.UGRADERT.erUgradert()).isTrue
        }
    }
}