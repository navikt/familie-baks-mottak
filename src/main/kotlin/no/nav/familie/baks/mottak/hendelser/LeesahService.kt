package no.nav.familie.baks.mottak.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import no.nav.familie.baks.mottak.domene.HendelseConsumer
import no.nav.familie.baks.mottak.domene.Hendelseslogg
import no.nav.familie.baks.mottak.domene.HendelsesloggRepository
import no.nav.familie.baks.mottak.domene.hendelser.PdlHendelse
import no.nav.familie.baks.mottak.hendelser.LeesahConsumer.Companion.SECURE_LOGGER
import no.nav.familie.baks.mottak.integrasjoner.RestAnnullerFødsel
import no.nav.familie.baks.mottak.task.FinnmarkstilleggTask
import no.nav.familie.baks.mottak.task.MottaAnnullerFødselTask
import no.nav.familie.baks.mottak.task.MottaFødselshendelseTask
import no.nav.familie.baks.mottak.task.SvalbardtilleggTask
import no.nav.familie.baks.mottak.task.VurderBarnetrygdLivshendelseTask
import no.nav.familie.baks.mottak.task.VurderFalskIdentitetTask
import no.nav.familie.baks.mottak.task.VurderFinnmarkstillleggTaskDTO
import no.nav.familie.baks.mottak.task.VurderKontantstøtteLivshendelseTask
import no.nav.familie.baks.mottak.task.VurderLivshendelseTaskDTO
import no.nav.familie.baks.mottak.task.VurderLivshendelseType
import no.nav.familie.baks.mottak.task.VurderLivshendelseType.SIVILSTAND
import no.nav.familie.baks.mottak.util.nesteGyldigeTriggertidFødselshendelser
import no.nav.familie.baks.mottak.util.nåPlussEnTimeIProd
import no.nav.familie.kontrakter.felles.jsonMapper
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTANDTYPE.GIFT
import no.nav.familie.kontrakter.felles.personopplysning.SIVILSTANDTYPE.REGISTRERT_PARTNER
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
import kotlin.random.Random.Default.nextLong

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

    val sivilstandOpprettetCounter: Counter = Metrics.counter("sivilstand.opprettet")
    val sivilstandIgnorertCounter: Counter = Metrics.counter("sivilstand.ignorert")
    val utflyttingOpprettetCounter: Counter = Metrics.counter("utflytting.opprettet")
    val utflyttingAnnullertCounter: Counter = Metrics.counter("utflytting.annullert")
    val utflyttingKorrigertCounter: Counter = Metrics.counter("utflytting.korrigert")
    val leesahDuplikatCounter: Counter = Metrics.counter("hendelse.leesah.duplikat")

    fun prosesserNyHendelse(pdlHendelse: PdlHendelse) {
        if (hendelsesloggRepository.existsByHendelseIdAndConsumer(pdlHendelse.hendelseId, CONSUMER_PDL)) {
            leesahDuplikatCounter.increment()
            return
        }

        when (pdlHendelse.opplysningstype) {
            OPPLYSNINGSTYPE_DØDSFALL -> behandleDødsfallHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_FØDSELSDATO -> behandleFødselsdatoHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_UTFLYTTING -> behandleUtflyttingHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_SIVILSTAND -> behandleSivilstandHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_BOSTEDSADRESSE -> behandleBostedsadresseHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_OPPHOLDSADRESSE -> behandleOppholdsadresseHendelse(pdlHendelse)
            OPPLYSNINGSTYPE_FALSK_ID -> behandleFalskIdentitet(pdlHendelse)
            OPPLYSNINGSTYPE_ADRESSEBESKYTTELSE -> behandleAdressebeskyttelseHendelse(pdlHendelse)
            else -> return
        }

        oppdaterHendelseslogg(pdlHendelse)
    }

    private fun behandleDødsfallHendelse(pdlHendelse: PdlHendelse) {
        dødsfallCounter.increment()

        when (pdlHendelse.endringstype) {
            OPPRETTET -> {
                SECURE_LOGGER.info("Mottatt behandleDødsfallHendelse $pdlHendelse")
                if (pdlHendelse.dødsdato == null) {
                    log.error("Mangler dødsdato. Ignorerer hendelse ${pdlHendelse.hendelseId}")
                    dødsfallIgnorertCounter.increment()
                } else {
                    opprettVurderBarnetrygdLivshendelseTaskForHendelse(VurderLivshendelseType.DØDSFALL, pdlHendelse)
                    opprettVurderKontantstøtteLivshendelseTaskForHendelse(VurderLivshendelseType.DØDSFALL, pdlHendelse)
                }
            }

            else -> {
                log.info("Ignorerer hendelse ${pdlHendelse.hendelseId}")
            }
        }
    }

    private fun behandleFalskIdentitet(pdlHendelse: PdlHendelse) {
        if (pdlHendelse.endringstype == OPPRETTET) {
            VurderFalskIdentitetTask
                .opprettTask(pdlHendelse)
                .medTriggerTid(nåPlussEnTimeIProd(environment))
                .also { taskService.save(it) }
        }
    }

    private fun behandleFødselsdatoHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.endringstype) {
            OPPRETTET, KORRIGERT -> {
                SECURE_LOGGER.info("Mottatt behandleFødselsdatoHendelse $pdlHendelse")

                val fødselsdato = pdlHendelse.fødselsdato
                if (fødselsdato == null) {
                    log.warn("Mangler fødselsdato. Ignorerer hendelse ${pdlHendelse.hendelseId}")
                    fødselIgnorertCounter.increment()
                } else if (erUnder6mnd(fødselsdato)) {
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
                } else if (erUnder18år(fødselsdato)) {
                    fødselIgnorertUnder18årCounter.increment()
                } else {
                    fødselIgnorertCounter.increment()
                }
            }

            ANNULLERT -> {
                fødselAnnullertCounter.increment()
                if (pdlHendelse.tidligereHendelseId != null) {
                    SECURE_LOGGER.info("Mottatt annulert behandleFødselsdatoHendelse $pdlHendelse")
                    val task =
                        Task(
                            type = MottaAnnullerFødselTask.TASK_STEP_TYPE,
                            payload =
                                jsonMapper.writeValueAsString(
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
                log.info("Ignorerer hendelse ${pdlHendelse.hendelseId}")
            }
        }
    }

    private fun behandleUtflyttingHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.endringstype) {
            OPPRETTET -> {
                SECURE_LOGGER.info("Mottatt behandleUtflyttingHendelse $pdlHendelse")
                utflyttingOpprettetCounter.increment()

                opprettVurderBarnetrygdLivshendelseTaskForHendelse(VurderLivshendelseType.UTFLYTTING, pdlHendelse)
                opprettVurderKontantstøtteLivshendelseTaskForHendelse(VurderLivshendelseType.UTFLYTTING, pdlHendelse)
            }

            else -> {
                log.info("Ignorerer hendelse ${pdlHendelse.hendelseId}")
                when (pdlHendelse.endringstype) {
                    ANNULLERT -> utflyttingAnnullertCounter.increment()
                    KORRIGERT -> utflyttingKorrigertCounter.increment()
                }
            }
        }
    }

    private fun behandleSivilstandHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.endringstype) {
            OPPRETTET -> {
                SECURE_LOGGER.info("Mottatt behandleSivilstandHendelse $pdlHendelse")
                sivilstandOpprettetCounter.increment()

                opprettTaskHvisSivilstandErGift(pdlHendelse)
            }

            else -> {
                log.info("Ignorerer hendelse ${pdlHendelse.hendelseId}")
                when (pdlHendelse.endringstype) {
                    ANNULLERT -> sivilstandOpprettetCounter.increment()
                    KORRIGERT -> sivilstandOpprettetCounter.increment()
                }
            }
        }
    }

    private fun opprettTaskHvisSivilstandErGift(pdlHendelse: PdlHendelse) {
        if (pdlHendelse.sivilstand in listOf(GIFT.name, REGISTRERT_PARTNER.name)) {
            opprettVurderBarnetrygdLivshendelseTaskForHendelse(SIVILSTAND, pdlHendelse)
        } else {
            sivilstandIgnorertCounter.increment()
        }
    }

    private fun behandleBostedsadresseHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.endringstype) {
            OPPRETTET,
            KORRIGERT,
            -> {
                SECURE_LOGGER.info("Mottatt behandleBostedsadresseHendelse $pdlHendelse")
                opprettFinnmarkstilleggTask(pdlHendelse)
            }

            else -> {
                log.info("Ignorerer hendelse ${pdlHendelse.hendelseId}: ${pdlHendelse.endringstype}")
            }
        }
    }

    private fun opprettFinnmarkstilleggTask(pdlHendelse: PdlHendelse) =
        Task(
            type = FinnmarkstilleggTask.TASK_STEP_TYPE,
            payload = lagFinnmarkstilleggTaskPayload(pdlHendelse),
        ).medTriggerTid(finnTriggerTidSvalbardOgFinnmarkstilleggTask())
            .apply {
                metadata["callId"] = pdlHendelse.hendelseId
                metadata["ident"] = pdlHendelse.hentPersonident()
            }.also { taskService.save(it) }

    private fun lagFinnmarkstilleggTaskPayload(pdlHendelse: PdlHendelse): String =
        jsonMapper.writeValueAsString(
            VurderFinnmarkstillleggTaskDTO(
                ident = pdlHendelse.hentPersonident(),
                bostedskommune = pdlHendelse.bostedskommune,
                bostedskommuneFomDato = pdlHendelse.bostedskommuneFomDato,
            ),
        )

    private fun behandleOppholdsadresseHendelse(pdlHendelse: PdlHendelse) {
        when (pdlHendelse.endringstype) {
            OPPRETTET,
            -> {
                SECURE_LOGGER.info("Mottatt behandleOppholdsadresseHendelse $pdlHendelse")
                opprettSvalbardtilleggTask(pdlHendelse)
            }

            else -> {
                log.info("Ignorerer hendelse ${pdlHendelse.hendelseId}: ${pdlHendelse.endringstype}")
            }
        }
    }

    private fun opprettSvalbardtilleggTask(pdlHendelse: PdlHendelse) =
        Task(
            type = SvalbardtilleggTask.TASK_STEP_TYPE,
            payload = pdlHendelse.hentPersonident(),
        ).medTriggerTid(finnTriggerTidSvalbardOgFinnmarkstilleggTask())
            .apply {
                metadata["callId"] = pdlHendelse.hendelseId
                metadata["ident"] = pdlHendelse.hentPersonident()
            }.also { taskService.save(it) }

    private fun finnTriggerTidSvalbardOgFinnmarkstilleggTask(): LocalDateTime =
        LocalDateTime.now().run {
            if (environment.activeProfiles.contains("prod")) {
                // Legger på tilfeldig delay på inntil 3 minutter for å
                // unngå at flere kall gjøres samtidig til ba-sak
                plusSeconds(nextLong(0, 180))
            } else {
                this
            }
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
            payload = jsonMapper.writeValueAsString(VurderLivshendelseTaskDTO(pdlHendelse.hentPersonident(), type)),
            properties =
                Properties().apply {
                    this["ident"] = pdlHendelse.hentPersonident()
                    this["callId"] = pdlHendelse.hendelseId
                    this["type"] = type.name
                },
        ).medTriggerTid(nåPlussEnTimeIProd(environment))
            .also { taskService.save(it) }
    }

    private fun opprettVurderKontantstøtteLivshendelseTaskForHendelse(
        type: VurderLivshendelseType,
        pdlHendelse: PdlHendelse,
    ) {
        log.info("opprett VurderKontantstøtteLivshendelseTask for pdlHendelse (id= ${pdlHendelse.hendelseId})")
        Task(
            type = VurderKontantstøtteLivshendelseTask.TASK_STEP_TYPE,
            payload = jsonMapper.writeValueAsString(VurderLivshendelseTaskDTO(pdlHendelse.hentPersonident(), type)),
            properties =
                Properties().apply {
                    this["ident"] = pdlHendelse.hentPersonident()
                    this["callId"] = pdlHendelse.hendelseId
                    this["type"] = type.name
                },
        ).medTriggerTid(nåPlussEnTimeIProd(environment))
            .also { taskService.save(it) }
    }

    private fun behandleAdressebeskyttelseHendelse(pdlHendelse: PdlHendelse) {
        // Logger  for å få innsikt i hvordan adressebeskyttelse-hendelser ser ut i praksis
        SECURE_LOGGER.info(
            "Mottatt ADRESSEBESKYTTELSE_v1-hendelse: " +
                "hendelseId=${pdlHendelse.hendelseId}, " +
                "endringstype=${pdlHendelse.endringstype}, " +
                "gradering=${pdlHendelse.adressebeskyttelse ?: "null"}, " +
                "aktørId=${pdlHendelse.gjeldendeAktørId}",
        )
    }

    private fun erUnder18år(fødselsDato: LocalDate): Boolean = LocalDate.now().isBefore(fødselsDato.plusYears(18))

    private fun erUnder6mnd(fødselsDato: LocalDate): Boolean = LocalDate.now().isBefore(fødselsDato.plusMonths(6))

    private fun erUtenforNorge(fødeland: String?): Boolean =
        when (fødeland) {
            null, "NOR" -> false
            else -> true
        }

    companion object {
        private val CONSUMER_PDL = HendelseConsumer.PDL
        val log: Logger = LoggerFactory.getLogger(LeesahService::class.java)
        const val OPPRETTET = "OPPRETTET"
        const val KORRIGERT = "KORRIGERT"
        const val ANNULLERT = "ANNULLERT"
        const val OPPLYSNINGSTYPE_DØDSFALL = "DOEDSFALL_V1"
        const val OPPLYSNINGSTYPE_FØDSELSDATO = "FOEDSELSDATO_V1"
        const val OPPLYSNINGSTYPE_UTFLYTTING = "UTFLYTTING_FRA_NORGE"
        const val OPPLYSNINGSTYPE_SIVILSTAND = "SIVILSTAND_V1"
        const val OPPLYSNINGSTYPE_BOSTEDSADRESSE = "BOSTEDSADRESSE_V1"
        const val OPPLYSNINGSTYPE_OPPHOLDSADRESSE = "OPPHOLDSADRESSE_V1"
        const val OPPLYSNINGSTYPE_FALSK_ID = "FALSK_ID_V1"
        const val OPPLYSNINGSTYPE_ADRESSEBESKYTTELSE = "ADRESSEBESKYTTELSE_V1"
    }
}
