package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface SøknadRepository : JpaRepository<DBBarnetrygdSøknad, String> {
    @Query(value = "SELECT s FROM Soknad s WHERE s.id = :soknadId")
    fun hentDBSøknad(soknadId: Long): DBBarnetrygdSøknad?

    @Query(
        "SELECT s FROM Soknad s ORDER BY s.opprettetTid DESC LIMIT 1",
    )
    fun finnSisteLagredeSøknad(): DBBarnetrygdSøknad

    fun getByJournalpostId(journalpostId: String): DBBarnetrygdSøknad
}

@Repository
interface SøknadVedleggRepository : JpaRepository<DBVedlegg, String> {
    @Query(value = "SELECT v FROM SoknadVedlegg v WHERE v.søknadId = :soknadId")
    fun hentAlleVedlegg(soknadId: Long): List<DBVedlegg>

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM SoknadVedlegg v WHERE v.søknadId = :soknadId")
    fun slettAlleVedlegg(soknadId: Long)
}
