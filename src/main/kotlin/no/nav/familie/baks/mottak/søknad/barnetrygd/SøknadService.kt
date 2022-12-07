package no.nav.familie.baks.mottak.søknad.barnetrygd

import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.FødselsnummerErNullException
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV7
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBVedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Properties

@Service
class SøknadService(
    private val søknadRepository: SøknadRepository,
    private val vedleggRepository: SøknadVedleggRepository,
    private val taskService: TaskService,
    private val vedleggClient: FamilieDokumentClient
) {

    @Transactional
    @Throws(FødselsnummerErNullException::class)
    fun motta(versjonertSøknad: VersjonertSøknad): DBSøknad {
        val (dbSøknad, dokumentasjon) = when (versjonertSøknad) {
            is SøknadV7 -> {
                Pair(versjonertSøknad.søknad.tilDBSøknad(), versjonertSøknad.søknad.dokumentasjon)
            }
            is SøknadV8 -> {
                Pair(versjonertSøknad.søknad.tilDBSøknad(), versjonertSøknad.søknad.dokumentasjon)
            }
        }

        lagreDBSøknad(dbSøknad)

        val properties = Properties().apply { this["søkersFødselsnummer"] = dbSøknad.fnr }

        // Vi må hente vedleggene nå mens vi har gyldig on-behalf-of-token for brukeren
        hentOgLagreSøknadvedlegg(dbSøknad = dbSøknad, søknaddokumentasjonsliste = dokumentasjon)

        taskService.save(
            Task(
                type = JournalførSøknadTask.JOURNALFØR_SØKNAD,
                payload = dbSøknad.id.toString(),
                properties = properties
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
                logger.debug("Henter ${vedlegg.navn} for dokumentasjonsbehov ${vedlegg.tittel}")
                val vedleggDokument = vedleggClient.hentVedlegg(dokumentId = vedlegg.dokumentId)
                vedleggRepository.save(vedlegg.tilDBVedlegg(dbSøknad, vedleggDokument))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KontantstøtteSøknadService::class.java)
    }
}
