package no.nav.familie.ba.mottak.domene

import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDateTime
import java.util.Properties
import javax.persistence.AttributeConverter
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Converter
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.SequenceGenerator
import javax.persistence.Table

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
        JOURNAL_AIVEN,
        EF_VEDTAK_V1,
        EF_VEDTAK_INFOTRYGD_V1
}

/**
 * JPA konverterer for å skrive ned en key=value text til et databasefelt (output tilsvarer java.util.Properties
 * format).
 */
@Converter
class PropertiesToStringConverter : AttributeConverter<Properties, String> {

        override fun convertToDatabaseColumn(props: Properties?): String? {
                if (props == null || props.isEmpty) {
                        return null
                }
                val stringWriter = StringWriter(512)
                // custom i stedet for Properties.store slik at vi ikke får med default timestamp
                props.forEach { key, value -> stringWriter.append(key as String).append('=').append(value as String).append('\n') }
                return stringWriter.toString()
        }

        override fun convertToEntityAttribute(dbData: String?): Properties {
                val props = Properties()
                if (dbData != null) {
                        try {
                                props.load(StringReader(dbData))
                        } catch (e: IOException) {
                                throw IllegalArgumentException("Kan ikke lese properties til string:$props", e) //$NON-NLS-1$
                        }
                }
                return props
        }
}