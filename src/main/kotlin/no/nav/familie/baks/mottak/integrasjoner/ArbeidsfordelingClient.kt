package no.nav.familie.baks.mottak.integrasjoner

import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggle.HENT_ARBEIDSFORDELING_MED_BEHANDLINGSTYPE
import no.nav.familie.baks.mottak.config.featureToggle.FeatureToggleService
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.kontrakter.felles.PersonIdent
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.kontrakter.felles.Tema
import no.nav.familie.kontrakter.felles.arbeidsfordeling.Enhet
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.util.Optional.ofNullable

@Component
class ArbeidsfordelingClient(
    @param:Value("\${FAMILIE_INTEGRASJONER_API_URL}") private val integrasjonUri: URI,
    @Qualifier("clientCredentials") restOperations: RestOperations,
    private val featureToggleService: FeatureToggleService,
) : AbstractRestClient(restOperations, "integrasjon") {
    fun hentBehandlendeEnheterPåIdent(
        personIdent: String,
        tema: Tema,
        behandlingstype: Behandlingstype?,
    ): List<Enhet> {
        val uri =
            UriComponentsBuilder
                .fromUri(integrasjonUri)
                .pathSegment("arbeidsfordeling", "enhet", tema.toString())
                .let {
                    if (featureToggleService.isEnabled(HENT_ARBEIDSFORDELING_MED_BEHANDLINGSTYPE, false)) {
                        it.queryParamIfPresent("behandlingstype", ofNullable(behandlingstype))
                    } else {
                        it
                    }
                }.build()
                .encode()
                .toUri()

        return runCatching {
            postForEntity<Ressurs<List<Enhet>>>(uri, PersonIdent(personIdent))
        }.fold(
            onSuccess = { it.data ?: throw IntegrasjonException(it.melding, uri = uri, ident = personIdent) },
            onFailure = { throw IntegrasjonException("Feil ved henting av behandlende enheter på ident m/ tema $tema og behandlingstype $behandlingstype", it, uri, personIdent) },
        )
    }

    fun hentBehandlendeEnhetPåIdent(
        personIdent: String,
        tema: Tema,
        behandlingstype: Behandlingstype?,
    ): Enhet = hentBehandlendeEnheterPåIdent(personIdent, tema, behandlingstype).singleOrNull() ?: throw IllegalStateException("Forventet bare 1 enhet på ident men fantes flere")
}
