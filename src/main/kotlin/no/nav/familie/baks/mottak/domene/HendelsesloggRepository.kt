package no.nav.familie.baks.mottak.domene

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import javax.persistence.LockModeType

@Repository
interface HendelsesloggRepository : JpaRepository<Hendelseslogg, Long> {

    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    fun save(hendelseslogg: Hendelseslogg): Hendelseslogg

    fun existsByHendelseIdAndConsumer(hendelseId: String, consumer: HendelseConsumer): Boolean
}
