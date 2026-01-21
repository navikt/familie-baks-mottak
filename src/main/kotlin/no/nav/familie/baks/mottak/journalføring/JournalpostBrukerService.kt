package no.nav.familie.baks.mottak.journalføring

import no.nav.familie.baks.mottak.integrasjoner.PdlClientService
import no.nav.familie.kontrakter.felles.BrukerIdType
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.journalpost.Bruker
import org.springframework.stereotype.Service

@Service
class JournalpostBrukerService(
    private val pdlClientService: PdlClientService,
) {
    fun tilPersonIdent(
        bruker: Bruker,
        tema: Tema,
    ): String =
        when (bruker.type) {
            BrukerIdType.AKTOERID -> pdlClientService.hentPersonident(bruker.id, tema)

            BrukerIdType.FNR,
            BrukerIdType.ORGNR,
            -> bruker.id
        }
}
