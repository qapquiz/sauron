package dev.fireforge.sauron.domain

object ThaiBanks {

    // Package names verified against store listings; the rest land in phase 2.
    private val byPackage = mapOf(
        "th.co.scb" to BankId.SCB,
        "com.kasikorn.retailapplication" to BankId.KBANK,
    )

    fun fromNotificationPackage(pkg: String): BankId? = byPackage[pkg]
}
