package no.nav.familie.ba.mottak.søknad

import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.config.FeatureToggleConfig
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.søknad.domene.FødselsnummerErNullException
import no.nav.familie.ba.mottak.søknad.domene.SøknadV6
import no.nav.familie.ba.mottak.søknad.domene.SøknadV7
import no.nav.familie.ba.mottak.søknad.domene.SøknadV8
import no.nav.familie.ba.mottak.søknad.domene.SøknadWipV8
import no.nav.familie.ba.mottak.søknad.domene.VersjonertSøknad
import no.nav.familie.kontrakter.ba.søknad.v4.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v4.Søknaddokumentasjon
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsvedlegg
import no.nav.familie.kontrakter.ba.søknad.v6.Søknad as SøknadKontraktV6
import no.nav.familie.kontrakter.ba.søknad.v7.Søknad as SøknadKontraktV7
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
class SøknadController(
    private val søknadService: SøknadService,
    private val featureToggleService: FeatureToggleService
) {

    // Metrics for ordinær barnetrygd
    val søknadMottattOk = Metrics.counter("barnetrygd.soknad.mottatt.ok")
    val søknadMottattFeil = Metrics.counter("barnetrygd.soknad.mottatt.feil")
    val søknadHarDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.dokumentasjonsbehov")
    val antallDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.dokumentasjonsbehov.antall")
    val søknadHarVedlegg = Metrics.counter("barnetrygd.soknad.harVedlegg")
    val antallVedlegg = Metrics.counter("barnetrygd.soknad.harVedlegg.antall")
    val harManglerIDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.harManglerIDokumentasjonsbehov")

    // Metrics for utvidet barnetrygd
    val søknadUtvidetMottattOk = Metrics.counter("barnetrygd.soknad.utvidet.mottatt.ok")
    val søknadUtvidetMottattFeil = Metrics.counter("barnetrygd.soknad.utvidet.mottatt.feil")
    val utvidetSøknadHarDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.utvidet.dokumentasjonsbehov")
    val utvidetAntallDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.utvidet.dokumentasjonsbehov.antall")
    val utvidetSøknadHarVedlegg = Metrics.counter("barnetrygd.soknad.utvidet.harVedlegg")
    val utvidetAntallVedlegg = Metrics.counter("barnetrygd.soknad.utvidet.harVedlegg.antall")
    val utvidetHarManglerIDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.utvidet.harManglerIDokumentasjonsbehov")

    // Metrics for EØS barnetrygd
    val søknadMedEøs = Metrics.counter("barnetrygd.soknad.eos.ok")
    val søknadMedEøsHarVedlegg = Metrics.counter("barnetrygd.soknad.eos.harvedlegg")
    val ordinærSøknadEøs = Metrics.counter("barnetrygd.ordinaer.soknad.eos")
    val utvidetSøknadEøs = Metrics.counter("barnetrygd.utvidet.soknad.eos")

    @PostMapping(value = ["/soknad/v6"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: SøknadKontraktV6): ResponseEntity<Ressurs<Kvittering>> =
        mottaVersjonertSøknadOgSendMetrikker(versjonertSøknad = SøknadV6(søknad = søknad))

    @PostMapping(value = ["/soknad/v7"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: SøknadKontraktV7): ResponseEntity<Ressurs<Kvittering>> =
        if (!featureToggleService.isEnabled(FeatureToggleConfig.TOGGLE_EØS_FULL)) {
            ResponseEntity.status(500)
                .body(Ressurs.failure("Endepunkt ikke tilgjengelig. Feature er skrudd av."))
        } else {
            mottaVersjonertSøknadOgSendMetrikker(versjonertSøknad = SøknadV7(søknad = søknad))
        }

    @PostMapping(value = ["/soknad/v8"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: SøknadWipV8): ResponseEntity<Ressurs<Kvittering>> =
            mottaVersjonertSøknadOgSendMetrikker(versjonertSøknad = SøknadV8(søknad = søknad))


    fun mottaVersjonertSøknadOgSendMetrikker(versjonertSøknad: VersjonertSøknad): ResponseEntity<Ressurs<Kvittering>> {

        val søknadstype = when (versjonertSøknad) {
            is SøknadV6 -> versjonertSøknad.søknad.søknadstype
            is SøknadV7 -> versjonertSøknad.søknad.søknadstype
            is SøknadV8 -> versjonertSøknad.søknad.søknadstype
        }

        return try {
            val dbSøknad = søknadService.motta(versjonertSøknad = versjonertSøknad)
            sendMetrics(versjonertSøknad = versjonertSøknad)
            ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er mottatt", dbSøknad.opprettetTid)))
        } catch (e: FødselsnummerErNullException) {
            if (søknadstype == Søknadstype.UTVIDET) søknadUtvidetMottattFeil.increment() else søknadMottattFeil.increment()
            ResponseEntity.status(500).body(Ressurs.failure("Lagring av søknad feilet"))
        }
    }

    private fun sendMetrics(versjonertSøknad: VersjonertSøknad) {
        val (søknadstype, dokumentasjon) = when (versjonertSøknad) {
            is SøknadV6 -> Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.dokumentasjon)
            is SøknadV7 -> Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.dokumentasjon)
            is SøknadV8 -> Pair(versjonertSøknad.søknad.søknadstype, versjonertSøknad.søknad.dokumentasjon)
        }

        val (harEøsSteg, kontraktVersjon) = when (versjonertSøknad) {
            is SøknadV6 -> Pair(false, 6)
            is SøknadV7 -> Pair(versjonertSøknad.søknad.antallEøsSteg > 0, versjonertSøknad.søknad.kontraktVersjon)
            is SøknadV8 -> Pair(versjonertSøknad.søknad.antallEøsSteg > 0, versjonertSøknad.søknad.kontraktVersjon)
        }

        val erUtvidet = søknadstype == Søknadstype.UTVIDET
        sendMetricsSøknad(harEøsSteg, erUtvidet)

        /* Kontraktversjonsnummer mindre enn 6 har ingen eøs-steg og bruker krevd
        dokumentasjon som grunnlag for å avgjøre om det er en eøs-søknad */
        if (kontraktVersjon < 7) {
            sendMetricsEøs(dokumentasjon)
        }

        sendMetricsDokumentasjon(erUtvidet, dokumentasjon)
    }

    private fun sendMetricsSøknad(harEøsSteg: Boolean, erUtvidet: Boolean) {
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

    private fun sendMetricsDokumentasjon(erUtvidet: Boolean, dokumentasjon: List<Søknaddokumentasjon>) {
        if (dokumentasjon.isNotEmpty()) {

            // Filtrerer ut Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
            val dokumentasjonsbehovUtenAnnenDokumentasjon =
                dokumentasjon.filter { it.dokumentasjonsbehov != Dokumentasjonsbehov.ANNEN_DOKUMENTASJON }

            if (dokumentasjonsbehovUtenAnnenDokumentasjon.isNotEmpty()) {
                sendMetricsDokumentasjonsbehov(
                    erUtvidet = erUtvidet,
                    dokumentasjonsbehov = dokumentasjonsbehovUtenAnnenDokumentasjon
                )
            }
            // Inkluderer Dokumentasjonsbehov.ANNEN_DOKUMENTASJON for søknadHarVedlegg og antallVedlegg
            val alleVedlegg: List<Søknadsvedlegg> = dokumentasjon.map { it.opplastedeVedlegg }.flatten()
            if (alleVedlegg.isNotEmpty()) {
                sendMetricsVedlegg(erUtvidet = erUtvidet, vedlegg = alleVedlegg)
            }

            // Filtrerer ut Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
            val harMangler =
                dokumentasjonsbehovUtenAnnenDokumentasjon.any { !it.harSendtInn && it.opplastedeVedlegg.isEmpty() }
            if (harMangler) {
                sendMetricsManglerVedlegg(erUtvidet = erUtvidet)
            }
        }
    }

    private fun sendMetricsDokumentasjonsbehov(erUtvidet: Boolean, dokumentasjonsbehov: List<Søknaddokumentasjon>) {
        if (erUtvidet) {
            utvidetSøknadHarDokumentasjonsbehov.increment()
            utvidetAntallDokumentasjonsbehov.increment(dokumentasjonsbehov.size.toDouble())
        } else {
            søknadHarDokumentasjonsbehov.increment()
            antallDokumentasjonsbehov.increment(dokumentasjonsbehov.size.toDouble())
        }
    }

    private fun sendMetricsVedlegg(erUtvidet: Boolean, vedlegg: List<Søknadsvedlegg>) {
        if (erUtvidet) {
            utvidetSøknadHarVedlegg.increment()
            utvidetAntallVedlegg.increment(vedlegg.size.toDouble())
        } else {
            søknadHarVedlegg.increment()
            antallVedlegg.increment(vedlegg.size.toDouble())
        }
    }

    private fun sendMetricsManglerVedlegg(erUtvidet: Boolean) {
        if (erUtvidet) utvidetHarManglerIDokumentasjonsbehov.increment() else harManglerIDokumentasjonsbehov.increment()
    }

    private fun sendMetricsEøs(dokumentasjon: List<Søknaddokumentasjon>) {
        val eøsDokumentasjon = dokumentasjon.find { it.dokumentasjonsbehov == Dokumentasjonsbehov.EØS_SKJEMA }
        if (eøsDokumentasjon != null) {
            søknadMedEøs.increment()
            if (eøsDokumentasjon.opplastedeVedlegg.isNotEmpty()) {
                søknadMedEøsHarVedlegg.increment()
            }
        }
    }

    @GetMapping(value = ["/ping"])
    @Unprotected
    fun ping(): ResponseEntity<String> {
        return ResponseEntity.ok().body("OK")
    }
}
