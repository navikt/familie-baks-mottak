package no.nav.familie.baks.mottak.config

import no.nav.familie.log.NavSystemtype
import no.nav.familie.log.filter.LogFilter
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.web.server.servlet.ServletWebServerFactory
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootConfiguration
@ComponentScan(
    "no.nav.familie.prosessering",
    "no.nav.familie.sikkerhet",
    "no.nav.familie.baks.mottak",
    "no.nav.familie.unleash",
)
@EntityScan("no.nav.familie.prosessering", "no.nav.familie.baks.mottak")
@ConfigurationPropertiesScan("no.nav.familie")
@EnableScheduling
@EnableResilientMethods
class ApplicationConfig {
    @Bean
    fun servletWebServerFactory(): ServletWebServerFactory = JettyServletWebServerFactory()

    @Bean
    fun logFilter(): FilterRegistrationBean<LogFilter> {
        val filterRegistration = FilterRegistrationBean<LogFilter>()
        filterRegistration.setFilter(LogFilter(NavSystemtype.NAV_INTEGRASJON))
        filterRegistration.order = 1
        return filterRegistration
    }
}
