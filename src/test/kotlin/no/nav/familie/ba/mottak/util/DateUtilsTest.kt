package no.nav.familie.ba.mottak.util

import io.mockk.every
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalDateTime

class DateUtilsTest {


    @ParameterizedTest
    @CsvSource(
            "2020-04-01T00:00:00, 2020-04-01", //før kl 14 skal returnere samme dag
            "2020-04-01T14:00:01, 2020-04-02", //etter kl 14 skal returnere neste dag
            "2020-04-04T00:00:00, 2020-04-06", //lørdag skal returnere mandag
            "2020-04-04T00:00:00, 2020-04-06", //søndag skal returnere mandag
            "2021-05-15T00:00:00, 2021-05-18",  //14 mai er fredag, 17 mai er mandag og fridag, skal returnere 18 mai
            "2020-12-25T00:00:00, 2020-12-28", //første arbeidsdag er mandag 28
            "2020-12-26T00:00:00, 2020-12-28", //første arbeidsdag er mandag 28
            "2020-01-01T00:00:00, 2020-01-02"
    )
    fun `fristFerdigstillelese skal returnere neste arbeidsdag`(input: LocalDateTime, expected: LocalDate) {
        mockkStatic(LocalDateTime::class)

        every { LocalDateTime.now() } returns input

        assertThat(fristFerdigstillelse()).isEqualTo(expected)
    }
}