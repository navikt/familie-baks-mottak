package no.nav.familie.baks.mottak.søknad.barnetrygd.domene

import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import no.nav.familie.kontrakter.ba.søknad.v7.Søknadsvedlegg
import no.nav.familie.kontrakter.felles.objectMapper
import java.time.LocalDateTime
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad as SøknadV8

@Entity(name = "Soknad")
@Table(name = "Soknad")
data class DBBarnetrygdSøknad(
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
    val journalpostId: String? = null,
) {
    private fun hentSøknadV8(): SøknadV8 = objectMapper.readValue(søknadJson)

    private fun hentSøknadVersjon(): String =
        try {
            val søknad = objectMapper.readTree(søknadJson)
            if (søknad.get("kontraktVersjon")?.asInt() == 8) {
                "v8"
            } else {
                "v7"
            }
        } catch (e: Error) {
            "v7"
        }

    fun hentVersjonertSøknad(): VersjonertBarnetrygdSøknad {
        val versjon = this.hentSøknadVersjon()
        return when (versjon) {
            "v8" -> SøknadV8(søknad = hentSøknadV8())
            else -> error("Ikke støttet versjon $versjon av kontrakt for Barnetrygd")
        }
    }
}

@Entity(name = "SoknadVedlegg")
@Table(name = "SoknadVedlegg")
data class DBVedlegg(
    @Id
    @Column(name = "dokument_id")
    override val dokumentId: String,
    @Column(name = "soknad_id")
    override val søknadId: Long,
    override val data: ByteArray,
) : Vedlegg

interface Vedlegg {
    val dokumentId: String
    val søknadId: Long
    val data: ByteArray
}

fun SøknadV8.tilDBSøknad(): DBBarnetrygdSøknad {
    try {
        return DBBarnetrygdSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr =
                this.søker.ident.verdi
                    .getValue("nb"),
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun Søknadsvedlegg.tilDBVedlegg(
    søknad: DBBarnetrygdSøknad,
    data: ByteArray,
): DBVedlegg =
    DBVedlegg(
        dokumentId = this.dokumentId,
        søknadId = søknad.id,
        data = data,
    )

fun DBBarnetrygdSøknad.harEøsSteg(): Boolean {
    val versjonertSøknad = this.hentVersjonertSøknad()

    return when (versjonertSøknad) {
        is no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8 -> versjonertSøknad.søknad.søker.harEøsSteg
    }
}

class FødselsnummerErNullException : Exception()
