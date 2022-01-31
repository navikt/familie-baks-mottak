package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.DBVedlegg
import no.nav.familie.ba.mottak.søknad.domene.FødselsnummerErNullException
import no.nav.familie.ba.mottak.søknad.domene.SøknadV6
import no.nav.familie.ba.mottak.søknad.domene.SøknadV7
import no.nav.familie.ba.mottak.søknad.domene.VersjonertSøknad
import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import no.nav.familie.ba.mottak.søknad.domene.tilDBVedlegg
import no.nav.familie.ba.mottak.task.JournalførSøknadTask
import no.nav.familie.kontrakter.ba.søknad.v4.Søknaddokumentasjon
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Properties

@Service
class SøknadService(
    private val søknadRepository: SøknadRepository,
    private val vedleggRepository: SøknadVedleggRepository,
    private val taskRepository: TaskRepository,
    private val vedleggClient: FamilieDokumentClient
) {

    @Transactional
    @Throws(FødselsnummerErNullException::class)
    fun motta(versjonertSøknad: VersjonertSøknad): DBSøknad {

        val (dbSøknad, dokumentasjon) = when (versjonertSøknad) {
            is SøknadV6 -> {
                Pair(versjonertSøknad.søknad.tilDBSøknad(), versjonertSøknad.søknad.dokumentasjon)
            }
            is SøknadV7 -> {
                Pair(versjonertSøknad.søknad.tilDBSøknad(), versjonertSøknad.søknad.dokumentasjon)
            }
        }

        lagreDBSøknad(dbSøknad)

        val properties = Properties().apply { this["søkersFødselsnummer"] = dbSøknad.fnr }

        // Vi må hente vedleggene nå mens vi har gyldig on-behalf-of-token for brukeren
        hentOgLagreSøknadvedlegg(dbSøknad = dbSøknad, søknaddokumentasjonsliste = dokumentasjon)

        taskRepository.save(
            Task.nyTask(
                JournalførSøknadTask.JOURNALFØR_SØKNAD,
                dbSøknad.id.toString(),
                properties
            )
        )
        return dbSøknad
    }

    fun lagreDBSøknad(dbSøknad: DBSøknad): DBSøknad {
        return søknadRepository.save(dbSøknad)
    }

    fun hentDBSøknad(søknadId: Long): DBSøknad? {
        return søknadRepository.hentDBSøknad(søknadId)
    }

    fun hentLagredeVedlegg(søknad: DBSøknad): Map<String, DBVedlegg> {
        val map = mutableMapOf<String, DBVedlegg>()
        vedleggRepository.hentAlleVedlegg(søknad.id).forEach {
            map.putIfAbsent(it.dokumentId, it)
        }
        return map
    }

    fun slettLagredeVedlegg(søknad: DBSøknad) {
        vedleggRepository.slettAlleVedlegg(søknad.id)
    }

    private fun hentOgLagreSøknadvedlegg(dbSøknad: DBSøknad, søknaddokumentasjonsliste: List<Søknaddokumentasjon>) {
        søknaddokumentasjonsliste.forEach { søknaddokumentasjon ->
            søknaddokumentasjon.opplastedeVedlegg.forEach { vedlegg ->
                val vedleggDokument = vedleggClient.hentVedlegg(vedlegg)
                vedleggRepository.save(vedlegg.tilDBVedlegg(dbSøknad, vedleggDokument))
            }
        }
    }
}
