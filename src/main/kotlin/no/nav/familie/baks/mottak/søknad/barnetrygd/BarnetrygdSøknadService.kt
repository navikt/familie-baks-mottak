package no.nav.familie.baks.mottak.søknad.barnetrygd

import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.FødselsnummerErNullException
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBVedlegg
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import no.nav.familie.kontrakter.ba.søknad.StøttetVersjonertBarnetrygdSøknad
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV10
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV8
import no.nav.familie.kontrakter.ba.søknad.VersjonertBarnetrygdSøknadV9
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Properties

@Service
class BarnetrygdSøknadService(
    private val søknadRepository: SøknadRepository,
    private val vedleggRepository: SøknadVedleggRepository,
    private val taskService: TaskService,
    private val vedleggClient: FamilieDokumentClient,
) {
    @Transactional
    @Throws(FødselsnummerErNullException::class)
    fun motta(versjonertBarnetrygdSøknad: StøttetVersjonertBarnetrygdSøknad): DBBarnetrygdSøknad {
        val (dbSøknad, dokumentasjon) =
            when (versjonertBarnetrygdSøknad) {
                is VersjonertBarnetrygdSøknadV8 -> {
                    Pair(versjonertBarnetrygdSøknad.barnetrygdSøknad.tilDBSøknad(), versjonertBarnetrygdSøknad.barnetrygdSøknad.dokumentasjon)
                }

                is VersjonertBarnetrygdSøknadV9 -> {
                    Pair(versjonertBarnetrygdSøknad.barnetrygdSøknad.tilDBSøknad(), versjonertBarnetrygdSøknad.barnetrygdSøknad.dokumentasjon)
                }

                is VersjonertBarnetrygdSøknadV10 -> {
                    Pair(versjonertBarnetrygdSøknad.barnetrygdSøknad.tilDBSøknad(), versjonertBarnetrygdSøknad.barnetrygdSøknad.dokumentasjon)
                }
            }

        lagreDBSøknad(dbSøknad)

        val properties = Properties().apply { this["søkersFødselsnummer"] = dbSøknad.fnr }

        // Vi må hente vedleggene nå mens vi har gyldig on-behalf-of-token for brukeren
        hentOgLagreSøknadvedlegg(dbBarnetrygdSøknad = dbSøknad, søknaddokumentasjonsliste = dokumentasjon)

        taskService.save(
            Task(
                type = JournalførSøknadTask.JOURNALFØR_SØKNAD,
                payload = dbSøknad.id.toString(),
                properties = properties,
            ),
        )
        return dbSøknad
    }

    fun lagreDBSøknad(dbBarnetrygdSøknad: DBBarnetrygdSøknad): DBBarnetrygdSøknad = søknadRepository.save(dbBarnetrygdSøknad)

    fun hentDBSøknad(søknadId: Long): DBBarnetrygdSøknad? = søknadRepository.hentDBSøknad(søknadId)

    fun finnDBSøknadFraJournalpost(journalpostId: String): DBBarnetrygdSøknad? = søknadRepository.finnDBSøknadForJournalpost(journalpostId = journalpostId)

    fun hentDBSøknadFraJournalpost(journalpostId: String): DBBarnetrygdSøknad =
        søknadRepository.finnDBSøknadForJournalpost(journalpostId = journalpostId)
            ?: throw IllegalStateException("Fant ikke søknad for journalpost $journalpostId")

    fun hentLagredeVedlegg(søknad: DBBarnetrygdSøknad): Map<String, DBVedlegg> {
        val map = mutableMapOf<String, DBVedlegg>()
        vedleggRepository.hentAlleVedlegg(søknad.id).forEach {
            map.putIfAbsent(it.dokumentId, it)
        }
        return map
    }

    fun slettLagredeVedlegg(søknad: DBBarnetrygdSøknad) {
        vedleggRepository.slettAlleVedlegg(søknad.id)
    }

    private fun hentOgLagreSøknadvedlegg(
        dbBarnetrygdSøknad: DBBarnetrygdSøknad,
        søknaddokumentasjonsliste: List<Søknaddokumentasjon>,
    ) {
        søknaddokumentasjonsliste.forEach { søknaddokumentasjon ->
            søknaddokumentasjon.opplastedeVedlegg.forEach { vedlegg ->
                val vedleggDokument = vedleggClient.hentVedlegg(dokumentId = vedlegg.dokumentId)
                vedleggRepository.save(vedlegg.tilDBVedlegg(dbBarnetrygdSøknad, vedleggDokument))
            }
        }
    }
}
