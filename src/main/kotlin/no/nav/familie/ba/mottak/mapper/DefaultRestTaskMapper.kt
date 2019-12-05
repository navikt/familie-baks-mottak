package no.nav.familie.ba.mottak.mapper

import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.rest.RestTask
import no.nav.familie.prosessering.rest.RestTaskMapper
import org.springframework.stereotype.Service

@Service
class DefaultRestTaskMapper : RestTaskMapper {
    override fun toDto(task: Task): RestTask {
        return RestTask(task, null, null, "null") //TODO Må få inn verdier når databasemodellen er på plass
    }
}