package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SoknadRepository : JpaRepository<DBSøknad, String> {

    @Query(value = "SELECT s FROM Soknad s WHERE s.id = :soknadId")
    fun hentSøknad(soknadId: Long): DBSøknad?
}