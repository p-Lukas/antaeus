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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.math.BigDecimal

import kotlin.random.Random


class BillingServiceTest {


    //payment provider - charge
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

    private fun getPaymentProviderAlwaysTrue(): PaymentProvider {
        return object : PaymentProvider {
            override fun charge(invoice: Invoice): Boolean {
                return true
            }
        }
    }

    private fun getPaymentProviderThrowExceptions(): PaymentProvider {
        return object : PaymentProvider {
            private var count = 0
            override fun charge(invoice: Invoice): Boolean {
                count += 1
                when (count) {
                    1 -> throw CustomerNotFoundException(invoice.customerId)
                    2 -> throw CurrencyMismatchException(invoice.id, invoice.customerId)
                    else -> {
                        throw NetworkException()
                    }
                }
            }
        }
    }

    fun genFetchPending(nrOfInvoiceStatus: Int) = runBlocking{
        return@runBlocking (1..nrOfInvoiceStatus).asFlow()
            .transform { i -> emit(Invoice(i, i, Money(BigDecimal.valueOf(1), Currency.EUR), InvoiceStatus.PENDING)) }
            .toList()
    }

    @Test
    fun `test billing happy path`(){
        val invoiceService = mockk<InvoiceService>()
        val invoices = genFetchPending(100)

        every { invoiceService.fetchPending() } returns invoices
        every { invoiceService.updateStatus(any()) } returns 1

        val billService = BillingService(getPaymentProviderAlwaysTrue(), invoiceService)
        val scheduler = BillingScheduler(billService)

        billService.setScheduler(scheduler)
        billService.run()

        for (i in invoices){
            assertEquals(InvoiceStatus.PAID, i.status)
        }
    }

    @Test
    fun `test exception handling`(){
        val invoiceService = mockk<InvoiceService>()
        val invoices = genFetchPending(3)

        every { invoiceService.fetchPending() } returns invoices
        every { invoiceService.updateStatus(any()) } returns 1
        val billService = BillingService(getPaymentProviderThrowExceptions(), invoiceService)
        val scheduler = BillingScheduler(billService)
        billService.setScheduler(scheduler)

        billService.run()

        assertEquals(InvoiceStatus.FAILED_NO_CUSTOMER, invoices[0].status)
        assertEquals(InvoiceStatus.FAILED_CURRENCY, invoices[1].status)
        assertEquals(InvoiceStatus.TO_RETRY, invoices[2].status)
    }

    @Test
    fun `load test billing`(){
        val invoiceService = mockk<InvoiceService>()
        val invoices = genFetchPending(500_000)

        every { invoiceService.fetchPending() } returns invoices
        every { invoiceService.updateStatus(any()) } returns 1

        val billService = BillingService(getPaymentProvider(), invoiceService)
        val scheduler = BillingScheduler(billService)
        billService.setScheduler(scheduler)

        billService.run()

        for (i in invoices){
            assertFalse(i.status != InvoiceStatus.PENDING)
        }
    }


}