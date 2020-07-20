package no.nav.familie.ba.mottak.søknad


import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import org.springframework.stereotype.Service

@Service
class JournalføringService(private val integrasjonerClient: IntegrasjonerClient,
                           private val søknadService: SøknadService) {

    fun journalførSøknad(søknadId: String): String {
        val søknad: DBSøknad = søknadService.get(søknadId)
        val journalpostId: String = send(søknad)
        val søknadMedJournalpostId = søknad.copy(journalpostId = journalpostId)
        søknadService.lagreDBSøknad(søknadMedJournalpostId)
        return journalpostId
    }

    private fun send(søknad: DBSøknad): String {
        val arkiverDokumentRequest = ArkiverDokumentRequestMapper.toDto(søknad)
        val ressurs = integrasjonerClient.arkiver(arkiverDokumentRequest)
        return ressurs.journalpostId
    }
}