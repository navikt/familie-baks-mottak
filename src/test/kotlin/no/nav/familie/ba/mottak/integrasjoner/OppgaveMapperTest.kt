package no.nav.familie.ba.mottak.integrasjoner

import no.nav.familie.ba.mottak.config.ApplicationConfig
import no.nav.familie.ba.mottak.config.ClientMocks
import no.nav.familie.kontrakter.felles.oppgave.Behandlingstema
import no.nav.familie.kontrakter.felles.oppgave.Oppgavetype
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

@SpringBootTest(classes = [ApplicationConfig::class], properties = ["FAMILIE_INTEGRASJONER_API_URL=http://localhost:28085/api"])
@ActiveProfiles("dev")
class OppgaveMapperTest(

        @Autowired
        @Qualifier("aktørClient")
        private val aktørClient: AktørClient,

        @Autowired
        private val journalpostClient: JournalpostClient

) {

    @Test
    fun `skal kaste exception dersom dokumentlisten er tom`() {
        val oppgaveMapper = OppgaveMapper(aktørClient)
        Assertions.assertThrows(IllegalStateException::class.java) {
            oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring,
                                               journalpostClient.hentJournalpost("123")
                                                       .copy(dokumenter = listOf())
            )
        }
    }

    @Test
    fun `skal kaste exception dersom brukerid ikke er satt`() {
        val oppgaveMapper = OppgaveMapper(aktørClient)
        Assertions.assertThrows(IllegalStateException::class.java) {
            oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring,
                                               journalpostClient.hentJournalpost("123")
                                                       .copy(dokumenter = listOf(DokumentInfo(
                                                               tittel = null,
                                                               brevkode = "",
                                                               dokumentstatus = null,
                                                               dokumentvarianter = null)),
                                                             bruker = null)
            )
        }
    }

    @Test
    fun `skal sette behandlingstema Ordinær`() {
        val oppgaveMapper = OppgaveMapper(aktørClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring,
                                                                journalpostClient.hentJournalpost("123")
                                                                        .copy(dokumenter = listOf(DokumentInfo(
                                                                                tittel = null,
                                                                                brevkode = "NAV 33-00.07",
                                                                                dokumentstatus = null,
                                                                                dokumentvarianter = null))
                                                                        )
        )
        assertEquals(Behandlingstema.OrdinærBarnetrygd.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema Utvidet`() {
        val oppgaveMapper = OppgaveMapper(aktørClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring,
                                                         journalpostClient.hentJournalpost("123")
                                                                 .copy(dokumenter = listOf(DokumentInfo(
                                                                         tittel = null,
                                                                         brevkode = "NAV 33-00.09",
                                                                         dokumentstatus = null,
                                                                         dokumentvarianter = null))
                                                                 )
        )
        assertEquals(Behandlingstema.UtvidetBarnetrygd.value, oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema fra journalpost`() {
        val oppgaveMapper = OppgaveMapper(aktørClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring,
                                                         journalpostClient.hentJournalpost("123")
                                                                 .copy(dokumenter = listOf(DokumentInfo(
                                                                         tittel = null,
                                                                         brevkode = null,
                                                                         dokumentstatus = null,
                                                                         dokumentvarianter = null)),
                                                                       behandlingstema = "btema"
                                                                 )
        )
        assertEquals("btema", oppgave.behandlingstema)
    }

    @Test
    fun `skal sette behandlingstema EØS`() {
        val oppgaveMapper = OppgaveMapper(aktørClient)
        val oppgave = oppgaveMapper.mapTilOpprettOppgave(Oppgavetype.Journalføring,
                                                         journalpostClient.hentJournalpost("123")
                                                                 .copy(dokumenter = listOf(DokumentInfo(
                                                                         tittel = null,
                                                                         brevkode = null,
                                                                         dokumentstatus = null,
                                                                         dokumentvarianter = null)),
                                                                       bruker = Bruker(id = "42345678910", type = BrukerIdType.ORGNR)
                                                                 )
        )
        assertEquals(Behandlingstema.BarnetrygdEØS.value, oppgave.behandlingstema)
    }
}