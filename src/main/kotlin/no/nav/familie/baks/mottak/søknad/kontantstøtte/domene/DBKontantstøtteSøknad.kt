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
import no.nav.familie.kontrakter.ks.søknad.StøttetVersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV6
import no.nav.familie.kontrakter.ks.søknad.v1.Søknadsvedlegg
import java.time.LocalDateTime
import no.nav.familie.kontrakter.ks.søknad.v4.KontantstøtteSøknad as KontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.v5.KontantstøtteSøknad as KontantstøtteSøknadV5
import no.nav.familie.kontrakter.ks.søknad.v6.KontantstøtteSøknad as KontantstøtteSøknadV6

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
    fun hentVersjonertKontantstøtteSøknad(): StøttetVersjonertKontantstøtteSøknad = objectMapper.readValue<StøttetVersjonertKontantstøtteSøknad>(søknadJson)
}

fun DBKontantstøtteSøknad.harEøsSteg(): Boolean {
    val versjonertSøknad = this.hentVersjonertKontantstøtteSøknad()

    return when (versjonertSøknad) {
        is VersjonertKontantstøtteSøknadV4 -> versjonertSøknad.kontantstøtteSøknad.søker.harEøsSteg
        is VersjonertKontantstøtteSøknadV5 -> versjonertSøknad.kontantstøtteSøknad.søker.harEøsSteg
        is VersjonertKontantstøtteSøknadV6 -> versjonertSøknad.kontantstøtteSøknad.søker.harEøsSteg
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

fun KontantstøtteSøknadV6.tilDBKontantstøtteSøknad(): DBKontantstøtteSøknad {
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
