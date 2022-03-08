package no.nav.familie.ba.mottak.domene

import no.nav.familie.prosessering.domene.PropertiesToStringConverter
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "HENDELSESLOGG")
data class Hendelseslogg(
        @Column(name = "kafka_offset")
        val offset: Long,

        @Column(name = "hendelse_id")
        val hendelseId: String,

        @Enumerated(EnumType.STRING)
        @Column(name = "consumer")
        val consumer: HendelseConsumer,

        @Convert(converter = PropertiesToStringConverter::class)
        @Column(name = "metadata")
        val metadata: Properties = Properties(),

        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hendelseslogg_seq")
        @SequenceGenerator(name = "hendelseslogg_seq")
        val id: Long? = null,

        @Column(name = "opprettet_tid", nullable = false, updatable = false)
        val opprettetTidspunkt: LocalDateTime = LocalDateTime.now(),

        @Column(name = "ident", nullable = true)
        val ident: String? = null
)

enum class HendelseConsumer {
        PDL,
        JOURNAL,
        JOURNAL_AIVEN,
        EF_VEDTAK_V1,
        EF_VEDTAK_INFOTRYGD_V1
}
