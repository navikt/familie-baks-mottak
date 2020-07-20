package no.nav.familie.ba.mottak.søknad

import no.nav.familie.ba.mottak.søknad.domene.tilDBSøknad
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import main.kotlin.no.nav.familie.ba.søknad.Søknad
import no.nav.familie.ba.mottak.søknad.domene.DBSøknad
import no.nav.familie.ba.mottak.søknad.domene.FødselsnummerErNullException
import no.nav.familie.ba.mottak.task.JournalførSøknadTask
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.springframework.data.repository.findByIdOrNull
import java.util.*


@Service
class SøknadService(private val søknadRepository: SøknadRepository, private val taskRepository: TaskRepository) {

    @Transactional
    @Throws(FødselsnummerErNullException::class)
    fun motta(søknad: Søknad): DBSøknad {
        val dbSøknad = lagreDBSøknad(søknad.tilDBSøknad())
        val properties = Properties().apply { this["søkersFødselsnummer"] = dbSøknad.fnr }

        taskRepository.save(Task.nyTask(JournalførSøknadTask.JOURNALFØR_SØKNAD,
                                        dbSøknad.id.toString(),
                                        properties))
        return dbSøknad

    }

    fun get(id: String): DBSøknad {
        return søknadRepository.findByIdOrNull(id) ?: error("Ugyldig primærnøkkel")
    }

    fun lagreDBSøknad(dbSøknad: DBSøknad): DBSøknad {
        return søknadRepository.save(dbSøknad)
    }

    fun hentDBSøknad(søknadId: Long): DBSøknad? {
        return søknadRepository.hentDBSøknad(søknadId)
    }

}

