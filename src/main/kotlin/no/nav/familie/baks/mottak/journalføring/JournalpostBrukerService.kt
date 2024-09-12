package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.Bruker
import no.nav.familie.baks.mottak.integrasjoner.BrukerIdType
import no.nav.familie.baks.mottak.integrasjoner.PdlClient
import no.nav.familie.kontrakter.felles.Tema
import org.springframework.stereotype.Service

@Service
class JournalpostBrukerService(
    private val pdlClient: PdlClient,
) {
    // TODO : Spør om bedre navn, er det en person ident om det er orgnr ?
    fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClient.hentPersonident(bruker.id, tema)
            BrukerIdType.FNR,
            BrukerIdType.ORGNR,
            -> bruker.id
        }
}
