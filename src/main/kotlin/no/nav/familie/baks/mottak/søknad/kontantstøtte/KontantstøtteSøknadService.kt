package no.nav.familie.baks.mottak.søknad.kontantstøtte

import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.FødselsnummerErNullException
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstotteVedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteVedleggRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.Søknaddokumentasjon
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteVedlegg
import no.nav.familie.baks.mottak.task.JournalførKontantstøtteSøknadTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class KontantstøtteSøknadService(
    private val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository,
    private val kontantstøtteVedleggRepository: KontantstøtteVedleggRepository,
    private val taskService: TaskService,
    private val vedleggClient: FamilieDokumentClient
) {
    @Transactional
    @Throws(FødselsnummerErNullException::class)
    fun mottaKontantstøtteSøknad(kontantstøtteSøknad: KontantstøtteSøknad): DBKontantstøtteSøknad {
        val dbKontantstøtteSøknad = kontantstøtteSøknad.tilDBKontantstøtteSøknad()
        val dokumentasjon = kontantstøtteSøknad.dokumentasjon

        lagreDBKontantstøtteSøknad(dbKontantstøtteSøknad)

        val properties = Properties().apply { this["søkersFødselsnummer"] = dbKontantstøtteSøknad.fnr }

        hentOgLagreSøknadvedlegg(dbKontantstøtteSøknad = dbKontantstøtteSøknad, søknaddokumentasjonsliste = dokumentasjon)

        taskService.save(
            Task(
                type = JournalførKontantstøtteSøknadTask.JOURNALFØR_KONTANTSTØTTE_SØKNAD,
                payload = dbKontantstøtteSøknad.id.toString(),
                properties = properties
            )
        )
        return dbKontantstøtteSøknad
    }

    fun lagreDBKontantstøtteSøknad(dbKontantstøtteSøknad: DBKontantstøtteSøknad): DBKontantstøtteSøknad {
        return kontantstøtteSøknadRepository.save(dbKontantstøtteSøknad)
    }

    fun hentDBKontantstøtteSøknad(søknadId: Long): DBKontantstøtteSøknad? {
        return kontantstøtteSøknadRepository.hentSøknad(søknadId)
    }

    fun hentLagredeDBKontantstøtteVedlegg(søknad: DBKontantstøtteSøknad): Map<String, DBKontantstotteVedlegg> {
        val vedleggMap = mutableMapOf<String, DBKontantstotteVedlegg>()
        kontantstøtteVedleggRepository.hentAlleVedlegg(søknad.id).forEach {
            vedleggMap.putIfAbsent(it.dokumentId, it)
        }
        return vedleggMap
    }

    fun slettLagredeDBKontantstøtteVedlegg(søknad: DBKontantstøtteSøknad) {
        kontantstøtteVedleggRepository.slettAlleVedlegg(søknad.id)
    }

    private fun hentOgLagreSøknadvedlegg(dbKontantstøtteSøknad: DBKontantstøtteSøknad, søknaddokumentasjonsliste: List<Søknaddokumentasjon>) {
        søknaddokumentasjonsliste.forEach { søknaddokumentasjon ->
            søknaddokumentasjon.opplastedeVedlegg.forEach { vedlegg ->
                logger.debug("Henter ${vedlegg.navn} for dokumentasjonsbehov ${vedlegg.tittel}")
                val vedleggDokument = vedleggClient.hentVedlegg(dokumentId = vedlegg.dokumentId)
                kontantstøtteVedleggRepository.save(vedlegg.tilDBKontantstøtteVedlegg(dbKontantstøtteSøknad, vedleggDokument))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KontantstøtteSøknadService::class.java)
    }
}
