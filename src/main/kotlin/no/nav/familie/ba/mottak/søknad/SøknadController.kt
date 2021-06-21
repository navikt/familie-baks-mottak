package no.nav.familie.ba.mottak.søknad

import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.config.FeatureToggleService
import no.nav.familie.ba.mottak.søknad.domene.FødselsnummerErNullException
import no.nav.familie.kontrakter.ba.søknad.Søknad
import no.nav.familie.kontrakter.ba.søknad.Søknadsvedlegg
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

    val søknadMottattOk = Metrics.counter("barnetrygd.soknad.mottatt.ok")
    val søknadMottattFeil = Metrics.counter("barnetrygd.soknad.mottatt.feil")

    val søknadHarDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.dokumentasjonsbehov")
    val antallDokumentasjonsbehov = Metrics.counter("barnetrygd.soknad.dokumentasjonsbehov.antall")
    val søknadHarVedlegg = Metrics.counter("barnetrygd.soknad.harVedlegg")
    val antallVedlegg = Metrics.counter("barnetrygd.soknad.harVedlegg.antall")

    @PostMapping(value = ["/soknad"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: Søknad): ResponseEntity<Ressurs<Kvittering>> {
        val lagreSøknad = featureToggleService.isEnabled("familie-ba-mottak.lagre-soknad")
        log.info("Lagring av søknad = $lagreSøknad")

        return if (lagreSøknad) {
            try {
                val dbSøknad = søknadService.motta(søknad)
                sendMetrics(søknad)

                ResponseEntity.ok(Ressurs.success(Kvittering("Søknad er mottatt", dbSøknad.opprettetTid)))
            } catch (e: FødselsnummerErNullException) {
                søknadMottattFeil.increment()
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
        søknadMottattOk.increment()

        if (søknad.dokumentasjon.isNotEmpty()) {
            søknadHarDokumentasjonsbehov.increment()
            antallDokumentasjonsbehov.increment(søknad.dokumentasjon.size.toDouble())

            val alleVedlegg: List<Søknadsvedlegg> = søknad.dokumentasjon.map { it.opplastedeVedlegg }.flatten()
            if (alleVedlegg.isNotEmpty()) {
                søknadHarVedlegg.increment()
                antallVedlegg.increment(alleVedlegg.size.toDouble())
            }
        }
    }

    @GetMapping(value = ["/ping"])
    @Unprotected
    fun ping(): ResponseEntity<String> {
        return ResponseEntity.ok().body("OK")
    }
}