package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SoknadRepository : JpaRepository<DBSøknad, String>