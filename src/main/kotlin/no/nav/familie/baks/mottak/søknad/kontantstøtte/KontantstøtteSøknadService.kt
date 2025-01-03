package no.nav.familie.baks.mottak.søknad.kontantstøtte

import no.nav.familie.baks.mottak.integrasjoner.FamilieDokumentClient
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstotteVedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.FødselsnummerErNullException
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteVedleggRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteVedlegg
import no.nav.familie.baks.mottak.task.JournalførKontantstøtteSøknadTask
import no.nav.familie.kontrakter.ks.søknad.StøttetVersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV4
import no.nav.familie.kontrakter.ks.søknad.VersjonertKontantstøtteSøknadV5
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Properties

@Service
class KontantstøtteSøknadService(
    private val kontantstøtteSøknadRepository: KontantstøtteSøknadRepository,
    private val kontantstøtteVedleggRepository: KontantstøtteVedleggRepository,
    private val taskService: TaskService,
    private val vedleggClient: FamilieDokumentClient,
) {
    @Transactional
    @Throws(FødselsnummerErNullException::class)
    fun mottaKontantstøtteSøknad(versjonertKontantstøtteSøknad: StøttetVersjonertKontantstøtteSøknad): DBKontantstøtteSøknad {
        val (dbKontantstøtteSøknad, dokumentasjon) =
            when (versjonertKontantstøtteSøknad) {
                is VersjonertKontantstøtteSøknadV4 -> {
                    Pair(
                        versjonertKontantstøtteSøknad.kontantstøtteSøknad.tilDBKontantstøtteSøknad(),
                        versjonertKontantstøtteSøknad.kontantstøtteSøknad.dokumentasjon,
                    )
                }

                is VersjonertKontantstøtteSøknadV5 -> {
                    Pair(
                        versjonertKontantstøtteSøknad.kontantstøtteSøknad.tilDBKontantstøtteSøknad(),
                        versjonertKontantstøtteSøknad.kontantstøtteSøknad.dokumentasjon,
                    )
                }
            }

        lagreDBKontantstøtteSøknad(dbKontantstøtteSøknad)

        val properties = Properties().apply { this["søkersFødselsnummer"] = dbKontantstøtteSøknad.fnr }

        hentOgLagreSøknadvedlegg(
            dbKontantstøtteSøknad = dbKontantstøtteSøknad,
            søknaddokumentasjonsliste = dokumentasjon,
        )

        taskService.save(
            Task(
                type = JournalførKontantstøtteSøknadTask.JOURNALFØR_KONTANTSTØTTE_SØKNAD,
                payload = dbKontantstøtteSøknad.id.toString(),
                properties = properties,
            ),
        )
        return dbKontantstøtteSøknad
    }

    fun lagreDBKontantstøtteSøknad(dbKontantstøtteSøknad: DBKontantstøtteSøknad): DBKontantstøtteSøknad = kontantstøtteSøknadRepository.save(dbKontantstøtteSøknad)

    fun hentDBKontantstøtteSøknad(søknadId: Long): DBKontantstøtteSøknad? = kontantstøtteSøknadRepository.hentSøknad(søknadId)

    fun finnDBKontantstøtteSøknadForJournalpost(journalpostId: String): DBKontantstøtteSøknad? = kontantstøtteSøknadRepository.finnSøknadForJournalpost(journalpostId = journalpostId)

    fun hentDBKontantstøtteSøknadForJournalpost(journalpostId: String): DBKontantstøtteSøknad =
        kontantstøtteSøknadRepository.finnSøknadForJournalpost(journalpostId = journalpostId)
            ?: throw IllegalStateException("Fant ikke søknad for journalpost $journalpostId")

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

    private fun hentOgLagreSøknadvedlegg(
        dbKontantstøtteSøknad: DBKontantstøtteSøknad,
        søknaddokumentasjonsliste: List<Søknaddokumentasjon>,
    ) {
        søknaddokumentasjonsliste.forEach { søknaddokumentasjon ->
            søknaddokumentasjon.opplastedeVedlegg.forEach { vedlegg ->
                val vedleggDokument = vedleggClient.hentVedlegg(dokumentId = vedlegg.dokumentId)
                kontantstøtteVedleggRepository.save(
                    vedlegg.tilDBKontantstøtteVedlegg(
                        dbKontantstøtteSøknad,
                        vedleggDokument,
                    ),
                )
            }
        }
    }
}
