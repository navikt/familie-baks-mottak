package no.nav.familie.ba.mottak.søknad.domene

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.kontrakter.ba.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v2.Søknad
import no.nav.familie.kontrakter.felles.objectMapper
import java.time.LocalDateTime
import javax.persistence.*
import no.nav.familie.kontrakter.ba.søknad.v3.Søknad as SøknadV3

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

    fun hentSøknad(): Søknad {
        return objectMapper.readValue(søknadJson)
    }

    fun hentSøknadV3(): SøknadV3 {
        return objectMapper.readValue(søknadJson)
    }

    fun hentSøknadVersjon(): String {
        val map: HashMap<String, Any> = objectMapper.readValue(søknadJson)
        val søknadVersjon = when(map["søknadstype"]) {
            Søknadstype.UTVIDET.toString() -> "v3"
            else -> "v2"
        }
        return søknadVersjon;
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
            fnr = this.søker.ident.verdi
        )
    } catch (e: KotlinNullPointerException) {
        throw FødselsnummerErNullException()
    }

}

fun SøknadV3.tilDBSøknad(): DBSøknad {
    try {
        return DBSøknad(
            søknadJson = objectMapper.writeValueAsString(this),
            fnr = this.søker.ident.verdi
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

