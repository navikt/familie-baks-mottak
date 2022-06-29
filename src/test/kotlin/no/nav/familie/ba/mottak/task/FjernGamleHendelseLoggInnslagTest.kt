package no.nav.familie.ba.mottak.task

import no.nav.familie.ba.mottak.DevLauncherPostgres
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.util.DbContainerInitializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDate.now
import java.time.LocalDateTime

@ExtendWith(SpringExtension::class)
@ContextConfiguration(initializers = [DbContainerInitializer::class])
@ActiveProfiles("postgres")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class FjernGamleHendelseLoggInnslagTest {

    @Autowired
    lateinit var hendelseLoggRepository: HendelsesloggRepository

    lateinit var fjernGamleHendelseLoggInnslag: FjernGamleHendelseLoggInnslag

    @BeforeEach
    fun setUp() {
        fjernGamleHendelseLoggInnslag = FjernGamleHendelseLoggInnslag(hendelseLoggRepository)
    }

    @Test
    fun `Skal slette hendelser fra hendelse_logg eldre enn 2 måneder, bortsett for de fra topic EF_VEDTAK_INFOTRYGD_V1 med retention -1`() {
        val opprettetDatoer = listOf(
            now(),
            now().minusMonths(1),
            now().minusMonths(3),
            now().minusYears(1)
        )
        val hendelserFørRyddeJobb = hendelseLoggRepository.saveAllAndFlush(
            opprettetDatoer.mapIndexed { idx, dato ->
                Hendelseslogg(
                    offset = idx.toLong(),
                    hendelseId = idx.toString(),
                    consumer = HendelseConsumer.values()[idx],
                    opprettetTidspunkt = dato.atStartOfDay()
                )
            }
        )
        fjernGamleHendelseLoggInnslag.slettHendelserEldreEnn2MånederFraTopicsMedMindreRetentionTid()

        val hendelserEtterRyddeJobb = hendelseLoggRepository.findAll()

        assertThat(hendelserFørRyddeJobb).hasSize(4)
        assertThat(hendelserEtterRyddeJobb).hasSize(3)
        assertThat(hendelserEtterRyddeJobb).doesNotContainAnyElementsOf(
            hendelserFørRyddeJobb
                .filter { it.opprettetTidspunkt.isBefore(LocalDateTime.now().minusMonths(2)) }
                .filterNot { it.consumer == HendelseConsumer.EF_VEDTAK_INFOTRYGD_V1 }
        )
    }
}
