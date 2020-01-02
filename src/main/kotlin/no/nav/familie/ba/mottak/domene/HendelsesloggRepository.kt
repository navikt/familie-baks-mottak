package no.nav.familie.ba.mottak.domene

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.util.*
import javax.persistence.LockModeType

@Repository
interface HendelsesloggRepository : JpaRepository<Hendelseslogg, Long> {


    override fun findById(id: Long): Optional<Hendelseslogg>

    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    fun save(hendelseslogg: Hendelseslogg): Hendelseslogg


    fun existsByHendelseId(hendelseId: String): Boolean

}