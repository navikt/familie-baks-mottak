package no.nav.familie.baks.mottak.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TidKtTest {
    @Nested
    inner class IsEqualOrAfterTest {
        @Test
        fun `skal returnere true om sammenligningstidspunktet er etter det innsendte tidspunktet`() {
            // Arrange
            val kl1030 = LocalTime.of(10, 30)
            val førKl1030 = kl1030.minusNanos(1)

            // Act
            val erLikEllerEtter = kl1030.isEqualOrAfter(førKl1030)

            // Assert
            assertThat(erLikEllerEtter).isTrue()
        }

        @Test
        fun `skal returnere true om sammenligningstidspunktet er lik det innsendte tidspunktet`() {
            // Arrange
            val kl1030v1 = LocalTime.of(10, 30)
            val kl1030v2 = LocalTime.of(10, 30)

            // Act
            val erLikEllerEtter = kl1030v1.isEqualOrAfter(kl1030v2)

            // Assert
            assertThat(erLikEllerEtter).isTrue()
        }

        @Test
        fun `skal returnere false om sammenligningstidspunktet er før det innsendte tidspunktet`() {
            // Arrange
            val kl1030 = LocalTime.of(10, 30)
            val etterKl1030 = kl1030.plusNanos(1)

            // Act
            val erLikEllerEtter = kl1030.isEqualOrAfter(etterKl1030)

            // Assert
            assertThat(erLikEllerEtter).isFalse()
        }
    }

    @Nested
    inner class IsEqualOrBeforeTest {
        @Test
        fun `skal returnere false om sammenligningstidspunktet er etter det innsendte tidspunktet`() {
            // Arrange
            val kl1030 = LocalTime.of(10, 30)
            val førKl1030 = kl1030.minusNanos(1)

            // Act
            val erLikEllerFør = kl1030.isEqualOrBefore(førKl1030)

            // Assert
            assertThat(erLikEllerFør).isFalse()
        }

        @Test
        fun `skal returnere true om sammenligningstidspunktet er lik det innsendte tidspunktet`() {
            // Arrange
            val kl1030v1 = LocalTime.of(10, 30)
            val kl1030v2 = LocalTime.of(10, 30)

            // Act
            val erLikEllerFør = kl1030v1.isEqualOrBefore(kl1030v2)

            // Assert
            assertThat(erLikEllerFør).isTrue()
        }

        @Test
        fun `skal returnere true om sammenligningstidspunktet er før det innsendte tidspunktet`() {
            // Arrange
            val kl1030 = LocalTime.of(10, 30)
            val etterKl1030 = kl1030.plusNanos(1)

            // Act
            val erLikEllerFør = kl1030.isEqualOrBefore(etterKl1030)

            // Assert
            assertThat(erLikEllerFør).isTrue()
        }
    }
}
