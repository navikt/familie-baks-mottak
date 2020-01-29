package no.nav.familie.ba.mottak.domene

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "HENDELSESLOGG")
data class Hendelseslogg(
        @Column(name = "kafka_offset")
        val offset: Long,

        @Column(name = "hendelse_id")
        val hendelseId: String,

        @Column(name = "aktor_id")
        val aktørid: String,

        @Column(name = "opplysningstype")
        val opplysningstype: String,

        @Column(name = "endringstype")
        val endringstype: String,

        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hendelseslogg_seq")
        @SequenceGenerator(name = "hendelseslogg_seq")
        val id: Long? = null,

        @Column(name = "opprettet_tid", nullable = false, updatable = false)
        val opprettetTidspunkt: LocalDateTime = LocalDateTime.now()
)
