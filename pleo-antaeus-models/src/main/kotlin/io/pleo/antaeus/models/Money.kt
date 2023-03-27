package io.pleo.antaeus.models

import java.math.BigDecimal

data class Money(
    var value: BigDecimal,
    var currency: Currency
)
