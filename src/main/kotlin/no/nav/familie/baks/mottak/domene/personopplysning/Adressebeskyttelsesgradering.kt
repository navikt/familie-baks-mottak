package no.nav.familie.baks.mottak.domene.personopplysning

enum class Adressebeskyttelsesgradering {
    STRENGT_FORTROLIG_UTLAND, // Kode 19
    FORTROLIG, // Kode 7
    STRENGT_FORTROLIG, // Kode 6
    UGRADERT,
    ;

    fun erStrengtFortrolig() = this == STRENGT_FORTROLIG

    fun erStrengtFortroligUtland() = this == STRENGT_FORTROLIG_UTLAND

    fun erFortrolig(): Boolean = this == FORTROLIG

    fun erUgradert(): Boolean = this == UGRADERT
}
