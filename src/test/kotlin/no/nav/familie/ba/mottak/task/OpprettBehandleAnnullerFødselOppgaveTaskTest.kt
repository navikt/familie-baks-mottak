package no.nav.familie.ba.mottak.task

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.familie.ba.mottak.domene.NyBehandling
import no.nav.familie.ba.mottak.hendelser.JournalføringHendelseServiceTest
import no.nav.familie.ba.mottak.integrasjoner.AktørClient
import no.nav.familie.ba.mottak.integrasjoner.BehandlingKategori
import no.nav.familie.ba.mottak.integrasjoner.BehandlingUnderkategori
import no.nav.familie.ba.mottak.integrasjoner.Bruker
import no.nav.familie.ba.mottak.integrasjoner.BrukerIdType
import no.nav.familie.ba.mottak.integrasjoner.DokumentInfo
import no.nav.familie.ba.mottak.integrasjoner.Dokumentstatus
import no.nav.familie.ba.mottak.integrasjoner.IntegrasjonException
import no.nav.familie.ba.mottak.integrasjoner.Journalpost
import no.nav.familie.ba.mottak.integrasjoner.JournalpostClient
import no.nav.familie.ba.mottak.integrasjoner.Journalposttype
import no.nav.familie.ba.mottak.integrasjoner.Journalstatus
import no.nav.familie.ba.mottak.integrasjoner.OppgaveClient
import no.nav.familie.ba.mottak.integrasjoner.RestArbeidsfordelingPåBehandling
import no.nav.familie.ba.mottak.integrasjoner.RestFagsak
import no.nav.familie.ba.mottak.integrasjoner.RestUtvidetBehandling
import no.nav.familie.ba.mottak.integrasjoner.SakClient
import no.nav.familie.kontrakter.felles.Behandlingstema
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstype
import no.nav.familie.kontrakter.felles.oppgave.IdentGruppe
import no.nav.familie.kontrakter.felles.oppgave.Oppgave
import no.nav.familie.kontrakter.felles.oppgave.OppgaveIdentV2
import no.nav.familie.kontrakter.felles.oppgave.OppgaveResponse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalStateException
import java.time.LocalDateTime
import java.util.Properties

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpprettBehandleAnnullerFødselOppgaveTaskTest {

    private val mockOppgaveClient: OppgaveClient = mockk()
    private val mockSakClient: SakClient = mockk()
    private val mockAktørClient: AktørClient = mockk()
    private val mockTaskRepository: TaskRepository = mockk(relaxed = true)

    private val taskStep = OpprettBehandleAnnullerFødselOppgaveTask(mockOppgaveClient, mockSakClient, mockAktørClient)

    @Test
    fun `Skal kaste feil hvis person har ikke aktør ID`() {
        every {
            mockSakClient.hentRestFagsak(any<String>())
        } returns lagRestFagsak()

        var identSlot = slot<String>()
        every {
            mockAktørClient.hentAktørId(capture(identSlot))
        } throws IntegrasjonException(msg = "Kan ikke finne aktørId for ident", ident = testIdent)

        Assertions.assertThatThrownBy {
            taskStep.doTask(
                Task.nyTask(
                    type = OpprettBehandleAnnullerFødselOppgaveTask.TASK_STEP_TYPE,
                    payload = jacksonObjectMapper().writeValueAsString(
                        NyBehandling(
                            morsIdent = testIdent,
                            barnasIdenter = arrayOf(testBarnIdent)
                        )
                    ),
                    properties = Properties()
                )
            )
        }.hasMessage("Kan ikke finne aktørId for ident")
        assertThat(identSlot.captured).isEqualTo(testIdent)
    }

    @Test
    fun `Skal kaste feil hvis feil ved henting Fagsak for person`() {
        every {
            mockSakClient.hentRestFagsak(any<String>())
        } throws IntegrasjonException("Feil ved henting av RestFagsak fra ba-sak.")

        Assertions.assertThatThrownBy {
            taskStep.doTask(
                Task.nyTask(
                    type = OpprettBehandleAnnullerFødselOppgaveTask.TASK_STEP_TYPE,
                    payload = jacksonObjectMapper().writeValueAsString(
                        NyBehandling(
                            morsIdent = testIdent,
                            barnasIdenter = arrayOf(testBarnIdent)
                        )
                    ),
                    properties = Properties()
                )
            )
        }.hasMessage("Feil ved henting av RestFagsak fra ba-sak.")
    }

    @Test
    fun `Skal kaste feil hvis Fagsak har ikke aktiv behandling`() {
        every {
            mockSakClient.hentRestFagsak(any<String>())
        } returns RestFagsak(
            id = 2,
            behandlinger = listOf(
                RestUtvidetBehandling(
                    aktiv = false,
                    arbeidsfordelingPåBehandling = RestArbeidsfordelingPåBehandling(testEnhetsId),
                    behandlingId = 2,
                    kategori = BehandlingKategori.NASJONAL,
                    opprettetTidspunkt = LocalDateTime.now(),
                    resultat = "RESULTAT",
                    steg = "STEG",
                    type = "TYPE",
                    underkategori = BehandlingUnderkategori.UTVIDET,
                ),
            )
        )

        assertThrows<IllegalStateException> {
            taskStep.doTask(
                Task.nyTask(
                    type = OpprettBehandleAnnullerFødselOppgaveTask.TASK_STEP_TYPE,
                    payload = jacksonObjectMapper().writeValueAsString(
                        NyBehandling(
                            morsIdent = testIdent,
                            barnasIdenter = arrayOf(testBarnIdent)
                        )
                    ),
                    properties = Properties()
                )
            )
        }
    }

    @Test
    fun `Skal opprett oppgave hvis person har Fagsak, aktiv Behandling, og Aktør ID`() {
        val fagsak = lagRestFagsak()
        val aktivBehandling = fagsak.behandlinger.find { it.aktiv }
        every {
            mockSakClient.hentRestFagsak(any<String>())
        } returns fagsak

        every {
            mockAktørClient.hentAktørId(any())
        } returns testAktørId

        val identSlot = slot<OppgaveIdentV2>()
        val saksIdSlot = slot<String>()
        val enhetsnummerSlot = slot<String>()
        val behandlingstemaSlot = slot<String>()
        val behandlingstypeSlot = slot<String>()
        val beskrivelseSlot = slot<String>()
        every {
            mockOppgaveClient.opprettBehandleAnnullerFødselOppgave(
                capture(identSlot), capture(saksIdSlot), capture(enhetsnummerSlot),
                capture(behandlingstemaSlot), capture(behandlingstypeSlot), capture(beskrivelseSlot)
            )
        } returns OppgaveResponse(1)

        taskStep.doTask(
            Task.nyTask(
                type = OpprettBehandleAnnullerFødselOppgaveTask.TASK_STEP_TYPE,
                payload = jacksonObjectMapper().writeValueAsString(
                    NyBehandling(
                        morsIdent = testIdent,
                        barnasIdenter = arrayOf(testBarnIdent)
                    )
                ),
                properties = Properties()
            )
        )

        assertThat(identSlot.captured.ident).isEqualTo(testAktørId)
        assertThat(identSlot.captured.gruppe).isEqualTo(IdentGruppe.AKTOERID)
        assertThat(saksIdSlot.captured).isEqualTo(fagsak.id.toString())
        assertThat(enhetsnummerSlot.captured).isEqualTo(aktivBehandling!!.arbeidsfordelingPåBehandling.behandlendeEnhetId)
        assertThat(behandlingstemaSlot.captured).isEqualTo(Behandlingstema.OrdinærBarnetrygd.toString())
        assertThat(behandlingstypeSlot.captured).isEqualTo(aktivBehandling!!.type)
        assertThat(beskrivelseSlot.captured).contains("annulert")
    }

    @Test
    fun `Skal ikke opprett oppgave hvis person har ikke Fagsak`() {
        every {
            mockSakClient.hentRestFagsak(any<String>())
        } returns null

        assertDoesNotThrow {
            taskStep.doTask(
                Task.nyTask(
                    type = OpprettBehandleAnnullerFødselOppgaveTask.TASK_STEP_TYPE,
                    payload = jacksonObjectMapper().writeValueAsString(
                        NyBehandling(
                            morsIdent = testIdent,
                            barnasIdenter = arrayOf(testBarnIdent)
                        )
                    ),
                    properties = Properties()
                )
            )
        }
    }

    internal fun lagRestFagsak() = RestFagsak(
        id = 1,
        behandlinger = listOf(
            RestUtvidetBehandling(
                aktiv = false,
                arbeidsfordelingPåBehandling = RestArbeidsfordelingPåBehandling(testEnhetsId),
                behandlingId = 2,
                kategori = BehandlingKategori.NASJONAL,
                opprettetTidspunkt = LocalDateTime.now(),
                resultat = "RESULTAT",
                steg = "STEG",
                type = Behandlingstype.EØS.toString(),
                underkategori = BehandlingUnderkategori.UTVIDET,
            ),
            RestUtvidetBehandling(
                aktiv = true,
                arbeidsfordelingPåBehandling = RestArbeidsfordelingPåBehandling(testEnhetsId),
                behandlingId = 3,
                kategori = BehandlingKategori.NASJONAL,
                opprettetTidspunkt = LocalDateTime.now(),
                resultat = "RESULTAT",
                steg = "STEG",
                type = Behandlingstype.EØS.toString(),
                underkategori = BehandlingUnderkategori.ORDINÆR,
            )
        )
    )

    companion object {

        val testIdent = "12345678910"
        val testBarnIdent = "10987654321"
        val testAktørId = "12345678911"
        val testEnhetsId = "0123"
    }
}