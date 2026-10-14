package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.task.JournalførKontantstøtteSøknadTask
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SøknadStatusRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun finnTidspunktForSisteMottatteSøknad(søknadstype: Søknadstype): LocalDateTime? =
        jdbcTemplate.queryForObject(
            "SELECT max(opprettet_tid) FROM task WHERE type = :type",
            mapOf("type" to søknadstype.journalføringTasktype()),
            LocalDateTime::class.java,
        )
}

fun Søknadstype.journalføringTasktype() =
    when (this) {
        Søknadstype.BARNETRYGD -> JournalførSøknadTask.JOURNALFØR_SØKNAD
        Søknadstype.KONTANTSTØTTE -> JournalførKontantstøtteSøknadTask.JOURNALFØR_KONTANTSTØTTE_SØKNAD
    }
