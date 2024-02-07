package no.nav.familie.baks.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.domene.HendelseConsumer
import no.nav.familie.baks.mottak.domene.Hendelseslogg
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.integrasjoner.RestAnnullerFødsel
import no.nav.familie.baks.mottak.task.MottaAnnullerFødselTask
import no.nav.familie.baks.mottak.task.MottaFødselshendelseTask
import no.nav.familie.baks.mottak.task.VurderBarnetrygdLivshendelseTask
import no.nav.familie.baks.mottak.task.VurderKontantstøtteLivshendelseTask
import no.nav.familie.baks.mottak.task.VurderLivshendelseTaskDTO
import no.nav.familie.baks.mottak.task.VurderLivshendelseType
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.SIVILSTAND
import no.nav.familie.baks.mottak.util.nesteGyldigeTriggertidFødselshendelser
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTAND.GIFT
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.internal.TaskService
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
    private val taskService: TaskService,
    @Value("\${FØDSELSHENDELSE_VENT_PÅ_TPS_MINUTTER}") private val triggerTidForTps: Long,
    private val environment: Environment,
) {
    val dødsfallCounter: Counter = Metrics.counter("dodsfall")
    val dødsfallIgnorertCounter: Counter = Metrics.counter("dodsfall.ignorert")
    val fødselOpprettetCounter: Counter = Metrics.counter("fodsel.opprettet")
    val fødselKorrigertCounter: Counter = Metrics.counter("fodsel.korrigert")
    val fødselAnnullertCounter: Counter = Metrics.counter("fodsel.annullert")

    val fødselIgnorertCounter: Counter = Metrics.counter("fodsel.ignorert")
    val fødselIgnorertUnder18årCounter: Counter = Metrics.counter("fodsel.ignorert.under18")
    val fødselIgnorertFødelandCounter: Counter = Metrics.counter("hendelse.ignorert.fodeland.nor")
    val sivilstandOpprettetCounter: Counter = Metrics.counter("sivilstand.opprettet")
    val sivilstandIgnorertCounter: Counter = Metrics.counter("sivilstand.ignorert")
    val utflyttingOpprettetCounter: Counter = Metrics.counter("utflytting.opprettet")
    val utflyttingAnnullertCounter: Counter = Metrics.counter("utflytting.annullert")
    val utflyttingKorrigertCounter: Counter = Metrics.counter("utflytting.korrigert")
    val leesahDuplikatCounter: Counter = Metrics.counter("hendelse.leesah.duplikat")

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
                    opprettVurderBarnetrygdLivshendelseTaskForHendelse(VurderLivshendelseType.DØDSFALL, pdlHendelse)
                    opprettVurderKontantstøtteLivshendelseTaskForHendelse(VurderLivshendelseType.DØDSFALL, pdlHendelse)
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
                    log.warn("Mangler fødselsdato. Ignorerer hendelse ${pdlHendelse.hendelseId}")
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

                        val task =
                            Task(
                                type = MottaFødselshendelseTask.TASK_STEP_TYPE,
                                payload = pdlHendelse.hentPersonident(),
                                properties =
                                    Properties().apply {
                                        this["ident"] = pdlHendelse.hentPersonident()
                                        this["callId"] = pdlHendelse.hendelseId
                                    },
                            ).medTriggerTid(
                                nesteGyldigeTriggertidFødselshendelser(triggerTidForTps),
                            )
                        taskService.save(task)
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
                    val task =
                        Task(
                            type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                            payload =
                                objectMapper.writeValueAsString(
                                    RestAnnullerFødsel(
                                        barnasIdenter = pdlHendelse.hentPersonidenter(),
                                        tidligereHendelseId = pdlHendelse.tidligereHendelseId,
                                    ),
                                ),
                            properties =
                                Properties().apply {
                                    this["ident"] = pdlHendelse.hentPersonident()
                                    this["callId"] = pdlHendelse.hendelseId
                                    this["tidligereHendelseId"] = pdlHendelse.tidligereHendelseId
                                },
                        )
                    taskService.save(task)
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

                opprettVurderBarnetrygdLivshendelseTaskForHendelse(VurderLivshendelseType.UTFLYTTING, pdlHendelse)
                opprettVurderKontantstøtteLivshendelseTaskForHendelse(VurderLivshendelseType.UTFLYTTING, pdlHendelse)
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
            opprettVurderBarnetrygdLivshendelseTaskForHendelse(SIVILSTAND, pdlHendelse)
        } else {
            sivilstandIgnorertCounter.increment()
        }
    }

    private fun logHendelse(
        pdlHendelse: PdlHendelse,
        ekstraInfo: String = "",
    ) {
        log.info(
            "person-pdl-leesah melding mottatt: " +
                "hendelseId: ${pdlHendelse.hendelseId} " +
                "offset: ${pdlHendelse.offset}, " +
                "opplysningstype: ${pdlHendelse.opplysningstype}, " +
                "aktørid: ${pdlHendelse.gjeldendeAktørId}, " +
                "endringstype: ${pdlHendelse.endringstype}, $ekstraInfo",
        )
    }

    private fun oppdaterHendelseslogg(pdlHendelse: PdlHendelse) {
        val metadata =
            mutableMapOf(
                "aktørId" to pdlHendelse.gjeldendeAktørId,
                "opplysningstype" to pdlHendelse.opplysningstype,
                "endringstype" to pdlHendelse.endringstype,
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
                ident = pdlHendelse.hentPersonident(),
            ),
        )
    }

    private fun opprettVurderBarnetrygdLivshendelseTaskForHendelse(
        type: VurderLivshendelseType,
        pdlHendelse: PdlHendelse,
    ) {
        log.info("opprett VurderBarnetrygdLivshendelseTask for pdlHendelse (id= ${pdlHendelse.hendelseId})")
        Task(
            type = VurderBarnetrygdLivshendelseTask.TASK_STEP_TYPE,
            payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(pdlHendelse.hentPersonident(), type)),
            properties =
                Properties().apply {
                    this["ident"] = pdlHendelse.hentPersonident()
                    this["callId"] = pdlHendelse.hendelseId
                    this["type"] = type.name
                },
        ).medTriggerTid(LocalDateTime.now().run { if (environment.activeProfiles.contains("prod")) this.plusHours(1) else this })
            .also {
                taskService.save(it)
            }
    }

    private fun opprettVurderKontantstøtteLivshendelseTaskForHendelse(
        type: VurderLivshendelseType,
        pdlHendelse: PdlHendelse,
    ) {
        log.info("opprett VurderKontantstøtteLivshendelseTask for pdlHendelse (id= ${pdlHendelse.hendelseId})")
        Task(
            type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
            payload = objectMapper.writeValueAsString(VurderLivshendelseTaskDTO(pdlHendelse.hentPersonident(), type)),
            properties =
                Properties().apply {
                    this["ident"] = pdlHendelse.hentPersonident()
                    this["callId"] = pdlHendelse.hendelseId
                    this["type"] = type.name
                },
        ).medTriggerTid(LocalDateTime.now().run { if (environment.activeProfiles.contains("prod")) this.plusHours(1) else this })
            .also {
                taskService.save(it)
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
