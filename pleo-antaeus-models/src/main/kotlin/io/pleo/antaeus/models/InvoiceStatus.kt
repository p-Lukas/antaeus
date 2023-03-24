package io.pleo.antaeus.models

enum class InvoiceStatus {
    PENDING,
    PAID,
    FAILED_NO_BALANCE,
    FAILED_CURRENCY,
    FAILED_NO_CUSTOMER,
    RETRY,
    FAILED
}
