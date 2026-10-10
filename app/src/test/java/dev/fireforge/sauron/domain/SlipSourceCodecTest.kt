package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class SlipSourceCodecTest {

    @Test
    fun `bank notification round-trips`() {
        val source = SlipSource.BankNotification(BankId.SCB, Instant.ofEpochMilli(1_700_000_000_000))
        assertEquals(source, SlipSourceCodec.decode(SlipSourceCodec.encode(source)))
    }

    @Test
    fun `screen capture round-trips`() {
        val source = SlipSource.ScreenCapture(
            "content://media/exports/slips/42",
            Instant.ofEpochMilli(1_700_000_000_001),
        )
        assertEquals(source, SlipSourceCodec.decode(SlipSourceCodec.encode(source)))
    }

    @Test
    fun `screen capture survives a pipe in the uri`() {
        val source = SlipSource.ScreenCapture(
            "content://weird/uri|with|pipes",
            Instant.ofEpochMilli(1_700_000_000_002),
        )
        assertEquals(source, SlipSourceCodec.decode(SlipSourceCodec.encode(source)))
    }

    @Test
    fun `manual entry round-trips`() {
        assertEquals(SlipSource.ManualEntry, SlipSourceCodec.decode(SlipSourceCodec.encode(SlipSource.ManualEntry)))
    }

    @Test
    fun `decode rejects an unknown form`() {
        assertThrows(IllegalArgumentException::class.java) {
            SlipSourceCodec.decode("CARRIER_PIGEON|now")
        }
    }
}
