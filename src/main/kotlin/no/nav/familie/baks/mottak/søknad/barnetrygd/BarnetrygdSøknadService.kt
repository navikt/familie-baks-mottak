package no.nav.familie.baks.mottak.søknad.barnetrygd

import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.FødselsnummerErNullException
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBVedlegg
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
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
    fun motta(versjonertBarnetrygdSøknad: VersjonertBarnetrygdSøknad): DBBarnetrygdSøknad {
        val (dbSøknad, dokumentasjon) =
            when (versjonertBarnetrygdSøknad) {
                is SøknadV8 -> {
                    Pair(versjonertBarnetrygdSøknad.søknad.tilDBSøknad(), versjonertBarnetrygdSøknad.søknad.dokumentasjon)
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

    fun lagreDBSøknad(dbBarnetrygdSøknad: DBBarnetrygdSøknad): DBBarnetrygdSøknad {
        return søknadRepository.save(dbBarnetrygdSøknad)
    }

    fun hentDBSøknad(søknadId: Long): DBBarnetrygdSøknad? {
        return søknadRepository.hentDBSøknad(søknadId)
    }

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
