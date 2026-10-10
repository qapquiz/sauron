package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThaiBanksTest {

    @Test
    fun `known notification packages map to their bank`() {
        assertEquals(BankId.SCB, ThaiBanks.fromNotificationPackage("th.co.scb"))
        assertEquals(BankId.KBANK, ThaiBanks.fromNotificationPackage("com.kasikorn.retailapplication"))
    }

    @Test
    fun `unknown notification package maps to null`() {
        assertNull(ThaiBanks.fromNotificationPackage("com.example.mysterybank"))
    }
}
