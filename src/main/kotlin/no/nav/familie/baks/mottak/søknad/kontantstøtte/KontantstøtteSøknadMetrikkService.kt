package no.nav.familie.baks.mottak.søknad.kontantstøtte

import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV3
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV4
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.VersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.ks.søknad.v1.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon
import org.springframework.stereotype.Service

@Service
class KontantstøtteSøknadMetrikkService {

    // Metrics for kontantstotte
    val søknadMottattOk = Metrics.counter("kontantstotte.soknad.mottatt.ok")
    val søknadMottattFeil = Metrics.counter("kontantstotte.soknad.mottatt.feil")
    val søknadHarDokumentasjonsbehov = Metrics.counter("kontantstotte.soknad.dokumentasjonsbehov")
    val antallDokumentasjonsbehov = Metrics.counter("kontantstotte.soknad.dokumentasjonsbehov.antall")
    val søknadHarVedlegg = Metrics.counter("kontantstotte.soknad.harVedlegg")
    val antallVedlegg = Metrics.counter("kontantstotte.soknad.harVedlegg.antall")
    val harManglerIDokumentasjonsbehov = Metrics.counter("kontantstotte.soknad.harManglerIDokumentasjonsbehov")

    // Metrics for EØS kontantstotte
    val søknadEøs = Metrics.counter("kontantstotte.soknad.eos")

    fun sendMottakMetrikker(versjonertKontantstøtteSøknad: VersjonertKontantstøtteSøknad) {
        val dokumentasjon = when (versjonertKontantstøtteSøknad) {
            is KontantstøtteSøknadV3 -> versjonertKontantstøtteSøknad.kontantstøtteSøknad.dokumentasjon
            is KontantstøtteSøknadV4 -> versjonertKontantstøtteSøknad.kontantstøtteSøknad.dokumentasjon
        }

        val harEøsSteg = when (versjonertKontantstøtteSøknad) {
            is KontantstøtteSøknadV3 -> versjonertKontantstøtteSøknad.kontantstøtteSøknad.antallEøsSteg > 0
            is KontantstøtteSøknadV4 -> versjonertKontantstøtteSøknad.kontantstøtteSøknad.antallEøsSteg > 0
        }

        sendSøknadMetrikker(harEøsSteg)

        sendDokumentasjonMetrikker(dokumentasjon)
    }

    fun sendMottakFeiletMetrikker() {
        søknadMottattFeil.increment()
    }

    private fun sendSøknadMetrikker(harEøsSteg: Boolean) {
        søknadMottattOk.increment()
        if (harEøsSteg) {
            søknadEøs.increment()
        }
    }

    private fun sendDokumentasjonMetrikker(dokumentasjon: List<Søknaddokumentasjon>) {
        if (dokumentasjon.isNotEmpty()) {
            val dokumentasjonsbehovUtenAnnenDokumentasjon =
                dokumentasjon.filter { it.dokumentasjonsbehov != Dokumentasjonsbehov.ANNEN_DOKUMENTASJON }

            if (dokumentasjonsbehovUtenAnnenDokumentasjon.isNotEmpty()) {
                sendDokumentasjonsbehovMetrikker(
                    dokumentasjonsbehov = dokumentasjonsbehovUtenAnnenDokumentasjon,
                )
            }
            val alleVedlegg = dokumentasjon.map { it.opplastedeVedlegg }.flatten()
            if (alleVedlegg.isNotEmpty()) {
                søknadHarVedlegg.increment()
                antallVedlegg.increment(alleVedlegg.size.toDouble())
            }

            val harMangler =
                dokumentasjonsbehovUtenAnnenDokumentasjon.any { !it.harSendtInn && it.opplastedeVedlegg.isEmpty() }
            if (harMangler) {
                harManglerIDokumentasjonsbehov.increment()
            }
        }
    }

    private fun sendDokumentasjonsbehovMetrikker(dokumentasjonsbehov: List<Søknaddokumentasjon>) {
        søknadHarDokumentasjonsbehov.increment()
        antallDokumentasjonsbehov.increment(dokumentasjonsbehov.size.toDouble())
    }
}
