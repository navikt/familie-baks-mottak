package no.nav.familie.ba.mottak.søknad

import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.søknad.domene.FødselsnummerErNullException
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadstype
import no.nav.familie.kontrakter.ba.søknad.v4.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ba.søknad.v4.Søknad
import no.nav.familie.kontrakter.ba.søknad.v4.Søknadsvedlegg
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping(path = ["/api"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
class SøknadController(
    private val featureToggleService: FeatureToggleService,
    private val søknadService: SøknadService
) {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

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

    @PostMapping(value = ["/soknad/v4"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: Søknad): ResponseEntity<Ressurs<Kvittering>> {
        val lagreSøknad = featureToggleService.isEnabled("familie-ba-mottak.lagre-soknad")
        log.info("Lagring av søknad = $lagreSøknad")

        return if (lagreSøknad) {
            try {
                val dbSøknad = søknadService.motta(søknad)
                sendMetrics(søknad)
                ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er mottatt", dbSøknad.opprettetTid)))
            } catch (e: FødselsnummerErNullException) {
                if (søknad.søknadstype == Søknadstype.UTVIDET) {
                    søknadUtvidetMottattFeil.increment()
                } else {
                    søknadMottattFeil.increment()
                }

                ResponseEntity.status(500).body(Ressurs.failure("Lagring av søknad feilet"))
            }
        } else {
            ResponseEntity.ok(
                Ressurs.success(
                    Kvittering(
                        "Søknad er mottatt. Lagring er deaktivert.",
                        LocalDateTime.now()
                    )
                )
            )
        }
    }

    private fun sendMetrics(søknad: Søknad) {
        sendMetricsEøs(søknad)

        val erUtvidet = søknad.søknadstype == Søknadstype.UTVIDET
        if (erUtvidet) søknadUtvidetMottattOk.increment()
            else søknadMottattOk.increment()
        if (søknad.dokumentasjon.isNotEmpty()) {
            // Filtrerer ut Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
            val dokumentasjonsbehovUtenAnnenDokumentasjon =
                søknad.dokumentasjon.filter { it.dokumentasjonsbehov != Dokumentasjonsbehov.ANNEN_DOKUMENTASJON }
            if (dokumentasjonsbehovUtenAnnenDokumentasjon.isNotEmpty()) {
                if (erUtvidet) {
                    utvidetSøknadHarDokumentasjonsbehov.increment()
                    utvidetAntallDokumentasjonsbehov.increment(dokumentasjonsbehovUtenAnnenDokumentasjon.size.toDouble())
                } else {
                    søknadHarDokumentasjonsbehov.increment()
                    antallDokumentasjonsbehov.increment(dokumentasjonsbehovUtenAnnenDokumentasjon.size.toDouble())
                }
            }
            sendMetricsAntallVedlegg(søknad)

            // Filtrerer ut Dokumentasjonsbehov.ANNEN_DOKUMENTASJON
            val harMangler =
                dokumentasjonsbehovUtenAnnenDokumentasjon.filter { !it.harSendtInn && it.opplastedeVedlegg.isEmpty() }
                    .isNotEmpty()
            if (harMangler) {
                if (erUtvidet) utvidetHarManglerIDokumentasjonsbehov.increment() else harManglerIDokumentasjonsbehov.increment()
            }
        }
    }

    private fun sendMetricsAntallVedlegg(søknad: Søknad) {
        val erUtvidet = søknad.søknadstype == Søknadstype.UTVIDET
        // Inkluderer Dokumentasjonsbehov.ANNEN_DOKUMENTASJON for søknadHarVedlegg og antallVedlegg
        val alleVedlegg: List<Søknadsvedlegg> = søknad.dokumentasjon.map { it.opplastedeVedlegg }.flatten()
        if (alleVedlegg.isNotEmpty()) {
            if (erUtvidet) {
                utvidetSøknadHarVedlegg.increment()
                utvidetAntallVedlegg.increment(alleVedlegg.size.toDouble())
            } else {
                søknadHarVedlegg.increment()
                antallVedlegg.increment(alleVedlegg.size.toDouble())
            }
        }
    }

    private fun sendMetricsEøs(søknad: Søknad) {
        val eøsDokumentasjon = søknad.dokumentasjon.find { it.dokumentasjonsbehov == Dokumentasjonsbehov.EØS_SKJEMA }
        if (eøsDokumentasjon != null)  {
            søknadMedEøs.increment()
            if(eøsDokumentasjon.opplastedeVedlegg.isNotEmpty()) {
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