package no.nav.familie.baks.mottak.søknad.kontantstøtte

import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.søknad.Kvittering
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.FødselsnummerErNullException
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV1
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteSøknadV2
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.VersjonertKontantstøtteSøknad
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.ks.søknad.v1.Dokumentasjonsbehov
import no.nav.familie.kontrakter.ks.søknad.v1.KontantstøtteSøknad as KontantstøtteSøknadKontraktV1
import no.nav.familie.kontrakter.ks.søknad.v2.KontantstøtteSøknad as KontantstøtteSøknadKontraktV2
import no.nav.familie.kontrakter.ks.søknad.v1.Søknaddokumentasjon
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
@RequestMapping(path = ["/api/kontantstotte/"], produces = [APPLICATION_JSON_VALUE])
@ProtectedWithClaims(issuer = "tokenx", claimMap = ["acr=Level4"])
class KontantstøtteSøknadController(
    private val kontantstøtteSøknadService: KontantstøtteSøknadService
) {

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

    @Deprecated("Endepunkt for utdatert versjon av ks-søknad")
    @PostMapping(value = ["/soknad"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: KontantstøtteSøknadKontraktV1): ResponseEntity<Ressurs<Kvittering>> =
        mottaVersjonertSøknadOgSendMetrikker(versjonertKontantstøtteSøknad = KontantstøtteSøknadV1(søknad = søknad))

    @PostMapping(value = ["/soknad/v2"], consumes = [MULTIPART_FORM_DATA_VALUE])
    fun taImotSøknad(@RequestPart("søknad") søknad: KontantstøtteSøknadKontraktV2): ResponseEntity<Ressurs<Kvittering>> =
        mottaVersjonertSøknadOgSendMetrikker(versjonertKontantstøtteSøknad = KontantstøtteSøknadV2(søknad = søknad))

    fun mottaVersjonertSøknadOgSendMetrikker(versjonertKontantstøtteSøknad: VersjonertKontantstøtteSøknad): ResponseEntity<Ressurs<Kvittering>> {
        return try {
            val dbKontantstøtteSøknad = kontantstøtteSøknadService.mottaKontantstøtteSøknad(versjonertKontantstøtteSøknad = versjonertKontantstøtteSøknad)
            sendMetrics(versjonertKontantstøtteSøknad = versjonertKontantstøtteSøknad)
            ResponseEntity.ok(
                Ressurs.success(
                    Kvittering(
                        "Søknad om kontantstøtte er mottatt",
                        dbKontantstøtteSøknad.opprettetTid
                    )
                )
            )
        } catch (e: FødselsnummerErNullException) {
            søknadMottattFeil.increment()
            ResponseEntity.status(500).body(Ressurs.failure("Lagring av søknad om kontantstøtte feilet"))
        }
    }

    private fun sendMetrics(versjonertKontantstøtteSøknad: VersjonertKontantstøtteSøknad) {
        val dokumentasjon = when (versjonertKontantstøtteSøknad) {
            is KontantstøtteSøknadV1 -> versjonertKontantstøtteSøknad.søknad.dokumentasjon
            is KontantstøtteSøknadV2 -> versjonertKontantstøtteSøknad.søknad.dokumentasjon
        }

        val harEøsSteg = when (versjonertKontantstøtteSøknad) {
            is KontantstøtteSøknadV1 -> versjonertKontantstøtteSøknad.søknad.antallEøsSteg > 0
            is KontantstøtteSøknadV2 -> versjonertKontantstøtteSøknad.søknad.antallEøsSteg > 0
        }

        sendMetricsSøknad(harEøsSteg)

        sendMetricsDokumentasjon(dokumentasjon)
    }

    private fun sendMetricsSøknad(harEøsSteg: Boolean) {
        søknadMottattOk.increment()
        if (harEøsSteg) {
            søknadEøs.increment()
        }
    }

    private fun sendMetricsDokumentasjon(dokumentasjon: List<Søknaddokumentasjon>) {
        if (dokumentasjon.isNotEmpty()) {
            val dokumentasjonsbehovUtenAnnenDokumentasjon =
                dokumentasjon.filter { it.dokumentasjonsbehov != Dokumentasjonsbehov.ANNEN_DOKUMENTASJON }

            if (dokumentasjonsbehovUtenAnnenDokumentasjon.isNotEmpty()) {
                sendMetricsDokumentasjonsbehov(
                    dokumentasjonsbehov = dokumentasjonsbehovUtenAnnenDokumentasjon
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

    private fun sendMetricsDokumentasjonsbehov(dokumentasjonsbehov: List<Søknaddokumentasjon>) {
        søknadHarDokumentasjonsbehov.increment()
        antallDokumentasjonsbehov.increment(dokumentasjonsbehov.size.toDouble())
    }

    @GetMapping(value = ["/ping"])
    @Unprotected
    fun ping(): ResponseEntity<String> {
        return ResponseEntity.ok().body("OK")
    }
}
