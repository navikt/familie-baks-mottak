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
import no.nav.familie.kontrakter.ba.søknad.v8.Søknad
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.objectMapper
import java.time.LocalDateTime

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
    private fun hentSøknadV8(): Søknad = objectMapper.readValue(søknadJson)

    private fun hentSøknadV9(): BarnetrygdSøknad = objectMapper.readValue(søknadJson)

    private fun hentSøknadVersjon(): String {
        val søknad = objectMapper.readTree(søknadJson)
        val kontraktversjon = søknad.get("kontraktVersjon")?.asInt()
        return when (kontraktversjon) {
            8 -> "v8"
            9 -> "v9"
            else -> "v$kontraktversjon"
        }
    }

    fun hentVersjonertSøknad(): VersjonertBarnetrygdSøknad {
        val versjon = this.hentSøknadVersjon()
        return when (versjon) {
            "v8" -> BarnetrygdSøknadV8(barnetrygdSøknad = hentSøknadV8())
            "v9" -> BarnetrygdSøknadV9(barnetrygdSøknad = hentSøknadV9())
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

fun Søknad.tilDBSøknad(): DBBarnetrygdSøknad {
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

fun BarnetrygdSøknad.tilDBSøknad(): DBBarnetrygdSøknad {
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
        is no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV8 -> versjonertSøknad.barnetrygdSøknad.søker.harEøsSteg
        is no.nav.familie.baks.mottak.søknad.barnetrygd.domene.BarnetrygdSøknadV9 -> versjonertSøknad.barnetrygdSøknad.søker.harEøsSteg
    }
}

class FødselsnummerErNullException : Exception()
