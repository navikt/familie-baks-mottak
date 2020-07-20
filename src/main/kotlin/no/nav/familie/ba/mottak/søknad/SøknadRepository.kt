package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SøknadRepository : JpaRepository<DBSøknad, String> {

    @Query(value = "SELECT s FROM Soknad s WHERE s.id = :soknadId")
    fun hentDBSøknad(soknadId: Long): DBSøknad?

    fun get(id: String): DBSøknad
}