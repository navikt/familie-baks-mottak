package no.nav.familie.ba.mottak.util

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.core.env.Environment
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


    @ParameterizedTest
    @CsvSource(
            "2020-06-09T13:37:00, 2020-06-10T13:37", //Neste dag er en arbeidsdag
            "2020-06-12T13:37:00, 2020-06-15T13:37", //Neste dag er en lørdag. Venter til mandag samme tid
            "2020-06-13T13:37:00, 2020-06-15T13:37", //Neste dag er en søndag. Venter til mandag samme tid
            "2020-06-13T06:37:00, 2020-06-15T10:37", //Ikke kjøre før kl 10 mandag
            "2020-06-11T16:01:00, 2020-06-15T10:01", //Ikke kjøre fredag etter 16. Vent til mandag
            "2021-05-14T00:00:00, 2021-05-18T10:00" //14 mai er fredag, 17 mai er mandag og fridag, skal returnere 18 mai
    )
    fun `skal returnere neste arbeidsdag `(input: LocalDateTime, expected: LocalDateTime) {
        mockkStatic(LocalDateTime::class)
        mockk<Environment>(relaxed = true)


        every { LocalDateTime.now() } returns input

        assertThat(nesteGyldigeTriggertidFødselshendelser(1440, mockk<Environment>(relaxed = true))).isEqualTo(expected)
    }


}