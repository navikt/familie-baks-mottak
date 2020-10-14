package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.ba.mottak.task.MottaFødselshendelseTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
class LeesahService(private val hendelsesloggRepository: HendelsesloggRepository,
                    private val taskRepository: TaskRepository,
                    @Value("\${FØDSELSHENDELSE_VENT_PÅ_TPS_MINUTTER}") private val triggerTidForTps: Long) {

    val dødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall")
    val fødselOpprettetCounter: Counter = Metrics.counter("barnetrygd.fodsel.opprettet")
    val fødselKorrigertCounter: Counter = Metrics.counter("barnetrygd.fodsel.korrigert")
    val fødselIgnorertCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorert")
    val fødselIgnorertUnder18årCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorert.under18")
    val fødselIgnorertFødelandCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.fodeland.nor")


    fun prosesserNyHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.opplysningstype) {
            OPPLYSNINGSTYPE_DØDSFALL -> behandleDødsfallHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_FØDSEL -> behandleFødselsHendelse(pdlHendelse)
        }
    }

    private fun behandleDødsfallHendelse(pdlHendelse: PdlHendelse) {
        dødsfallCounter.increment()

        when (pdlHendelse.endringstype) {
            OPPRETTET, KORRIGERT -> {
                logHendelse(pdlHendelse, "dødsdato: ${pdlHendelse.dødsdato}")
            }
            else -> {
                logHendelse(pdlHendelse)
            }
        }
        oppdaterHendelseslogg(pdlHendelse)
    }

    private fun behandleFødselsHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.endringstype) {
            OPPRETTET, KORRIGERT -> {
                logHendelse(pdlHendelse, "fødselsdato: ${pdlHendelse.fødselsdato}")

                val fødselsdato = pdlHendelse.fødselsdato

                if (fødselsdato == null) {
                    log.error("Mangler fødselsdato. Ignorerer hendelse ${pdlHendelse.hendelseId}")
                    fødselIgnorertCounter.increment()
                } else if (erUtenforNorge(pdlHendelse.fødeland)) {
                    log.info("Fødeland er ikke Norge. Ignorerer hendelse ${pdlHendelse.hendelseId}")
                    fødselIgnorertFødelandCounter.increment()
                } else if (erUnder6mnd(fødselsdato)) {
                    when (pdlHendelse.endringstype) {
                        OPPRETTET -> fødselOpprettetCounter.increment()
                        KORRIGERT -> fødselKorrigertCounter.increment()
                    }

                    val task = Task.nyTaskMedTriggerTid(MottaFødselshendelseTask.TASK_STEP_TYPE,
                                                        pdlHendelse.hentPersonident(),
                                                        LocalDateTime.now().plusMinutes(triggerTidForTps),
                                                        Properties().apply {
                                                            this["ident"] = pdlHendelse.hentPersonident()
                                                        })
                    taskRepository.save(task)
                } else if (erUnder18år(fødselsdato)) {
                    fødselIgnorertUnder18årCounter.increment()
                } else {
                    fødselIgnorertCounter.increment()
                }
            }

            else -> {
                logHendelse(pdlHendelse)
            }
        }
        oppdaterHendelseslogg(pdlHendelse)
    }

    private fun logHendelse(pdlHendelse: PdlHendelse, ekstraInfo: String = "") {
        log.info("person-pdl-leesah melding mottatt: offset: {}, opplysningstype: {}, aktørid: {}, " +
                 "endringstype: {}, $ekstraInfo",
                 pdlHendelse.offset,
                 pdlHendelse.opplysningstype,
                 pdlHendelse.hentAktørId(),
                 pdlHendelse.endringstype)
    }

    private fun oppdaterHendelseslogg(pdlHendelse: PdlHendelse) {
        val metadata = mutableMapOf("aktørId" to pdlHendelse.hentAktørId(),
                                    "opplysningstype" to pdlHendelse.opplysningstype,
                                    "endringstype" to pdlHendelse.endringstype)

        if (pdlHendelse.fødeland != null) {
            metadata["fødeland"] = pdlHendelse.fødeland
        }

        hendelsesloggRepository.save(Hendelseslogg(pdlHendelse.offset,
                                                   pdlHendelse.hendelseId,
                                                   CONSUMER_PDL,
                                                   metadata.toProperties(),
                                                   ident = pdlHendelse.hentPersonident()))
    }

    private fun erUnder18år(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusYears(18))
    }

    private fun erUnder6mnd(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusMonths(6))
    }

    private fun erUtenforNorge(fødeland: String?): Boolean {
        return when (fødeland) {
            null, "NOR" ->  false
            else -> true
        }
    }

    companion object {

        private val CONSUMER_PDL = HendelseConsumer.PDL
        val log: Logger = LoggerFactory.getLogger(LeesahService::class.java)
        const val OPPRETTET = "OPPRETTET"
        const val KORRIGERT = "KORRIGERT"
        const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
        const val OPPLYSNINGSTYPE_FØDSEL = "FOEDSEL_V1"
    }
}