package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.Vedlegg
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.ks.søknad.v1.KontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.v1.Søknadsvedlegg
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.SequenceGenerator
import javax.persistence.Table

@Entity(name = "kontantstotte_soknad")
@Table(name = "kontantstotte_soknad")
data class DBKontantstøtteSøknad(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "kontantstotte_soknad_seq_generator")
    @SequenceGenerator(name = "kontantstotte_soknad_seq_generator", sequenceName = "kontantstotte_soknad_seq", allocationSize = 50)
    val id: Long = 0,
    @Column(name = "soknad_json")
    val søknadJson: String,
    val fnr: String,
    @Column(name = "opprettet_tid")
    val opprettetTid: LocalDateTime = LocalDateTime.now(),
    @Column(name = "journalpost_id")
    val journalpostId: String? = null
) {

    fun hentSøknad(): KontantstøtteSøknad {
        return objectMapper.readValue(søknadJson)
    }
}

@Entity(name = "kontantstotte_soknad_vedlegg")
@Table(name = "kontantstotte_soknad_vedlegg")
data class DBKontantstotteVedlegg(
    @Id
    @Column(name = "dokument_id")
    override val dokumentId: String,
    @Column(name = "soknad_id")
    override val søknadId: Long,
    override val data: ByteArray
) : Vedlegg

fun KontantstøtteSøknad.tilDBKontantstøtteSøknad(): DBKontantstøtteSøknad {
    try {
        return DBKontantstøtteSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr = this.søker.ident.verdi.getValue("nb")
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun Søknadsvedlegg.tilDBKontantstøtteVedlegg(søknad: DBKontantstøtteSøknad, data: ByteArray): DBKontantstotteVedlegg {
    return DBKontantstotteVedlegg(
        dokumentId = this.dokumentId,
        søknadId = søknad.id,
        data = data
    )
}

class FødselsnummerErNullException : Exception()
