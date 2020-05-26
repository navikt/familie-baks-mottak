package no.nav.familie.ba.mottak.hendelser

import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.joarkjournalfoeringhendelser.JournalfoeringHendelseRecord
import no.nav.security.token.support.core.api.Unprotected
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.kafka.support.Acknowledgment
import org.springframework.util.Assert
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*
import kotlin.random.Random
import kotlin.random.nextUInt


@RestController
@RequestMapping("/e2e")
@Profile(value = ["dev", "postgres", "e2e"])
class HendelseController(private val leesahService: LeesahService,
                         private val journalhendelseService: JournalhendelseService) {


    @PostMapping(path = ["/pdl/foedsel"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Unprotected
    fun pdlHendelseFødsel(@RequestBody personIdenter: List<String>) {
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                hendelseId = UUID.randomUUID().toString(),
                personIdenter = personIdenter,
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_FØDSEL,
                fødselsdato = LocalDate.now())

        leesahService.prosesserNyHendelse(pdlHendelse)
    }

    @PostMapping(path = ["/pdl/doedsfall"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Unprotected
    fun pdlHendelseDødsfall(@RequestBody personIdenter: List<String>) {
        val pdlHendelse = PdlHendelse(
                offset = Random.nextUInt().toLong(),
                hendelseId = UUID.randomUUID().toString(),
                personIdenter = personIdenter,
                endringstype = LeesahService.OPPRETTET,
                opplysningstype = LeesahService.OPPLYSNINGSTYPE_DØDSFALL,
                dødsdato = LocalDate.now())

        leesahService.prosesserNyHendelse(pdlHendelse)
    }


    @PostMapping(path = ["/journal/{journalpostId}"])
    @Unprotected
    fun opprettJournalHendelse(@PathVariable(name = "journalpostId", required = true) journalpostId: Long) {

        val hendelseid = UUID.randomUUID().toString()
        var journalHendelse = JournalfoeringHendelseRecord(
                hendelseid,
                1,
                "MidlertidigJournalført",
                123L,
                null, //Må settes på selve journalposten
                "BAR",
                "BAR",
                null, //Må settes på selve journalposten
                "e2e-$hendelseid",
                null) //Kan settes på selve journalposten

        val cr = ConsumerRecord("topic", 1, 1, 1L, journalHendelse)
        val acknowledgment = E2EAcknowledgment()
        journalhendelseService.prosesserNyHendelse(cr, acknowledgment)
        Assert.isTrue(acknowledgment.ack, "Melding med $hendelseid ikke kjørt ok")
    }

    class E2EAcknowledgment: Acknowledgment {
        var ack:Boolean = false

        override fun acknowledge() {
            ack = true
        }

    }
}