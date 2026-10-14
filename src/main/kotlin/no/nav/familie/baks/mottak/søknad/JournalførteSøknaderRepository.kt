package no.nav.familie.baks.mottak.søknad

import no.nav.familie.prosessering.domene.Status
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class JournalførteSøknaderRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    /**
     * Sletter søknader med vedlegg som har journalpostId, eller som er eldre enn [eldreEnn] uten en uferdig journalføringstask.
     * I prod slettes kun ferdige tasks, så en søknad uten uferdig journalføringstask er journalført.
     * Utenfor prod sletter FjernGamleFeiledeTasksIPreprod også gamle feilede tasks, så der slettes i tillegg
     * søknader som aldri ble journalført fordi journalføringstasken feilet og senere ble fjernet.
     */
    @Transactional
    fun slettJournalførteSøknader(
        søknadstype: Søknadstype,
        eldreEnn: LocalDateTime,
        maksAntall: Int,
    ): Int {
        val (søknadTabell, vedleggTabell) = søknadstype.tabeller()
        val søknadIder =
            jdbcTemplate.queryForList(
                """
                SELECT s.id FROM $søknadTabell s
                WHERE s.journalpost_id IS NOT NULL
                   OR (s.opprettet_tid < :eldreEnn
                       AND s.id::text NOT IN (SELECT t.payload FROM task t WHERE t.type = :tasktype AND t.status <> :ferdig))
                LIMIT :maksAntall
                """.trimIndent(),
                mapOf(
                    "eldreEnn" to eldreEnn,
                    "tasktype" to søknadstype.journalføringTasktype(),
                    "ferdig" to Status.FERDIG.name,
                    "maksAntall" to maksAntall,
                ),
                Long::class.java,
            )
        if (søknadIder.isEmpty()) return 0

        jdbcTemplate.update("DELETE FROM $vedleggTabell WHERE soknad_id IN (:soknadIder)", mapOf("soknadIder" to søknadIder))
        return jdbcTemplate.update("DELETE FROM $søknadTabell WHERE id IN (:soknadIder)", mapOf("soknadIder" to søknadIder))
    }

    private fun Søknadstype.tabeller() =
        when (this) {
            Søknadstype.BARNETRYGD -> "soknad" to "soknad_vedlegg"
            Søknadstype.KONTANTSTØTTE -> "kontantstotte_soknad" to "kontantstotte_soknad_vedlegg"
        }
}
