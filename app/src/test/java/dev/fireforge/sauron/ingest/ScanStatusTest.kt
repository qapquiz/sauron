package dev.fireforge.sauron.ingest

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanStatusTest {

    @Test
    fun `periodic enqueued alone stays idle`() {
        assertEquals(ScanStatus.Idle, scanStatus(emptyList(), listOf(WorkInfo.State.ENQUEUED), 0))
    }

    @Test
    fun `one time enqueued is working`() {
        assertEquals(ScanStatus.Working, scanStatus(listOf(WorkInfo.State.ENQUEUED), emptyList(), 0))
    }

    @Test
    fun `periodic running is working`() {
        assertEquals(ScanStatus.Working, scanStatus(emptyList(), listOf(WorkInfo.State.RUNNING), 0))
    }

    @Test
    fun `parked two with idle runs is parked two`() {
        assertEquals(
            ScanStatus.Parked(2),
            scanStatus(listOf(WorkInfo.State.SUCCEEDED), listOf(WorkInfo.State.ENQUEUED), 2),
        )
    }

    @Test
    fun `parked zero is idle`() {
        assertEquals(ScanStatus.Idle, scanStatus(emptyList(), emptyList(), 0))
    }

    @Test
    fun `working wins over parked`() {
        assertEquals(ScanStatus.Working, scanStatus(listOf(WorkInfo.State.RUNNING), emptyList(), 5))
    }
}
