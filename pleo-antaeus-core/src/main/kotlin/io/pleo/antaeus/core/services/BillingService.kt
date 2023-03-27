package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.scheduler.BillingScheduler
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import java.util.*

// All of these values could/should be moved to a config file and adjusted depending on the PaymentProvider system
const val MAX_RETRIES = 3
const val THROTTLE_MULTIPLIER = 7L
const val MAX_THROTTLE = 5L

private val logger = KotlinLogging.logger {}

/**
 * Billing Service:
 *
 * With the assumptions 2, 3 and 4 in mind, when getting an NetworkException, throttling kicks in, in form of reduction in requests per second.
 */
class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService
) : TimerTask() {

    //Not atomic even though it can be used by multiple coroutines at the same time.
    //If the value is slightly above or below target values or a value that is not a 100% up to date,
    // it will not influence the result in a significant way...
    private var networkThrottle = 0L
    private var failedInvoices = 0L

    private lateinit var scheduler: BillingScheduler

    fun setScheduler(schedulerService: BillingScheduler){
        scheduler  = schedulerService
    }

    override fun run(){
        processInvoices(invoiceService.fetchPending())  //with large data-sets this should be pulled and processed in batches
        scheduler.scheduleExecution()   //Schedules the next execution
    }

    fun processRetryInvoices(){
        processInvoices(invoiceService.fetchRetry()) //with large data-sets this should be pulled and processed in batches
    }

    private fun processInvoices(invoices: List<Invoice>) = runBlocking{

        logger.info { "Started processing ${invoices.size} of invoices." }
        for (invoice in invoices){
            // The IO bound Dispatcher was chosen, because sending out the payment request and updating the DB entry are IO bound.
            launch(Dispatchers.IO){
                processSingleInvoice(invoice)

                //In cases of failures between these two functions. Some Invoices could be processed double.
                // To avoid this, the recovery mechanism would need to check bank transactions of customers who have
                // pending invoices and update the invoices accordingly.
                invoiceService.updateStatus(invoice)
            }
            delay(networkThrottle * THROTTLE_MULTIPLIER)
        }

        logger.info { "Finished processing Invoices." +
                "Number of Invoices failed: $failedInvoices ." }
    }

    /**
     * IncreaseThrottle and decreaseThrottle could be more sophisticated and
     * use methodologies borrowed from Networking protocols like TCP.
     */
    private fun increaseThrottle(){
        if (networkThrottle < MAX_THROTTLE){
            networkThrottle += 1
        }
    }

    private fun decreaseThrottle(){
        if (0 < networkThrottle){
            networkThrottle -= 1
        }
    }

    private suspend fun processSingleInvoice(invoice: Invoice, retriesLeft: Int = MAX_RETRIES){
        try {
            if (paymentProvider.charge(invoice)){
                invoice.status = InvoiceStatus.PAID
            }else{
                invoice.status = InvoiceStatus.FAILED_NO_BALANCE
                failedInvoices += 1
            }
            // It is called here, assuming that after success, the PaymentProvider had time to scale.
            // If decreaseThrottle is never called we most likely will use maximum delay throughout the whole process.
            decreaseThrottle()
        }catch (e: NetworkException){
            /**
             * Even though NetworkException is very general and can have multifaceted reasons; connection refused,
             * timed out, addr. unreachable, etc.
             * Waiting and retrying, resolves most issues...
             */
            increaseThrottle()
            if (0 < retriesLeft){
                delay(networkThrottle * THROTTLE_MULTIPLIER)
                processSingleInvoice(invoice, retriesLeft - 1)
            }else{
                invoice.status = InvoiceStatus.TO_RETRY
                failedInvoices += 1
            }
        }catch (e: CurrencyMismatchException){
            invoice.status = InvoiceStatus.FAILED_CURRENCY
            logger.error { "Currency mismatch on invoice with id: ${invoice.id} and currency: ${invoice.amount.currency} " +
                    "for customer with id: ${invoice.customerId}" }
            failedInvoices += 1
        }catch (e: CustomerNotFoundException){
            invoice.status = InvoiceStatus.FAILED_NO_CUSTOMER
            logger.error { "Customer with id: ${invoice.customerId} not found." }
            failedInvoices += 1
        }
    }
}
