package no.nav.familie.ba.mottak.integrasjoner

import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.familie.ba.mottak.DevLauncher
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import java.io.IOException


@SpringBootTest(classes = [DevLauncher::class], properties = ["FAMILIE_BA_INFOTRYGD_BARNETRYGD_API_URL=http://localhost:28085"])
@ActiveProfiles("dev", "mock-oauth")
@AutoConfigureWireMock(port = 28085)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InfotrygdBarnetrygdClientTest {

    @Autowired
    lateinit var infotrygdClient: InfotrygdBarnetrygdClient

    @Test
    @Tag("integration")
    fun `hentSaker skal returnere SakDto`() {
        stubFor(post(urlEqualTo("/infotrygd/barnetrygd/saker"))
            .withRequestBody(equalToJson("{\n" +
                                         "  \"brukere\": [\"20086600000\"],\n" +
                                         "  \"barn\": [\"31038600000\"]\n" +
                                         "}"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(gyldigSakResponse())))

        val sakDto = infotrygdClient.hentSaker(brukersIdenter, barnasIdenter).bruker.first()
    }

    @Test
    @Tag("integration")
    fun `hentLøpendeUtbetalinger skal returnere StønadDto`() {
        stubFor(post(urlEqualTo("/infotrygd/barnetrygd/stonad"))
                        .withRequestBody(equalToJson("{\n" +
                                                     "  \"brukere\": [\"20086600000\"],\n" +
                                                     "  \"barn\": [\"31038600000\"]\n" +
                                                     "}"))
                        .willReturn(aResponse()
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(gyldigStønadResponse())))

        val stønadDto = infotrygdClient.hentLøpendeUtbetalinger(brukersIdenter, barnasIdenter).bruker.first()
    }


    @Throws(IOException::class) private fun gyldigStønadResponse(): String {
        return "{\n" +
               "  \"bruker\": [\n" +
               "    {\n" +
               "      \"stønadId\": 327086,\n" +
               "      \"sakNr\": \"02\",\n" +
               "      \"saksblokk\": \"A\",\n" +
               "      \"fNr\": \"20086600000\",\n" +
               "      \"tkNr\": \"0315\",\n" +
               "      \"region\": \"2\",\n" +
               "      \"opphørtFom\": \"000000\",\n" +
               "      \"opphørsgrunn\": \" \"\n" +
               "    }\n" +
               "  ],\n" +
               "  \"barn\": []\n" +
               "}"
    }

    @Throws(IOException::class) private fun gyldigSakResponse(): String {
        return "{\n" +
               "  \"bruker\": [\n" +
               "    {\n" +
               "      \"s01Personkey\": 31566082000000,\n" +
               "      \"s05Saksblokk\": \"A\",\n" +
               "      \"saksnr\": \"01\",\n" +
               "      \"regDato\": \"2020-09-07\",\n" +
               "      \"mottattdato\": \"2020-09-07\",\n" +
               "      \"kapittelnr\": \"BA\",\n" +
               "      \"valg\": \"OR\",\n" +
               "      \"DublettFeil\": null,\n" +
               "      \"Type\": \"S\",\n" +
               "      \"Innstilling\": null,\n" +
               "      \"Resultat\": \"I\",\n" +
               "      \"Vedtaksdato\": \"2020-09-07\",\n" +
               "      \"vedtak\": {\n" +
               "        \"stønadId\": 293587,\n" +
               "        \"sakNr\": \"01\",\n" +
               "        \"saksblokk\": \"A\",\n" +
               "        \"fNr\": \"20086600000\",\n" +
               "        \"tkNr\": \"0315\",\n" +
               "        \"region\": \"2\",\n" +
               "        \"opphørtFom\": \"000000\",\n" +
               "        \"opphørsgrunn\": \"0\"\n" +
               "      },\n" +
               "      \"Nivaa\": \"TK \",\n" +
               "      \"Innstilldato\": null,\n" +
               "      \"Iverksattdato\": \"2020-01-01\",\n" +
               "      \"GrunnblDato\": \"2020-09-07\",\n" +
               "      \"Aarsakskode\": \"00\",\n" +
               "      \"Tellepunkt\": \"420\",\n" +
               "      \"Telletype\": \"D\",\n" +
               "      \"Telledato\": \"2020-09-07\",\n" +
               "      \"EvalKode\": \"EVEO\",\n" +
               "      \"EvalTir\": \"R\",\n" +
               "      \"Fremlegg\": \"\\u0000\\u0000\\u0000\",\n" +
               "      \"Innstilling2\": \"  \",\n" +
               "      \"Innstilldato2\": null,\n" +
               "      \"AnnenInstans\": \"O\",\n" +
               "      \"BehenType\": \"NFE\",\n" +
               "      \"BehenEnhet\": \"4833\",\n" +
               "      \"RegAvType\": \"NFE\",\n" +
               "      \"RegAvEnhet\": \"4833\",\n" +
               "      \"DiffFramlegg\": \"000\",\n" +
               "      \"InnstilltAvType\": \"   \",\n" +
               "      \"InnstilltAvEnhet\": \"0000\",\n" +
               "      \"VedtattAvType\": \"NFE\",\n" +
               "      \"VedtattAvEnhet\": \"4833\",\n" +
               "      \"PrioTab\": \"      \",\n" +
               "      \"Aoe\": \"   \",\n" +
               "      \"EsSystem\": \" \",\n" +
               "      \"EsGsakOppdragsid\": 0,\n" +
               "      \"KnyttetTilSak\": \"00\",\n" +
               "      \"Vedtakstype\": \"V\",\n" +
               "      \"ReellEnhet\": \"4833\",\n" +
               "      \"ModEndret\": \" \",\n" +
               "      \"tkNr\": \"0315\",\n" +
               "      \"fNr\": \"20086600000\",\n" +
               "      \"kildeIs\": \"RG02\",\n" +
               "      \"region\": \"2\",\n" +
               "      \"sakId\": 20582481,\n" +
               "      \"status\": \"FB\"\n" +
               "    }\n" +
               "  ]\n," +
               "  \"barn\": [\n" +
               "    {\n" +
               "      \"s01Personkey\": 31586033100000,\n" +
               "      \"s05Saksblokk\": \"A\",\n" +
               "      \"saksnr\": \"02\",\n" +
               "      \"regDato\": \"2020-09-07\",\n" +
               "      \"mottattdato\": \"2020-09-07\",\n" +
               "      \"kapittelnr\": \"BA\",\n" +
               "      \"valg\": \"OR\",\n" +
               "      \"DublettFeil\": null,\n" +
               "      \"Type\": \"R\",\n" +
               "      \"Innstilling\": null,\n" +
               "      \"Resultat\": null,\n" +
               "      \"Vedtaksdato\": null,\n" +
               "      \"vedtak\": null,\n" +
               "      \"Nivaa\": \"   \",\n" +
               "      \"Innstilldato\": null,\n" +
               "      \"Iverksattdato\": null,\n" +
               "      \"GrunnblDato\": null,\n" +
               "      \"Aarsakskode\": \"00\",\n" +
               "      \"Tellepunkt\": \"000\",\n" +
               "      \"Telletype\": \" \",\n" +
               "      \"Telledato\": null,\n" +
               "      \"EvalKode\": \"    \",\n" +
               "      \"EvalTir\": \" \",\n" +
               "      \"Fremlegg\": \"\\u0000\\u0000\\u0000\",\n" +
               "      \"Innstilling2\": \"  \",\n" +
               "      \"Innstilldato2\": null,\n" +
               "      \"AnnenInstans\": \"O\",\n" +
               "      \"BehenType\": \"NFE\",\n" +
               "      \"BehenEnhet\": \"4833\",\n" +
               "      \"RegAvType\": \"NFE\",\n" +
               "      \"RegAvEnhet\": \"4833\",\n" +
               "      \"DiffFramlegg\": \"000\",\n" +
               "      \"InnstilltAvType\": \"   \",\n" +
               "      \"InnstilltAvEnhet\": \"0000\",\n" +
               "      \"VedtattAvType\": \"   \",\n" +
               "      \"VedtattAvEnhet\": \"0000\",\n" +
               "      \"PrioTab\": \"      \",\n" +
               "      \"Aoe\": \"   \",\n" +
               "      \"EsSystem\": \" \",\n" +
               "      \"EsGsakOppdragsid\": 0,\n" +
               "      \"KnyttetTilSak\": \"00\",\n" +
               "      \"Vedtakstype\": \" \",\n" +
               "      \"ReellEnhet\": \"4833\",\n" +
               "      \"ModEndret\": \" \",\n" +
               "      \"tkNr\": \"0315\",\n" +
               "      \"fNr\": \"31038600000\",\n" +
               "      \"kildeIs\": \"RG02\",\n" +
               "      \"region\": \"2\",\n" +
               "      \"sakId\": 20909637,\n" +
               "      \"status\": \"UB\"\n" +
               "    }\n" +
               "  ]\n" +
               "}"
    }


    companion object {
        private val brukersIdenter = listOf("20086600000")
        private val barnasIdenter = listOf("31038600000")
    }
}