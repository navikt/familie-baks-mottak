package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface SøknadRepository : JpaRepository<DBSøknad, String> {
    @Query(value = "SELECT s FROM Soknad s WHERE s.id = :soknadId")
    fun hentDBSøknad(soknadId: Long): DBSøknad?
}

@Repository
interface SøknadVedleggRepository : JpaRepository<DBVedlegg, String> {
    @Query(value = "SELECT v FROM SoknadVedlegg v WHERE v.dokumentId = :dokumentId")
    fun hentVedlegg(dokumentId: String): DBVedlegg?

    @Query(value = "SELECT v FROM SoknadVedlegg v WHERE v.søknadId = :soknadId")
    fun hentAlleVedlegg(soknadId: Long): List<DBVedlegg>

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM SoknadVedlegg v WHERE v.søknadId = :soknadId")
    fun slettAlleVedlegg(soknadId: Long)
}
