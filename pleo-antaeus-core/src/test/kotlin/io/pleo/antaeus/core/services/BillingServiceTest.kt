package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.scheduler.BillingScheduler
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal

import kotlin.random.Random


class BillingServiceTest {


    //payment provicer - charge
    private fun getPaymentProvider(): PaymentProvider {
        return object : PaymentProvider {
            override fun charge(invoice: Invoice): Boolean {
                val err_rate = Random.nextInt(100)
                if(95 < err_rate){
                    throw NetworkException()
                }else if (92 < err_rate){
                    throw CustomerNotFoundException(invoice.customerId)
                }else if (89 < err_rate){
                    throw CurrencyMismatchException(invoice.id, invoice.customerId)
                }else if (85 < err_rate){
                    return false
                }
                return true
            }
        }
    }

    fun genFetchPending() = runBlocking{
        return@runBlocking (1..10_000).asFlow()
            .transform { i -> emit(Invoice(i, i, Money(BigDecimal.valueOf(1), Currency.EUR), InvoiceStatus.PENDING)) }
            .toList()
    }

    @Test
    fun test_schedule(){
        val invoiceService = mockk<InvoiceService>()

        every { invoiceService.fetchPending() } returns genFetchPending()
        every { invoiceService.updateStatus(any()) } returns 1

        val billService = BillingService(getPaymentProvider(), invoiceService)
        val scheduler = BillingScheduler(billService)

        billService.setScheduler(scheduler)
        billService.run()
    }


}