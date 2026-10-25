package dev.fireforge.sauron.ingest

import androidx.work.WorkInfo

sealed interface ScanStatus {
    data object Working : ScanStatus
    data object Idle : ScanStatus
    data class Parked(val count: Int) : ScanStatus
}

fun scanStatus(
    oneTime: List<WorkInfo.State>,
    periodic: List<WorkInfo.State>,
    parked: Int,
): ScanStatus = when {
    oneTime.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.RUNNING } ||
        periodic.any { it == WorkInfo.State.RUNNING } -> ScanStatus.Working
    parked > 0 -> ScanStatus.Parked(parked)
    else -> ScanStatus.Idle
}
