package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.*
import no.nav.familie.kontrakter.felles.Tema
import org.springframework.stereotype.Service

@Service
class AdressebeskyttelesesgraderingService(
    private val pdlClient: PdlClient,
    private val søknadsidenterService: SøknadsidenterService,
) {
    fun finnesAdressebeskyttelsegradringPåJournalpost(
        tema: Tema,
        journalpost: Journalpost,
    ): Boolean {
        if (journalpost.bruker == null) {
            throw IllegalStateException("Bruker på journalpost ${journalpost.journalpostId} kan ikke være null")
        }

        val (søkersIdent, barnasIdenter) =
            when (tema) {
                Tema.BAR -> finnIdenterForBarnetrygd(tema, journalpost.bruker, journalpost.journalpostId, journalpost.erDigitalSøknad())
                Tema.KON -> finnIdenterForKontantstøtte(tema, journalpost.bruker, journalpost.journalpostId, journalpost.erDigitalSøknad())
                Tema.ENF,
                Tema.OPP,
                -> {
                    throw IllegalStateException("Støtter ikke tema $tema")
                }
            }

        val alleIdenter = barnasIdenter + søkersIdent

        return alleIdenter
            .map { pdlClient.hentPerson(it, "hentperson-med-adressebeskyttelse", tema) }
            .flatMap { it.adressebeskyttelse }
            .any { it.gradering.erStrengtFortrolig() }
    }

    private fun finnIdenterForKontantstøtte(
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
        erDigitalSøknad: Boolean,
    ): Pair<String, List<String>> =
        if (erDigitalSøknad) {
            søknadsidenterService.hentIdenterForKontantstøtteViaJournalpost(journalpostId)
        } else {
            Pair(
                tilPersonIdent(
                    bruker,
                    tema,
                ),
                emptyList(),
            )
        }

    private fun finnIdenterForBarnetrygd(
        tema: Tema,
        bruker: Bruker,
        journalpostId: String,
        erDigitalSøknad: Boolean,
    ): Pair<String, List<String>> =
        if (erDigitalSøknad) {
            søknadsidenterService.hentIdenterForBarnetrygdViaJournalpost(journalpostId)
        } else {
            Pair(
                tilPersonIdent(
                    bruker,
                    tema,
                ),
                emptyList(),
            )
        }

    private fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema)
            else -> bruker.id
        }
}
