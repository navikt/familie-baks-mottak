package no.nav.familie.ba.mottak.søknad.domene

import com.fasterxml.jackson.module.kotlin.readValue
import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.familie.kontrakter.felles.objectMapper
import java.time.LocalDateTime
import javax.persistence.*

@Entity(name = "Soknad")
@Table(name = "soknad")

data class DBSøknad(@Id
                    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "soknad_seq_generator")
                    @SequenceGenerator(name = "soknad_seq_generator", sequenceName = "soknad_seq", allocationSize = 50)
                    val id: Long = 0,
                    @Column(name = "soknad_json")
                    val søknadJson: String,
                    val fnr: String,
                    @Column(name = "opprettet_tid")
                    val opprettetTid: LocalDateTime = LocalDateTime.now()) {

    fun hentSøknad(): Søknad {
        return objectMapper.readValue(søknadJson)
    }
}

fun Søknad.tilDBSøknad(): DBSøknad {
    return DBSøknad(søknadJson = objectMapper.writeValueAsString(this),
            fnr = this.søker.fødselsnummer!!.verdi
    )
}


