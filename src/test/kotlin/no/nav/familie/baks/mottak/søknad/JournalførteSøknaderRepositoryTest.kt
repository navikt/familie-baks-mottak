package no.nav.familie.baks.mottak.søknad

import no.nav.familie.baks.mottak.DevLauncherPostgres
import no.nav.familie.baks.mottak.søknad.barnetrygd.BarnetrygdSøknadService
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBVedlegg
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.SøknadVedleggRepository
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.tilDBSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadService
import no.nav.familie.baks.mottak.søknad.kontantstøtte.KontantstøtteSøknadTestData
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstotteVedlegg
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.KontantstøtteVedleggRepository
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.tilDBKontantstøtteSøknad
import no.nav.familie.baks.mottak.task.JournalførSøknadTask
import no.nav.familie.prosessering.domene.Status
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID

@ExtendWith(SpringExtension::class)
@ActiveProfiles("postgres", "testcontainers")
@Tag("integration")
@SpringBootTest(classes = [DevLauncherPostgres::class])
class JournalførteSøknaderRepositoryTest(
    @Autowired
    val journalførteSøknaderRepository: JournalførteSøknaderRepository,
    @Autowired
    val barnetrygdSøknadService: BarnetrygdSøknadService,
    @Autowired
    val kontantstøtteSøknadService: KontantstøtteSøknadService,
    @Autowired
    val søknadVedleggRepository: SøknadVedleggRepository,
    @Autowired
    val kontantstøtteVedleggRepository: KontantstøtteVedleggRepository,
    @Autowired
    val jdbcTemplate: NamedParameterJdbcTemplate,
    @Autowired
    val taskService: TaskService,
) {
    private val toUkerSiden = LocalDateTime.now().minusWeeks(2)

    @Test
    fun `skal slette journalførte barnetrygdsøknader med vedlegg og beholde søknader som ikke er journalført`() {
        // Arrange
        val journalførtSøknad = lagreBarnetrygdSøknadMedVedlegg()
        val ikkeJournalførtSøknad = lagreBarnetrygdSøknadMedVedlegg()
        settJournalpostId("soknad", journalførtSøknad)

        // Act
        journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.BARNETRYGD, eldreEnn = toUkerSiden, maksAntall = 500)

        // Assert
        assertThat(barnetrygdSøknadService.hentDBSøknad(journalførtSøknad)).isNull()
        assertThat(søknadVedleggRepository.hentAlleVedlegg(journalførtSøknad)).isEmpty()
        assertThat(barnetrygdSøknadService.hentDBSøknad(ikkeJournalførtSøknad)).isNotNull()
        assertThat(søknadVedleggRepository.hentAlleVedlegg(ikkeJournalførtSøknad)).hasSize(1)
    }

    @Test
    fun `skal slette journalførte kontantstøttesøknader med vedlegg og beholde søknader som ikke er journalført`() {
        // Arrange
        val journalførtSøknad = lagreKontantstøtteSøknadMedVedlegg()
        val ikkeJournalførtSøknad = lagreKontantstøtteSøknadMedVedlegg()
        settJournalpostId("kontantstotte_soknad", journalførtSøknad)

        // Act
        journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.KONTANTSTØTTE, eldreEnn = toUkerSiden, maksAntall = 500)

        // Assert
        assertThat(kontantstøtteSøknadService.hentDBKontantstøtteSøknad(journalførtSøknad)).isNull()
        assertThat(kontantstøtteVedleggRepository.hentAlleVedlegg(journalførtSøknad)).isEmpty()
        assertThat(kontantstøtteSøknadService.hentDBKontantstøtteSøknad(ikkeJournalførtSøknad)).isNotNull()
        assertThat(kontantstøtteVedleggRepository.hentAlleVedlegg(ikkeJournalførtSøknad)).hasSize(1)
    }

    @Test
    fun `skal slette gamle søknader uten journalpostId når journalføringstasken ikke er uferdig`() {
        // Arrange
        val gammelSøknadUtenTask = lagreBarnetrygdSøknadMedVedlegg()
        val gammelSøknadMedFerdigTask = lagreBarnetrygdSøknadMedVedlegg()
        val gammelSøknadMedUferdigTask = lagreBarnetrygdSøknadMedVedlegg()
        listOf(gammelSøknadUtenTask, gammelSøknadMedFerdigTask, gammelSøknadMedUferdigTask).forEach {
            settOpprettetTid("soknad", it, LocalDateTime.now().minusWeeks(3))
        }
        lagreJournalføringTask(gammelSøknadMedFerdigTask, Status.FERDIG)
        lagreJournalføringTask(gammelSøknadMedUferdigTask, Status.MANUELL_OPPFØLGING)

        // Act
        journalførteSøknaderRepository.slettJournalførteSøknader(Søknadstype.BARNETRYGD, eldreEnn = toUkerSiden, maksAntall = 500)

        // Assert
        assertThat(barnetrygdSøknadService.hentDBSøknad(gammelSøknadUtenTask)).isNull()
        assertThat(søknadVedleggRepository.hentAlleVedlegg(gammelSøknadUtenTask)).isEmpty()
        assertThat(barnetrygdSøknadService.hentDBSøknad(gammelSøknadMedFerdigTask)).isNull()
        assertThat(barnetrygdSøknadService.hentDBSøknad(gammelSøknadMedUferdigTask)).isNotNull()
        assertThat(søknadVedleggRepository.hentAlleVedlegg(gammelSøknadMedUferdigTask)).hasSize(1)
    }

    private fun lagreBarnetrygdSøknadMedVedlegg(): Long {
        val søknad = barnetrygdSøknadService.lagreDBSøknad(SøknadTestData.barnetrygdSøknad().tilDBSøknad())
        søknadVedleggRepository.save(DBVedlegg(dokumentId = UUID.randomUUID().toString(), søknadId = søknad.id, data = ByteArray(1)))
        return søknad.id
    }

    private fun lagreKontantstøtteSøknadMedVedlegg(): Long {
        val søknad =
            kontantstøtteSøknadService.lagreDBKontantstøtteSøknad(KontantstøtteSøknadTestData.kontantstøtteSøknad().tilDBKontantstøtteSøknad())
        kontantstøtteVedleggRepository.save(DBKontantstotteVedlegg(dokumentId = UUID.randomUUID().toString(), søknadId = søknad.id, data = ByteArray(1)))
        return søknad.id
    }

    private fun settJournalpostId(
        tabell: String,
        søknadId: Long,
    ) {
        jdbcTemplate.update("UPDATE $tabell SET journalpost_id = '123' WHERE id = :id", mapOf("id" to søknadId))
    }

    private fun settOpprettetTid(
        tabell: String,
        søknadId: Long,
        opprettetTid: LocalDateTime,
    ) {
        jdbcTemplate.update("UPDATE $tabell SET opprettet_tid = :opprettetTid WHERE id = :id", mapOf("opprettetTid" to opprettetTid, "id" to søknadId))
    }

    private fun lagreJournalføringTask(
        søknadId: Long,
        status: Status,
    ) {
        taskService.save(
            Task(type = JournalførSøknadTask.JOURNALFØR_SØKNAD, payload = søknadId.toString(), properties = Properties()).copy(status = status),
        )
    }
}
