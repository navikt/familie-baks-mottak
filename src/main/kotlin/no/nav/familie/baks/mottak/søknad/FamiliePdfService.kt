package no.nav.familie.baks.mottak.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.familie.baks.mottak.domene.FeltMap
import no.nav.familie.baks.mottak.domene.VerdilisteElement
import no.nav.familie.baks.mottak.søknad.barnetrygd.domene.DBBarnetrygdSøknad
import no.nav.familie.baks.mottak.søknad.kontantstøtte.domene.DBKontantstøtteSøknad
import no.nav.familie.kontrakter.ba.søknad.v1.SIVILSTANDTYPE
import no.nav.familie.kontrakter.ba.søknad.v1.SøknadAdresse
import no.nav.familie.kontrakter.ba.søknad.v8.Søker
import no.nav.familie.kontrakter.ba.søknad.v9.BarnetrygdSøknad
import no.nav.familie.kontrakter.felles.Fødselsnummer
import no.nav.familie.kontrakter.felles.objectMapper
import no.nav.familie.kontrakter.felles.søknad.Søknadsfelt
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

@Service
class FamiliePdfService(
    private val familiePdfClient: FamiliePdfClient,
    private val søknadSpråkvelgerService: SøknadSpråkvelgerService,
) {
    fun lagBarnetrygdPdfKvittering(
        søknad: DBBarnetrygdSøknad,
        språk: String,
    ): ByteArray {
        // TODO mangler vedleggstitler?

        val feltmap = lagBarnetrygdFeltMap(søknad, språk)

        return familiePdfClient.opprettPdf(feltmap)
    }

    private fun lagBarnetrygdFeltMap(
        søknad: DBBarnetrygdSøknad,
        språk: String,
    ): FeltMap {
        val barnetrygdSøknad = objectMapper.readValue<BarnetrygdSøknad>(søknad.søknadJson)
        val barnetrygdSøknadOversatt = mapTilBarnetrygd(barnetrygdSøknad, språk)
        val feltMap = finnFelter(barnetrygdSøknadOversatt)
        println(feltMap)
        // Placeholderkode v
        val feltmap = FeltMap("", emptyList())
        return feltmap
    }

    private val endNodes =
        setOf<KClass<*>>(
            String::class,
            Int::class,
            Boolean::class,
            Double::class,
//            Dokumentasjon::class,
            Fødselsnummer::class,
//            MånedÅrPeriode::class,
//            Datoperiode::class,
//            Adresse::class,
            LocalDate::class,
            LocalDateTime::class,
            Month::class,
            Long::class,
        )

    fun finnFelter(entitet: Any): List<VerdilisteElement> {
        val parametere = konstruktørparametere(entitet)
        val list =
            parametere
                .asSequence()
                .map { finnSøknadsfelt(entitet, it) }
                .filter { it.visibility == KVisibility.PUBLIC }
                .mapNotNull { getFeltverdi(it, entitet) }
                .map { finnFelter(it) } // Kall rekursivt videre
                .flatten()
                .toList()

        // print("listeeeeeee1" + list + " listeeeeeee2")

        /*if (entitet is LabelVerdiPar<*>) {
            return listOf(VerdilisteElement(label = ""))
        }*/
    }

   /* private fun mapTilVerdiListeElement(
        entitet: Søknadsfelt<*>,
        språk: String,
    ) = VerdilisteElement(
        entitet.label,
        verdi = mapVerdi(entitet.verdi!!, språk),
        alternativer = entitet.alternativer?.joinToString(" / "),
    )*/

    private fun konstruktørparametere(entity: Any) = entity::class.primaryConstructor?.parameters ?: emptyList()

    private fun getFeltverdi(
        felt: KProperty1<out Any, Any?>,
        entitet: Any,
    ) = felt.getter.call(entitet)

    private fun finnSøknadsfelt(
        entity: Any,
        konstruktørparameter: KParameter,
    ) = entity::class.declaredMemberProperties.first { it.name == konstruktørparameter.name }

    fun mapTilBarnetrygd(
        søknad: BarnetrygdSøknad,
        språk: String,
    ): StrukturertBarnetrygdSøknad = StrukturertBarnetrygdSøknad(omDeg = mapTilOmDegSeksjon(søknad.søker, språk))

    fun mapTilOmDegSeksjon(
        søker: Søker,
        språk: String,
    ): OmDegSeksjon = OmDegSeksjon(ident = søker.ident.tilSpråk(språk), søker.statsborgerskap.tilSpråk(språk), søker.sivilstand.tilSpråkSivilstand(språk), søker.adresse.tilSpråk(språk))

    data class StrukturertBarnetrygdSøknad(
        val omDeg: OmDegSeksjon? = null,
    )

    data class OmDegSeksjon(
        val ident: LabelVerdiPar,
        val statsborgerskap: LabelVerdiParListe,
        val sivilstatus: LabelVerdiPar,
        val adresse: LabelVerdiParAdresse,
    )

    fun Søknadsfelt<String>.tilSpråk(språk: String): LabelVerdiPar = LabelVerdiPar(this.label[språk], this.verdi[språk])

    fun Søknadsfelt<List<String>>.tilSpråk(språk: String): LabelVerdiParListe = LabelVerdiParListe(this.label[språk], this.verdi[språk])

    fun Søknadsfelt<SIVILSTANDTYPE>.tilSpråkSivilstand(språk: String): LabelVerdiPar = LabelVerdiPar(this.label[språk], this.verdi[språk].toString()) // TODO hjelpefunksjon som gjør enumen brukervennlig

    fun Søknadsfelt<SøknadAdresse?>.tilSpråk(språk: String): LabelVerdiParAdresse = LabelVerdiParAdresse(this.label[språk], this.verdi[språk])

    data class LabelVerdiPar(
        val label: String?,
        val verdi: String?,
    )

    data class LabelVerdiParListe(
        val label: String?,
        val verdi: List<String>?,
    )

    data class LabelVerdiParAdresse(
        val label: String?,
        val verdi: SøknadAdresse?,
    )

/*  Generisk type som kan brukes for de forksjellige labelverdiparene. Litt usikker på om det funker så bra.
  sealed class LabelVerdiPar<T>(
        open val label: String?,
        open val verdi: T?,
    )

    data class LabelVerdiParString(
        override val label: String?,
        override val verdi: String?,
    ) : LabelVerdiPar<String>(label, verdi)

    data class LabelVerdiParListe(
        override val label: String?,
        override val verdi: List<String>?,
    ) : LabelVerdiPar<List<String>>(label, verdi)

    data class LabelVerdiParAdresse(
        override val label: String?,
        override val verdi: SøknadAdresse?,
    ) : LabelVerdiPar<SøknadAdresse>(label, verdi)*/

    fun lagKontantstøttePdfKvittering(
        søknad: DBKontantstøtteSøknad,
        språk: String,
    ): ByteArray {
        // TODO mangler vedleggstitler?
        val feltmap = lagKontantstøtteFeltMap(søknad)

        return familiePdfClient.opprettPdf(feltmap)
    }

    private fun lagKontantstøtteFeltMap(søknad: DBKontantstøtteSøknad): FeltMap = FeltMap("", emptyList())
}
