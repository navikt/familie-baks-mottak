package no.nav.familie.ba.mottak.e2e

import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.ba.mottak.hendelser.JournalhendelseService
import no.nav.familie.ba.mottak.hendelser.LeesahService
import no.nav.familie.kontrakter.felles.Ressurs
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.kafka.support.Acknowledgment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextUInt

@RestController
@RequestMapping("/internal/e2e")
@Profile(value = ["dev", "postgres", "e2e"])
class E2EController(
    private val leesahService: LeesahService,
    private val journalhendelseService: JournalhendelseService,
    private val hendelsesloggRepository: HendelsesloggRepository,
    private val taskRepository: TaskRepository,
    private val databaseCleanupService: DatabaseCleanupService
) {

    val logger: Logger = LoggerFactory.getLogger(E2EController::class.java)

    @PostMapping(path = ["/pdl/foedsel"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun pdlHendelseFødsel(@RequestBody personIdenter: List<String>): String {
        logger.info("Oppretter fødselshendelse e2e")
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
            offset = Random.nextUInt().toLong(),
            gjeldendeAktørId = personIdenter.first { it.length == 13 },
            hendelseId = hendelseId,
            personIdenter = personIdenter,
            endringstype = LeesahService.OPPRETTET,
            opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSEL,
            fødselsdato = LocalDate.now()
        )

        leesahService.prosesserNyHendelse(pdlHendelse)
        return hendelseId
    }

    @PostMapping(path = ["/pdl/doedsfall"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun pdlHendelseDødsfall(@RequestBody personIdenter: List<String>): String {
        logger.info("Oppretter dødshendelse e2e")
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
            offset = Random.nextUInt().toLong(),
            gjeldendeAktørId = personIdenter.first { it.length == 13 },
            hendelseId = hendelseId,
            personIdenter = personIdenter,
            endringstype = LeesahService.OPPRETTET,
            opplysningstype = LeesahService.OPPLYSNINGSTYPE_DØDSFALL,
            dødsdato = LocalDate.now()
        )

        leesahService.prosesserNyHendelse(pdlHendelse)
        return hendelseId
    }

    @PostMapping(path = ["/pdl/utflytting"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun pdlHendelseUtflytting(@RequestBody personIdenter: List<String>): String {
        logger.info("Oppretter utflyttingshendlse e2e")
        val hendelseId = UUID.randomUUID().toString()
        val pdlHendelse = PdlHendelse(
            offset = Random.nextUInt().toLong(),
            gjeldendeAktørId = personIdenter.first { it.length == 13 },
            hendelseId = hendelseId,
            personIdenter = personIdenter,
            endringstype = LeesahService.OPPRETTET,
            opplysningstype = LeesahService.OPPLYSNINGSTYPE_UTFLYTTING,
            utflyttingsdato = LocalDate.now()
        )

        leesahService.prosesserNyHendelse(pdlHendelse)
        return hendelseId
    }

    @PostMapping(path = ["/journal"])
    fun opprettJournalHendelse(@RequestBody journalpost: Journalpost): String {
        logger.info("Oppretter journalhendelse e2e")
        val hendelseid = UUID.randomUUID().toString()
        var journalHendelse = JournalfoeringHendelseRecord(
            hendelseid,
            1,
            "MidlertidigJournalført",
            journalpost.journalpostId,
            null, // Må settes på selve journalposten
            "BAR",
            "BAR",
            null, // Må settes på selve journalposten
            "e2e-$hendelseid",
            null
        ) // Kan settes på selve journalposten

        val cr = ConsumerRecord("topic", 1, 1, 1L, journalHendelse)
        val acknowledgment = E2EAcknowledgment()
        try {
            journalhendelseService.prosesserNyHendelse(cr, acknowledgment)
        } catch (e: Exception) {
            throw IllegalStateException("Feil ved prosessering av ny hendelse ", e)
        }
        if (!acknowledgment.ack) {
            throw error("Melding med $hendelseid ikke kjørt ok")
        }
        return hendelseid
    }

    @GetMapping(path = ["/hendelselogg/{hendelseId}/{consumer}"])
    fun hentHendelselogg(
        @PathVariable(name = "hendelseId", required = true) hendelseId: String,
        @PathVariable(name = "consumer", required = true) consumer: HendelseConsumer
    ): Boolean {
        return hendelsesloggRepository.existsByHendelseIdAndConsumer(hendelseId, consumer)
    }

    @GetMapping(path = ["/task/{key}/{value}"])
    fun hentTaskMedProperty(
        @PathVariable(name = "key", required = true) key: String,
        @PathVariable(name = "value", required = true) value: String
    ): List<Task> {
        return taskRepository.findAll().filter { it.metadata[key] == value }
    }

    @GetMapping(path = ["/truncate"])
    fun truncate(): ResponseEntity<Ressurs<String>> {
        databaseCleanupService.truncate()

        return ResponseEntity.ok(Ressurs.success("Truncate fullført"))
    }

    class E2EAcknowledgment : Acknowledgment {
        var ack: Boolean = false

        override fun acknowledge() {
            ack = true
        }
    }

    data class Journalpost(val journalpostId: Long)
}
