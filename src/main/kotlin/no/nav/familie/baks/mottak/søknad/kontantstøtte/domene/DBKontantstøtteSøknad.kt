package no.nav.familie.baks.mottak.søknad.kontantstøtte.domene

import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.Vedlegg
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.ks.søknad.v1.Søknadsvedlegg
import java.time.LocalDateTime
import no.nav.familie.kontrakter.ks.søknad.v3.KontantstøtteSøknad as KontantstøtteSøknadV3
import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad as KontantstøtteSøknadV4

@Entity(name = "kontantstotte_soknad")
@Table(name = "kontantstotte_soknad")
data class DBKontantstøtteSøknad(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "kontantstotte_soknad_seq_generator")
    @SequenceGenerator(
        name = "kontantstotte_soknad_seq_generator",
        sequenceName = "kontantstotte_soknad_seq",
        allocationSize = 50,
    )
    val id: Long = 0,
    @Column(name = "soknad_json")
    val søknadJson: String,
    val fnr: String,
    @Column(name = "opprettet_tid")
    val opprettetTid: LocalDateTime = LocalDateTime.now(),
    @Column(name = "journalpost_id")
    val journalpostId: String? = null,
) {
    private fun hentSøknadV3(): KontantstøtteSøknadV3 {
        return objectMapper.readValue(søknadJson)
    }

    private fun hentSøknadV4(): KontantstøtteSøknadV4 {
        return objectMapper.readValue(søknadJson)
    }

    private fun hentSøknadVersjon(): String {
        val søknad = objectMapper.readTree(søknadJson)
        return if (søknad.get("kontraktVersjon")?.asInt() == 4) {
            "v4"
        } else {
            "v3"
        }
    }

    fun hentVersjonertKontantstøtteSøknad(): VersjonertKontantstøtteSøknad {
        val versjon = this.hentSøknadVersjon()
        if (versjon == "v4") {
            return KontantstøtteSøknadV4(kontantstøtteSøknad = hentSøknadV4())
        }
        return KontantstøtteSøknadV3(kontantstøtteSøknad = hentSøknadV3())
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
    override val data: ByteArray,
) : Vedlegg

fun KontantstøtteSøknadV3.tilDBKontantstøtteSøknad(): DBKontantstøtteSøknad {
    try {
        return DBKontantstøtteSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr = this.søker.ident.verdi.getValue("nb"),
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun KontantstøtteSøknadV4.tilDBKontantstøtteSøknad(): DBKontantstøtteSøknad {
    try {
        return DBKontantstøtteSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr = this.søker.ident.verdi.getValue("nb"),
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun Søknadsvedlegg.tilDBKontantstøtteVedlegg(
    søknad: DBKontantstøtteSøknad,
    data: ByteArray,
): DBKontantstotteVedlegg {
    return DBKontantstotteVedlegg(
        dokumentId = this.dokumentId,
        søknadId = søknad.id,
        data = data,
    )
}

class FødselsnummerErNullException : Exception()
