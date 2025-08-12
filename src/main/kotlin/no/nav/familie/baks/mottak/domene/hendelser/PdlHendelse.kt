package no.nav.familie.baks.mottak.domene.hendelser

import java.time.LocalDate

data class PdlHendelse(
    val hendelseId: String,
    val gjeldendeAktørId: String,
    val offset: Long,
    val opplysningstype: String,
    val endringstype: String,
    val personIdenter: List<String>,
    val dødsdato: LocalDate? = null,
    val fødselsdato: LocalDate? = null,
    val fødeland: String? = null,
    val utflyttingsdato: LocalDate? = null,
    val tidligereHendelseId: String? = null,
    val sivilstand: String? = null,
    val sivilstandDato: LocalDate? = null,
    val bostedskommune: String? = null,
    val bostedskommuneFomDato: LocalDate? = null,
) {
    // TODO: Skal gjøres tydeligere og mer robust.
    fun hentPersonident() = personIdenter.first { it.length == 11 }

    fun hentPersonidenter() = personIdenter.filter { it.length == 11 }
}
