package no.nav.familie.ba.mottak.domene

data class NyBehandling(val søkersIdent: String? = null,
                        val morsIdent: String? = null,
                        val barnasIdenter: Array<String>)