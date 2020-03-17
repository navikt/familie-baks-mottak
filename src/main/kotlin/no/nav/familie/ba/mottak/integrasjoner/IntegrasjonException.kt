package no.nav.familie.ba.mottak.integrasjoner

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.RestClientResponseException
import java.net.URI

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
class IntegrasjonException(msg: String,
                           throwable: Throwable? = null,
                           uri: URI? = null,
                           ident: String? = null) : RuntimeException(msg, throwable) {

    init {
        val response = when { throwable is RestClientResponseException
            -> "Responsekode: ${throwable.getRawStatusCode()}, body: ${throwable.responseBodyAsString}" else
            -> ""
        }
        secureLogger.info("$msg. ident={} {} {}",
                          uri,
                          ident,
                          response,
                          throwable)
        logger.warn("$msg. {} {}", uri, response)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IntegrasjonException::class.java)
        private val secureLogger = LoggerFactory.getLogger("secureLogger")
    }
}
