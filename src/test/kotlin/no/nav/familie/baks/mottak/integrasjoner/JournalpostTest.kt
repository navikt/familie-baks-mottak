package no.nav.familie.baks.mottak.integrasjoner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JournalpostTest {
    @Nested
    inner class ErDigitalSøknadTest {
        @Test
        fun `skal returnere false hvis brevkode ikke er riktig`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "1",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "NAV_NO",
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                brevkode = "feil brevkode",
                                tittel = "Søknad",
                                dokumentstatus = Dokumentstatus.FERDIGSTILT,
                                dokumentvarianter = emptyList(),
                            ),
                        ),
                )
            // Act
            val erDigitalSøknad = journalpost.erDigitalSøknad()

            // Assert
            assertThat(erDigitalSøknad).isFalse()
        }

        @Test
        fun `skal returnere false hvis kanal ikke er NAV_NO`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "1",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "IKKE_NAV_NO",
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                brevkode = "NAV 33-00.07",
                                tittel = "Søknad",
                                dokumentstatus = Dokumentstatus.FERDIGSTILT,
                                dokumentvarianter = emptyList(),
                            ),
                        ),
                )
            // Act
            val erDigitalSøknad = journalpost.erDigitalSøknad()

            // Assert
            assertThat(erDigitalSøknad).isFalse()
        }

        @Test
        fun `skal returnere true hvis digital kontatstøtte søknad`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "1",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "NAV_NO",
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                brevkode = "NAV 34-00.08",
                                tittel = "Søknad",
                                dokumentstatus = Dokumentstatus.FERDIGSTILT,
                                dokumentvarianter = emptyList(),
                            ),
                        ),
                )
            // Act
            val erDigitalSøknad = journalpost.erDigitalSøknad()

            // Assert
            assertThat(erDigitalSøknad).isTrue()
        }

        @Test
        fun `skal returnere true hvis ordinær digital barnetrygd søknad`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "1",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "NAV_NO",
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                brevkode = "NAV 33-00.07",
                                tittel = "Søknad",
                                dokumentstatus = Dokumentstatus.FERDIGSTILT,
                                dokumentvarianter = emptyList(),
                            ),
                        ),
                )
            // Act
            val erDigitalSøknad = journalpost.erDigitalSøknad()

            // Assert
            assertThat(erDigitalSøknad).isTrue()
        }

        @Test
        fun `skal returnere true hvis utvidet digital barnetrygd søknad`() {
            // Arrange
            val journalpost =
                Journalpost(
                    journalpostId = "1",
                    journalposttype = Journalposttype.I,
                    journalstatus = Journalstatus.MOTTATT,
                    kanal = "NAV_NO",
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                brevkode = "NAV 33-00.09",
                                tittel = "Søknad",
                                dokumentstatus = Dokumentstatus.FERDIGSTILT,
                                dokumentvarianter = emptyList(),
                            ),
                        ),
                )
            // Act
            val erDigitalSøknad = journalpost.erDigitalSøknad()

            // Assert
            assertThat(erDigitalSøknad).isTrue()
        }
    }
}
