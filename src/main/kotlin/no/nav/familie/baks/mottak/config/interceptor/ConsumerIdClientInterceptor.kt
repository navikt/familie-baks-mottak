package no.nav.familie.baks.mottak.config.interceptor

import no.nav.familie.log.NavHttpHeaders
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component

@Component
class ConsumerIdClientInterceptor(
    @Value("\${application.name}") private val appName: String,
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        request.headers.add(NavHttpHeaders.NAV_CONSUMER_ID.asString(), appName)
        return execution.execute(request, body)
    }
}
