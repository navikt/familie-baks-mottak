package no.nav.familie.baks.mottak.domene

import jakarta.persistence.EntityManager
import no.nav.familie.baks.mottak.DevLauncherPostgres
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@ActiveProfiles("postgres", "testcontainers")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class HendelsesloggRepositoryTest(
    @Autowired private val hendelsesloggRepository: HendelsesloggRepository,
    @Autowired private val entityManager: EntityManager,
) {
    @Test
    @Transactional
    fun `skal slette alle hendelser eldre enn grensen`() {
        val grense = LocalDateTime.of(2026, 7, 7, 9, 0)
        val gammelPdlHendelse = hendelsesloggRepository.save(hendelseslogg("gammel-pdl", grense.minusSeconds(1)))
        val gammelInfotrygdHendelse =
            hendelsesloggRepository.save(
                hendelseslogg("gammel-infotrygd", grense.minusDays(1), HendelseConsumer.EF_VEDTAK_INFOTRYGD_V1),
            )
        val nyHendelse = hendelsesloggRepository.save(hendelseslogg("ny", grense))
        entityManager.flush()

        val antallSlettet = hendelsesloggRepository.slettEldreEnn(grense)
        entityManager.clear()

        assertThat(antallSlettet).isEqualTo(2)
        assertThat(hendelsesloggRepository.existsById(gammelPdlHendelse.id!!)).isFalse()
        assertThat(hendelsesloggRepository.existsById(gammelInfotrygdHendelse.id!!)).isFalse()
        assertThat(hendelsesloggRepository.existsById(nyHendelse.id!!)).isTrue()
    }

    private fun hendelseslogg(
        hendelseId: String,
        opprettetTidspunkt: LocalDateTime,
        consumer: HendelseConsumer = HendelseConsumer.PDL,
    ) = Hendelseslogg(
        offset = 1,
        hendelseId = hendelseId,
        consumer = consumer,
        opprettetTidspunkt = opprettetTidspunkt,
    )
}
