package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface KontantstøtteSøknadRepository : JpaRepository<DBKontantstøtteSøknad, String> {
    @Query(value = "SELECT s FROM kontantstotte_soknad s WHERE s.id = :soknadId")
    fun hentSøknad(soknadId: Long): DBKontantstøtteSøknad?

    @Query(value = "SELECT s FROM kontantstotte_soknad s WHERE s.journalpostId = :journalpostId")
    fun finnSøknadForJournalpost(journalpostId: String): DBKontantstøtteSøknad?

    @Query(
        "SELECT s FROM kontantstotte_soknad s ORDER BY s.opprettetTid DESC LIMIT 1",
    )
    fun finnSisteLagredeSøknad(): DBKontantstøtteSøknad

    fun getByJournalpostId(journalpostId: String): DBKontantstøtteSøknad
}

@Repository
interface KontantstøtteVedleggRepository : JpaRepository<DBKontantstotteVedlegg, String> {
    @Query(value = "SELECT v FROM kontantstotte_soknad_vedlegg v WHERE v.søknadId = :soknadId")
    fun hentAlleVedlegg(soknadId: Long): List<DBKontantstotteVedlegg>

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM kontantstotte_soknad_vedlegg v WHERE v.søknadId = :soknadId")
    fun slettAlleVedlegg(soknadId: Long)
}
