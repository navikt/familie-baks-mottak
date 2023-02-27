package no.nav.familie.baks.mottak.domene.personopplysning

import java.time.LocalDate
import java.util.Collections

@Deprecated("Fjernes når barnetrygd er ute av infotrygd")
data class Personinfo(
    var personIdent: PersonIdent?,
    var navn: String?,
    var bostedsadresse: Adresseinfo?,
    var kjønn: String?,
    var fødselsdato: LocalDate?,
    var dødsdato: LocalDate?,
    var personstatus: PersonstatusType?,
    var sivilstand: SivilstandType?,
    var familierelasjoner: Set<Familierelasjon>? = Collections.emptySet(),
    var statsborgerskap: Landkode?,
    var utlandsadresse: String?,
    var geografiskTilknytning: String?,
    var diskresjonskode: String?,
    var adresseLandkode: String?,
)

data class Adresseinfo(
    var gjeldendePostadresseType: AdresseType?,
    var mottakerNavn: String?,
    var adresselinje1: String?,
    var adresselinje2: String?,
    var adresselinje3: String?,
    var adresselinje4: String?,
    var postNr: String?,
    var poststed: String?,
    var land: String?,
    var personstatus: PersonstatusType?,
)

enum class PersonstatusType {
    ABNR, ADNR, BOSA, DØD, DØDD, FOSV, FØDR, UFUL, UREG, UTAN, UTPE, UTVA
}

enum class SivilstandType {
    GIFT, UGIF, ENKE, SKIL, SKPA, SAMB, GJPA, GLAD, NULL, REPA, SEPA, SEPR
}

enum class AdresseType {
    BOSTEDSADRESSE, POSTADRESSE, POSTADRESSE_UTLAND, MIDLERTIDIG_POSTADRESSE_NORGE, MIDLERTIDIG_POSTADRESSE_UTLAND, UKJENT_ADRESSE
}

enum class RelasjonsRolleType {
    EKTE, BARN, FARA, MORA, REPA, SAMB, MMOR
}

data class Landkode(var kode: String?) {
    fun erNorge(): Boolean {
        return NORGE == this
    }

    companion object {
        val UDEFINERT = Landkode("UDEFINERT")
        val NORGE = Landkode("NOR")
    }
}
