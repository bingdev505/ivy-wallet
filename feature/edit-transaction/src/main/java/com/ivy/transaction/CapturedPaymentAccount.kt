package com.ivy.transaction

import com.ivy.data.model.SharedBill
import com.ivy.legacy.datamodel.Account
import java.util.UUID

internal fun capturedPaymentAccount(
    accounts: List<Account>,
    currency: String,
    baseCurrency: String,
    preferredId: UUID?,
): Account? {
    val matching = accounts.filter {
        (it.currency ?: baseCurrency).equals(currency, ignoreCase = true) &&
            it.id != SharedBill.clearingAccount(currency).id
    }
    return matching.firstOrNull { it.id == preferredId } ?: matching.firstOrNull()
}
