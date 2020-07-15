package no.nav.familie.ba.mottak.repository.domain

import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "soknad")
data class Soknad(@Id
                  val id: UUID = UUID.randomUUID(),
                  @Column(name = "soknad_json")
                  val søknadJson: String,
                  val fnr: String,
                  @Column(name = "task_opprettet")
                  val taskOpprettet: Boolean = false,
                  @Column(name = "opprettet_tid")
                  val opprettetTid: LocalDateTime = LocalDateTime.now())
