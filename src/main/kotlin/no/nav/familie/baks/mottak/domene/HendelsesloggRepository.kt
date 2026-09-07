package no.nav.familie.baks.mottak.domene

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface HendelsesloggRepository : JpaRepository<Hendelseslogg, Long> {
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    fun save(hendelseslogg: Hendelseslogg): Hendelseslogg

    fun existsByHendelseIdAndConsumer(
        hendelseId: String,
        consumer: HendelseConsumer,
    ): Boolean

    @Modifying
    @Query("DELETE FROM Hendelseslogg h WHERE h.opprettetTidspunkt < :grense")
    fun slettEldreEnn(grense: LocalDateTime): Int
}
