package no.nav.familie.ba.mottak.repository

import no.nav.familie.ba.mottak.repository.domain.Soknad
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SoknadRepository : JpaRepository<Soknad, String>