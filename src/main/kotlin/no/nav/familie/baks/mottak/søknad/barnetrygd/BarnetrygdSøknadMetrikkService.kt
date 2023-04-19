package no.nav.familie.baks.mottak.søknad.barnetrygd

import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV7
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadV8
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.VersjonertSøknad
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v7.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v7.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v7.Søknadsvedlegg
import org.springframework.stereotype.Service

@Service
class BarnetrygdSøknadMetrikkService {

    // Metrics for ordinær barnetrygd
    private val søknadMottattOk = Metrics.counter("barnetrygd.soknad.mottatt.ok")
    private val søknadMottattFeil = Metrics.counter("barnetrygd.soknad.mottatt.feil")
    private val søknadHarDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.dokumentasjonsbehov")
    private val antallDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.dokumentasjonsbehov.antall")
    private val søknadHarVedlegg = Metrics.counter("barnetrygd.soknad.harVedlegg")
    private val antallVedlegg = Metrics.counter("barnetrygd.soknad.harVedlegg.antall")
    private val harManglerIDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.harManglerIDokumentasjonsbehov")

    // Metrics for utvidet barnetrygd
    private val søknadUtvidetMottattOk = Metrics.counter("barnetrygd.soknad.utvidet.mottatt.ok")
    private val søknadUtvidetMottattFeil = Metrics.counter("barnetrygd.soknad.utvidet.mottatt.feil")
    private val utvidetSøknadHarDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.utvidet.dokumentasjonsbehov")
    private val utvidetAntallDokumentasjonsbehov =
        Metrics.counter("barnetrygd.soknad.utvidet.dokumentasjonsbehov.antall")
    private val utvidetSøknadHarVedlegg = Metrics.counter("barnetrygd.soknad.utvidet.harVedlegg")
    private val utvidetAntallVedlegg = Metrics.counter("barnetrygd.soknad.utvidet.harVedlegg.antall")
    private val utvidetHarManglerIDokumentasjonsbehov =
        Metrics.counter("barnetrygd.soknad.utvidet.harManglerIDokumentasjonsbehov")

    // Metrics for EØS barnetrygd
    private val ordinærSøknadEøs = Metrics.counter("barnetrygd.ordinaer.soknad.eos")
    private val utvidetSøknadEøs = Metrics.counter("barnetrygd.utvidet.soknad.eos")

    fun sendMottakMetrikker(versjonertSøknad: VersjonertSøknad) {
        val (søknadstype, dokumentasjon) = when (versjonertSøknad) {
            is SøknadV7 -> Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.dokumentasjon)
            is SøknadV8 -> Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.dokumentasjon)
        }

        val harEøsSteg = when (versjonertSøknad) {
            is SøknadV7 -> versjonertSøknad.søknad.antallEøsSteg > 0
            is SøknadV8 -> versjonertSøknad.søknad.antallEøsSteg > 0
        }

        val erUtvidet = søknadstype == Søknadstype.UTVIDET
        sendSøknadMetrikker(harEøsSteg, erUtvidet)

        sendDokumentasjonMetrikker(erUtvidet, dokumentasjon)
    }

    fun sendMottakFeiletMetrikker(versjonertSøknad: VersjonertSøknad) {
        val søknadstype = when (versjonertSøknad) {
            is SøknadV7 -> versjonertSøknad.søknad.søknadstype
            is SøknadV8 -> versjonertSøknad.søknad.søknadstype
        }
        if (søknadstype == Søknadstype.UTVIDET) søknadUtvidetMottattFeil.increment() else søknadMottattFeil.increment()
    }

    private fun sendSøknadMetrikker(harEøsSteg: Boolean, erUtvidet: Boolean) {
        if (erUtvidet) {
            søknadUtvidetMottattOk.increment()
            if (harEøsSteg) {
                utvidetSøknadEøs.increment()
            }
        } else {
            søknadMottattOk.increment()
            if (harEøsSteg) {
                ordinærSøknadEøs.increment()
            }
        }
    }

    private fun sendDokumentasjonMetrikker(erUtvidet: Boolean, dokumentasjon: List<Søknaddokumentasjon>) {
        if (dokumentasjon.isNotEmpty()) {
            // Filtrerer ut Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
            val dokumentasjonsbehovUtenAnnenDokumentasjon =
                dokumentasjon.filter { it.dokumentasjonsbehov != Dokumentasjonsbehov.ANNEN_DOKUMENTASJON }

            if (dokumentasjonsbehovUtenAnnenDokumentasjon.isNotEmpty()) {
                sendDokumentasjonsbehovMetrikker(
                    erUtvidet = erUtvidet,
                    dokumentasjonsbehov = dokumentasjonsbehovUtenAnnenDokumentasjon,
                )
            }
            // Inkluderer Dokumentasjonsbehov.ANNEN_DOKUMENTASJON for søknadHarVedlegg og antallVedlegg
            val alleVedlegg: List<Søknadsvedlegg> = dokumentasjon.map { it.opplastedeVedlegg }.flatten()
            if (alleVedlegg.isNotEmpty()) {
                sendVedleggMetrikker(erUtvidet = erUtvidet, vedlegg = alleVedlegg)
            }

            // Filtrerer ut Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
            val harMangler =
                dokumentasjonsbehovUtenAnnenDokumentasjon.any { !it.harSendtInn && it.opplastedeVedlegg.isEmpty() }
            if (harMangler) {
                sendManglerVedleggMetrikker(erUtvidet = erUtvidet)
            }
        }
    }

    private fun sendDokumentasjonsbehovMetrikker(erUtvidet: Boolean, dokumentasjonsbehov: List<Søknaddokumentasjon>) {
        if (erUtvidet) {
            utvidetSøknadHarDokumentasjonsbehov.increment()
            utvidetAntallDokumentasjonsbehov.increment(dokumentasjonsbehov.size.toDouble())
        } else {
            søknadHarDokumentasjonsbehov.increment()
            antallDokumentasjonsbehov.increment(dokumentasjonsbehov.size.toDouble())
        }
    }

    private fun sendVedleggMetrikker(erUtvidet: Boolean, vedlegg: List<Søknadsvedlegg>) {
        if (erUtvidet) {
            utvidetSøknadHarVedlegg.increment()
            utvidetAntallVedlegg.increment(vedlegg.size.toDouble())
        } else {
            søknadHarVedlegg.increment()
            antallVedlegg.increment(vedlegg.size.toDouble())
        }
    }

    private fun sendManglerVedleggMetrikker(erUtvidet: Boolean) {
        if (erUtvidet) utvidetHarManglerIDokumentasjonsbehov.increment() else harManglerIDokumentasjonsbehov.increment()
    }
}
