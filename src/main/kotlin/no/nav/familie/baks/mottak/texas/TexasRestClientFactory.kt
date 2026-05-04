package no.nav.familie.baks.mottak.texas

import no.nav.familie.restklient.interceptor.ConsumerIdClientInterceptor
import no.nav.familie.restklient.interceptor.MdcValuesPropagatingClientInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@Import(
    ConsumerIdClientInterceptor::class,
    MdcValuesPropagatingClientInterceptor::class,
)
class TexasRestClientFactory(
    private val texasClient: TexasClient,
    private val consumerIdClientInterceptor: ConsumerIdClientInterceptor,
    private val mdcValuesPropagatingClientInterceptor: MdcValuesPropagatingClientInterceptor,
    @Value("\${NAIS_APP_NAME}") private val appName: String,
) {
    fun lagMaskinRestKlient(target: String): RestClient =
        RestClient
            .builder()
            .requestInterceptor(consumerIdClientInterceptor)
            .requestInterceptor(mdcValuesPropagatingClientInterceptor)
            .requestInterceptor(TexasMaskinTokenInterceptor(texasClient, target))
            .build()
}
