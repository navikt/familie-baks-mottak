package no.nav.familie.baks.mottak.domene

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository

@Repository
interface HendelsesloggRepository : JpaRepository<Hendelseslogg, Long> {
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    fun save(hendelseslogg: Hendelseslogg): Hendelseslogg

    fun existsByHendelseIdAndConsumer(
        hendelseId: String,
        consumer: HendelseConsumer,
    ): Boolean
}
