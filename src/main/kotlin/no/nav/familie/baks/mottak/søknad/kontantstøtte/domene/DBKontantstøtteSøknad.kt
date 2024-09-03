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
import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad as KontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.v5.KontantstøtteSøknad as KontantstøtteSøknadV5

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
    fun hentVersjonertKontantstøtteSøknad(): VersjonertKontantstøtteSøknad {
        val søknad = objectMapper.readTree(søknadJson)
        val versjon = søknad.get("kontraktVersjon")?.asInt()
        return when (versjon) {
            4 -> KontantstøtteSøknadV4(kontantstøtteSøknad = objectMapper.readValue<KontantstøtteSøknadV4>(søknadJson))
            5 -> KontantstøtteSøknadV5(kontantstøtteSøknad = objectMapper.readValue<KontantstøtteSøknadV5>(søknadJson))
            else -> error("Ikke støttet versjon $versjon av kontrakt for Kontantstøtte")
        }
    }
}

fun DBKontantstøtteSøknad.harEøsSteg(): Boolean {
    val versjonertSøknad = this.hentVersjonertKontantstøtteSøknad()

    return when (versjonertSøknad) {
        is no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4 -> versjonertSøknad.kontantstøtteSøknad.søker.harEøsSteg
        is no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV5 -> versjonertSøknad.kontantstøtteSøknad.søker.harEøsSteg
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

fun KontantstøtteSøknadV4.tilDBKontantstøtteSøknad(): DBKontantstøtteSøknad {
    try {
        return DBKontantstøtteSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr =
                this.søker.ident.verdi
                    .getValue("nb"),
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun KontantstøtteSøknadV5.tilDBKontantstøtteSøknad(): DBKontantstøtteSøknad {
    try {
        return DBKontantstøtteSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr =
                this.søker.ident.verdi
                    .getValue("nb"),
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }
}

fun Søknadsvedlegg.tilDBKontantstøtteVedlegg(
    søknad: DBKontantstøtteSøknad,
    data: ByteArray,
): DBKontantstotteVedlegg =
    DBKontantstotteVedlegg(
        dokumentId = this.dokumentId,
        søknadId = søknad.id,
        data = data,
    )

class FødselsnummerErNullException : Exception()
