package no.nav.familie.baks.mottak.domene

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import no.nav.familie.baks.mottak.util.PropertiesToStringConverter
import java.time.LocalDateTime
import java.util.Properties

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
    val ident: String? = null,
)

enum class HendelseConsumer {
    PDL,
    JOURNAL_AIVEN,
    EF_VEDTAK_V1,
    EF_VEDTAK_INFOTRYGD_V1,
}
