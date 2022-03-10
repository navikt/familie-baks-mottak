package no.nav.familie.ba.mottak.søknad.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v6.Søknad
import no.nav.familie.kontrakter.felles.objectMapper
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.SequenceGenerator
import javax.persistence.Table

@Entity(name = "Soknad")
@Table(name = "Soknad")
data class DBSøknad(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "soknad_seq_generator")
    @SequenceGenerator(name = "soknad_seq_generator", sequenceName = "soknad_seq", allocationSize = 50)
    val id: Long = 0,
    @Column(name = "soknad_json")
    val søknadJson: String,
    val fnr: String,
    @Column(name = "opprettet_tid")
    val opprettetTid: LocalDateTime = LocalDateTime.now(),
    @Column(name = "journalpost_id")
    val journalpostId: String? = null
) {

    private fun hentSøknad(): Søknad {
        return objectMapper.readValue(søknadJson)
    }
    private fun hentSøknadV7(): SøknadNewWip {
        return objectMapper.readValue(søknadJson)
    }

    private fun hentSøknadVersjon(): String {
        return try {
            val søknad = objectMapper.readTree(søknadJson)
            if (søknad.get("kontraktVersjon")?.asInt() == 7) "v7" else "v6"
        } catch (e: Error) {
            "v6"
        }
    }

    fun hentVersjonertSøknad(): VersjonertSøknad {
        val versjon = this.hentSøknadVersjon()
        if (versjon == "v7") {
            return SøknadV7(søknad = hentSøknadV7())
        }
        return SøknadV6(søknad = hentSøknad())
    }
}

@Entity(name = "SoknadVedlegg")
@Table(name = "SoknadVedlegg")
data class DBVedlegg(
    @Id
    @Column(name = "dokument_id")
    val dokumentId: String,
    @Column(name = "soknad_id")
    val søknadId: Long,
    val data: ByteArray
)

fun Søknad.tilDBSøknad(): DBSøknad {
    try {
        return DBSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr = this.søker.ident.verdi.getValue("nb")
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun SøknadNewWip.tilDBSøknad(): DBSøknad {
    try {
        return DBSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr = this.søker.ident.verdi.getValue("nb")
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun Søknadsvedlegg.tilDBVedlegg(søknad: DBSøknad, data: ByteArray): DBVedlegg {
    return DBVedlegg(
        dokumentId = this.dokumentId,
        søknadId = søknad.id,
        data = data
    )
}

class FødselsnummerErNullException : Exception()
