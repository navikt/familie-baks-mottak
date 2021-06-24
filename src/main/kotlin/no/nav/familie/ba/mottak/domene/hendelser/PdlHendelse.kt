package no.nav.familie.ba.mottak.domene.hendelser

import java.time.LocalDate

data class PdlHendelse(val hendelseId: String,
                       val offset: Long,
                       val opplysningstype: String,
                       val endringstype: String,
                       val personIdenter: List<String>,
                       val dødsdato: LocalDate? = null,
                       val fødselsdato: LocalDate? = null,
                       val fødeland: String? = null,
                       val utflyttingsdato: LocalDate? = null,
    ) {

        // TODO: Skal gjøres tydeligere og mer robust.
        fun hentAktørId() = personIdenter.first { it.length == 13 }

        // TODO: Ditto.
        fun hentPersonident() = personIdenter.first { it.length == 11 }
    }
