package no.nav.familie.ba.mottak.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("familie.ba.pdfgenerator")
@ConstructorBinding
data class PdfgeneratorConfig(val url: String)