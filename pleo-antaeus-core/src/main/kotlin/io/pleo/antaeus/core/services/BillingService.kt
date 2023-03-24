package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

const val MAX_RETRIES = 3

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) {

    fun processInvoices(){
        processInvoices(invoiceService.fetchPending())
    }

    fun processRetryInvoices(){
        processInvoices(invoiceService.fetchRetry())
    }

    private fun processInvoices(invoices: List<Invoice>){

        for (invoice in invoices){
            processSingleInvoice(invoice)
            invoiceService.updateStatus(invoice)
        }
    }

    private fun processSingleInvoice(invoice: Invoice, retriesLeft: Int = MAX_RETRIES){
        try {
            if (paymentProvider.charge(invoice)){
                invoice.status = InvoiceStatus.PAID
            }else{
                invoice.status = InvoiceStatus.FAILED_NO_BALANCE
            }
        }catch (e: NetworkException){
            //TODO: throttle
            if (0 < retriesLeft){
                processSingleInvoice(invoice, retriesLeft - 1)
            }else{
                invoice.status = InvoiceStatus.RETRY
            }
        }catch (e: CurrencyMismatchException){
            invoice.status = InvoiceStatus.FAILED_CURRENCY
        }catch (e: CustomerNotFoundException){
            invoice.status = InvoiceStatus.FAILED_NO_CUSTOMER
        }
    }
}
