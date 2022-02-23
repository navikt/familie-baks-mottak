package no.nav.familie.ba.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.ba.mottak.domene.HendelseConsumer
import no.nav.familie.ba.mottak.domene.Hendelseslogg
import no.nav.familie.ba.mottak.domene.HendelsesloggRepository
import no.nav.familie.ba.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.ba.mottak.integrasjoner.RestAnnullerFødsel
import no.nav.familie.ba.mottak.task.MottaAnnullerFødselTask
import no.nav.familie.ba.mottak.task.MottaFødselshendelseTask
import no.nav.familie.ba.mottak.task.VurderLivshendelseTask
import no.nav.familie.ba.mottak.task.VurderLivshendelseTaskDTO
import no.nav.familie.ba.mottak.task.VurderLivshendelseType
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.DØDSFALL
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.SIVILSTAND
import no.nav.familie.ba.mottak.task.VurderLivshendelseType.UTFLYTTING
import no.nav.familie.ba.mottak.util.nesteGyldigeTriggertidFødselshendelser
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND.GIFT
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Properties

@Service
class LeesahService(
    private val hendelsesloggRepository: HendelsesloggRepository,
    private val taskRepository: TaskRepository,
    @Value("\${FØDSELSHENDELSE_VENT_PÅ_TPS_MINUTTER}") private val triggerTidForTps: Long,
    private val environment: Environment
) {

    val dødsfallCounter: Counter = Metrics.counter("barnetrygd.dodsfall")
    val dødsfallIgnorertCounter: Counter = Metrics.counter("barnetrygd.dodsfall.ignorert")
    val fødselOpprettetCounter: Counter = Metrics.counter("barnetrygd.fodsel.opprettet")
    val fødselKorrigertCounter: Counter = Metrics.counter("barnetrygd.fodsel.korrigert")
    val fødselAnnullertCounter: Counter = Metrics.counter("barnetrygd.fodsel.annullert")

    val fødselIgnorertCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorert")
    val fødselIgnorertUnder18årCounter: Counter = Metrics.counter("barnetrygd.fodsel.ignorert.under18")
    val fødselIgnorertFødelandCounter: Counter = Metrics.counter("barnetrygd.hendelse.ignorert.fodeland.nor")
    val sivilstandOpprettetCounter: Counter = Metrics.counter("barnetrygd.sivilstand.opprettet")
    val sivilstandAnnullertCounter: Counter = Metrics.counter("barnetrygd.sivilstand.annullert")
    val sivilstandKorrigertCounter: Counter = Metrics.counter("barnetrygd.sivilstand.korrigert")
    val sivilstandIgnorertCounter: Counter = Metrics.counter("barnetrygd.sivilstand.ignorert")
    val utflyttingOpprettetCounter: Counter = Metrics.counter("barnetrygd.utflytting.opprettet")
    val utflyttingAnnullertCounter: Counter = Metrics.counter("barnetrygd.utflytting.annullert")
    val utflyttingKorrigertCounter: Counter = Metrics.counter("barnetrygd.utflytting.korrigert")
    val utflyttingIgnorertCounter: Counter = Metrics.counter("barnetrygd.utflytting.ignorert")
    val leesahDuplikatCounter: Counter = Metrics.counter("barnetrygd.hendelse.leesah.duplikat")


    fun prosesserNyHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.opplysningstype) {
            OPPLYSNINGSTYPE_DØDSFALL -> behandleDødsfallHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_FØDSEL -> behandleFødselsHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_UTFLYTTING -> behandleUtflyttingHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_SIVILSTAND -> behandleSivilstandHendelse(pdlHendelse)
        }
    }

    private fun behandleDødsfallHendelse(pdlHendelse: PdlHendelse) {
        dødsfallCounter.increment()
        if (hendelsesloggRepository.existsByHendelseIdAndConsumer(pdlHendelse.hendelseId, CONSUMER_PDL)) {
            leesahDuplikatCounter.increment()
            return
        }
        logHendelse(pdlHendelse, "dødsdato: ${pdlHendelse.dødsdato}")

        when (pdlHendelse.endringstype) {
            OPPRETTET -> {

                if (pdlHendelse.dødsdato == null) {
                    log.error("Mangler dødsdato. Ignorerer hendelse ${pdlHendelse.hendelseId}")
                    dødsfallIgnorertCounter.increment()
                } else {
                    opprettVurderLivshendelseTaskForHendelse(DØDSFALL, pdlHendelse)
                }

            }
            else -> {
                logHendelse(pdlHendelse)
                logHendelse(pdlHendelse, "Ikke av type OPPRETTET. Dødsdato: ${pdlHendelse.dødsdato}")
            }

        }
        oppdaterHendelseslogg(pdlHendelse)
    }

    private fun behandleFødselsHendelse(pdlHendelse: PdlHendelse) {
        if (hendelsesloggRepository.existsByHendelseIdAndConsumer(pdlHendelse.hendelseId, CONSUMER_PDL)) {
            leesahDuplikatCounter.increment()
            return
        }
        when (pdlHendelse.endringstype) {
            OPPRETTET, KORRIGERT -> {
                logHendelse(pdlHendelse, "fødselsdato: ${pdlHendelse.fødselsdato}")

                val fødselsdato = pdlHendelse.fødselsdato
                if (fødselsdato == null) {
                    log.error("Mangler fødselsdato. Ignorerer hendelse ${pdlHendelse.hendelseId}")
                    fødselIgnorertCounter.increment()
                } else if (erUnder6mnd(fødselsdato)) {
                    if (erUtenforNorge(pdlHendelse.fødeland)) {
                        log.info("Fødeland er ikke Norge. Ignorerer hendelse ${pdlHendelse.hendelseId}")
                        fødselIgnorertFødelandCounter.increment()
                    } else {
                        when (pdlHendelse.endringstype) {
                            OPPRETTET -> fødselOpprettetCounter.increment()
                            KORRIGERT -> fødselKorrigertCounter.increment()
                        }

                        val task = Task.nyTaskMedTriggerTid(MottaFødselshendelseTask.TASK_STEP_TYPE,
                                                            pdlHendelse.hentPersonident(),
                                                            nesteGyldigeTriggertidFødselshendelser(triggerTidForTps, environment),
                                                            Properties().apply {
                                                                this["ident"] = pdlHendelse.hentPersonident()
                                                                this["callId"] = pdlHendelse.hendelseId
                                                            })
                        taskRepository.save(task)
                    }
                } else if (erUnder18år(fødselsdato)) {
                    fødselIgnorertUnder18årCounter.increment()
                } else {
                    fødselIgnorertCounter.increment()
                }
            }
            ANNULLERT -> {
                fødselAnnullertCounter.increment()
                if (pdlHendelse.tidligereHendelseId != null) {
                    val task = Task.nyTask(type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                                           payload = objectMapper.writeValueAsString(
                                               RestAnnullerFødsel(
                                                   barnasIdenter = pdlHendelse.hentPersonidenter(),
                                                   tidligereHendelseId = pdlHendelse.tidligereHendelseId
                                               )
                                           ),
                                           properties = Properties().apply {
                                               this["ident"] = pdlHendelse.hentPersonident()
                                               this["callId"] = pdlHendelse.hendelseId
                                               this["tidligereHendelseId"] = pdlHendelse.tidligereHendelseId
                                           })
                    taskRepository.save(task)
                } else {
                    log.warn("Mottatt annuller fødsel uten tidligereHendelseId, hendelseId ${pdlHendelse.hendelseId}")
                }
            }
            else -> {
                logHendelse(pdlHendelse)
            }
        }
        oppdaterHendelseslogg(pdlHendelse)
    }

    private fun behandleUtflyttingHendelse(pdlHendelse: PdlHendelse) {
        if (hendelsesloggRepository.existsByHendelseIdAndConsumer(pdlHendelse.hendelseId, CONSUMER_PDL)) {
            leesahDuplikatCounter.increment()
            return
        }

        when (pdlHendelse.endringstype) {
            OPPRETTET -> {
                logHendelse(pdlHendelse, "utflyttingsdato: ${pdlHendelse.utflyttingsdato}")
                utflyttingOpprettetCounter.increment()

                opprettVurderLivshendelseTaskForHendelse(UTFLYTTING, pdlHendelse)
            }
            else -> {
                logHendelse(pdlHendelse, "Ikke av type OPPRETTET.")
                when (pdlHendelse.endringstype) {
                    ANNULLERT -> utflyttingAnnullertCounter.increment()
                    KORRIGERT -> utflyttingKorrigertCounter.increment()
                }
            }
        }
        oppdaterHendelseslogg(pdlHendelse)
    }

    private fun behandleSivilstandHendelse(pdlHendelse: PdlHendelse) {
        if (hendelsesloggRepository.existsByHendelseIdAndConsumer(pdlHendelse.hendelseId, CONSUMER_PDL)) {
            leesahDuplikatCounter.increment()
            return
        }

        when (pdlHendelse.endringstype) {
            OPPRETTET -> {
                logHendelse(pdlHendelse, "sivilstandDato: ${pdlHendelse.sivilstandDato}")
                sivilstandOpprettetCounter.increment()

                opprettTaskHvisSivilstandErGift(pdlHendelse)
            }
            else -> {
                logHendelse(pdlHendelse, "Ikke av type OPPRETTET.")
                when (pdlHendelse.endringstype) {
                    ANNULLERT -> sivilstandOpprettetCounter.increment()
                    KORRIGERT -> sivilstandOpprettetCounter.increment()
                }
            }
        }
        oppdaterHendelseslogg(pdlHendelse)
    }

    private fun opprettTaskHvisSivilstandErGift(pdlHendelse: PdlHendelse) {
        if (pdlHendelse.sivilstand == GIFT.name) {
            opprettVurderLivshendelseTaskForHendelse(SIVILSTAND, pdlHendelse, pdlHendelse.sivilstandDato)
        } else {
            sivilstandIgnorertCounter.increment()
        }
    }

    private fun logHendelse(pdlHendelse: PdlHendelse, ekstraInfo: String = "") {
        log.info(
            "person-pdl-leesah melding mottatt: " +
                    "hendelseId: ${pdlHendelse.hendelseId} " +
                    "offset: ${pdlHendelse.offset}, " +
                    "opplysningstype: ${pdlHendelse.opplysningstype}, " +
                    "aktørid: ${pdlHendelse.gjeldendeAktørId}, " +
                    "endringstype: ${pdlHendelse.endringstype}, $ekstraInfo"
        )
    }

    private fun oppdaterHendelseslogg(pdlHendelse: PdlHendelse) {
        val metadata = mutableMapOf(
            "aktørId" to pdlHendelse.gjeldendeAktørId,
            "opplysningstype" to pdlHendelse.opplysningstype,
            "endringstype" to pdlHendelse.endringstype
        )

        if (pdlHendelse.fødeland != null) {
            metadata["fødeland"] = pdlHendelse.fødeland
        }

        hendelsesloggRepository.save(
            Hendelseslogg(
                pdlHendelse.offset,
                pdlHendelse.hendelseId,
                CONSUMER_PDL,
                metadata.toProperties(),
                ident = pdlHendelse.hentPersonident()
            )
        )
    }

    private fun opprettVurderLivshendelseTaskForHendelse(
        type: VurderLivshendelseType,
        pdlHendelse: PdlHendelse,
        gyldigFom: LocalDate? = null
    ) {
        log.info("opprett VurderLivshendelseTask for pdlHendelse (id= ${pdlHendelse.hendelseId})")
        Task.nyTaskMedTriggerTid(
            type = VurderLivshendelseTask.TASK_STEP_TYPE,
            payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(pdlHendelse.hentPersonident(), type, gyldigFom)),
            triggerTid = LocalDateTime.now().run { if (environment.activeProfiles.contains("prod")) this.plusHours(1) else this },
        ).apply {
            metadata["ident"] = pdlHendelse.hentPersonident()
            metadata["callId"] = pdlHendelse.hendelseId
            metadata["type"] = type.name
            taskRepository.save(this)
        }
    }

    private fun erUnder18år(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusYears(18))
    }

    private fun erUnder6mnd(fødselsDato: LocalDate): Boolean {
        return LocalDate.now().isBefore(fødselsDato.plusMonths(6))
    }

    private fun erUtenforNorge(fødeland: String?): Boolean {
        return when (fødeland) {
            null, "NOR" -> false
            else -> true
        }
    }

    companion object {

        private val CONSUMER_PDL = HendelseConsumer.PDL
        val log: Logger = LoggerFactory.getLogger(LeesahService::class.java)
        const val OPPRETTET = "OPPRETTET"
        const val KORRIGERT = "KORRIGERT"
        const val ANNULLERT = "ANNULLERT"
        const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
        const val OPPLYSNINGSTYPE_FØDSEL = "FOEDSEL_V1"
        const val OPPLYSNINGSTYPE_UTFLYTTING = "UTFLYTTING_FRA_NORGE"
        const val OPPLYSNINGSTYPE_SIVILSTAND = "SIVILSTAND_V1"
    }
}