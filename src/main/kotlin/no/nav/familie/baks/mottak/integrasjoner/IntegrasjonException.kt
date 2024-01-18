package no.nav.familie.baks.mottak.integrasjoner

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.RestClientResponseException
import java.net.URI

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
open class IntegrasjonException(
    msg: String,
    throwable: Throwable? = null,
    uri: URI? = null,
    ident: String? = null,
) : RuntimeException(responseFra(uri, throwable) ?: msg, throwable) {
    init {
        val detaljertMelding = responseFra(uri, throwable)
        secureLogger.info(
            "$msg. ident={} {} {}",
            ident,
            detaljertMelding ?: uri,
            throwable,
        )
        logger.warn("$msg. {}", detaljertMelding ?: uri)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IntegrasjonException::class.java)
        private val secureLogger = LoggerFactory.getLogger("secureLogger")

        fun responseFra(
            uri: URI?,
            e: Throwable?,
        ): String? {
            return when (e) {
                is RestClientResponseException,
                -> "Error mot $uri status=${e.getRawStatusCode()} body=${e.responseBodyAsString}" else
                -> null
            }
        }
    }
}

class PdlNotFoundException(
    msg: String,
    uri: URI,
    ident: String,
) : IntegrasjonException(msg = msg, uri = uri, ident = ident)
