package no.nav.familie.baks.mottak.integrasjoner

import org.springframework.stereotype.Service

@Service
class SøknadsidenterService(
    private val baksVersjonertSøknadClient: BaksVersjonertSøknadClient,
) {
    fun hentIdenterFraBarnetrygdSøknad(
        journalpostId: String,
    ): List<String> {
        val søknad = baksVersjonertSøknadClient.hentVersjonertBarnetrygdSøknad(journalpostId)
        return søknad.barnetrygdSøknad.personerISøknad()
    }

    fun hentIdenterFraKontantstøtteSøknad(
        journalpostId: String,
    ): List<String> {
        val søknad = baksVersjonertSøknadClient.hentVersjonertKontantstøtteSøknad(journalpostId)
        return søknad.kontantstøtteSøknad.personerISøknad()
    }
}
