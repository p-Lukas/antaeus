package io.pleo.antaeus.core.scheduler

import io.pleo.antaeus.core.services.BillingService
import mu.KotlinLogging
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

const val TIMEZONE = "Europe/Berlin"    //TODO: move into config file and/or make set automatically
private val logger = KotlinLogging.logger {}

class BillingScheduler(private val billingService: BillingService?) {

    fun scheduleExecution(){
        val executionDay = LocalDateTime.now().withDayOfMonth(1)
            .plusMonths(1).withHour(1).withMinute(0).withSecond(0).withNano(0).atZone(ZoneId.of(TIMEZONE))
        val date : Date = Date.from(Instant.from(executionDay))

        //Schedule with period not used, because time between two firsts of the months is not consistent
        Timer("BillingService-$date", true).schedule(billingService, date)
        logger.info { "The next planned BillingService execution is on the $date." }
    }
}