package dev.fireforge.sauron.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.fireforge.sauron.SauronApplication
import dev.fireforge.sauron.data.CategoryTotal
import dev.fireforge.sauron.data.ExpenseWithCategory
import dev.fireforge.sauron.domain.Currency
import dev.fireforge.sauron.domain.Money
import dev.fireforge.sauron.domain.MonthWindow
import dev.fireforge.sauron.ingest.ModelDownloadWorker
import dev.fireforge.sauron.ingest.ScanStatus
import dev.fireforge.sauron.ingest.SlipIngestWorker
import dev.fireforge.sauron.ingest.scanStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val Bangkok = ZoneOffset.ofHours(7)

private val ExpenseDateFormat = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)

private fun MonthHeaderLabel(from: Instant): String =
    DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH).format(from.atZone(Bangkok))

private fun formatOccurred(at: Instant): String = ExpenseDateFormat.format(at.atZone(Bangkok))

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SauronApplication).container
        val expenseDao = container.expenseDao
        val window = MonthWindow.current(Instant.now(), Bangkok)
        setContent {
            SauronTheme {
                val (hasImagePermission, requestImagePermission) = rememberImagePermission(applicationContext)
                val expenses by expenseDao.observeAll().collectAsState(initial = emptyList())
                val totals by expenseDao.totalByCategory(window.from, window.to).collectAsState(initial = emptyList())
                val workInfos by WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME)
                    .collectAsState(initial = emptyList())
                val downloadInfo = workInfos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                } ?: workInfos.lastOrNull()
                var modelReady by remember { mutableStateOf(container.gemmaSlipReader.hasModel()) }
                LaunchedEffect(downloadInfo?.state) {
                    if (downloadInfo?.state == WorkInfo.State.SUCCEEDED) {
                        modelReady = container.gemmaSlipReader.hasModel()
                    }
                }
                val downloadModel: () -> Unit = {
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        ModelDownloadWorker.UNIQUE_NAME,
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                            .setConstraints(Constraints(requiredNetworkType = NetworkType.UNMETERED))
                            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                            .build(),
                    )
                }
                val oneTimeWorkInfos by WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow(SlipIngestWorker.UNIQUE_ONE_TIME_NAME)
                    .collectAsState(initial = emptyList())
                val periodicWorkInfos by WorkManager.getInstance(applicationContext)
                    .getWorkInfosForUniqueWorkFlow(SlipIngestWorker.UNIQUE_PERIODIC_NAME)
                    .collectAsState(initial = emptyList())
                val parkedCount by container.captureDao.observeParkedCount().collectAsState(initial = 0)
                val status = scanStatus(
                    oneTime = oneTimeWorkInfos.map { it.state },
                    periodic = periodicWorkInfos.map { it.state },
                    parked = parkedCount,
                )
                ExpenseScreen(
                    expenses, totals, status, hasImagePermission, requestImagePermission,
                    monthLabel = MonthHeaderLabel(window.from),
                    onOpenSettings = {
                        startActivity(Intent(applicationContext, SettingsActivity::class.java))
                    },
                ) {
                    if (!modelReady) {
                        ModelDownloadCard(downloadInfo, downloadModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberImagePermission(context: Context): Pair<Boolean, () -> Unit> {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
    }
    if (!granted) {
        LaunchedEffect(permission) {
            launcher.launch(permission)
        }
    }
    return granted to { launcher.launch(permission) }
}

@Composable
fun ExpenseScreen(
    expenses: List<ExpenseWithCategory>,
    totals: List<CategoryTotal>,
    status: ScanStatus,
    hasImagePermission: Boolean,
    onRequestImagePermission: () -> Unit,
    monthLabel: String,
    onOpenSettings: () -> Unit = {},
    modelCard: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SAURON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenSettings, shape = RectangleShape) {
                    Text("Settings")
                }
            }
            modelCard()
            AnimatedVisibility(
                visible = !hasImagePermission,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PermissionBanner(onRequestImagePermission)
            }
            AnimatedVisibility(
                visible = status != ScanStatus.Idle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ScanIndicator(status)
            }
            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "No expenses yet",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "Save a slip in K PLUS, SCB EASY, or CLICX. " +
                                "It becomes an expense here within 15 minutes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                val grandTotal = totals.fold(0L) { acc, total -> acc + total.totalMinor }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item { MonthHeader(monthLabel, grandTotal) }
                    items(totals) { total ->
                        CategoryTotalRow(total, grandTotal)
                    }
                    item { SectionLabel("All expenses") }
                    items(expenses) { expense ->
                        ExpenseRow(expense)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(monthLabel: String, grandTotalMinor: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "This month · $monthLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Money(grandTotalMinor, Currency.THB).display(),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun CategoryTotalRow(total: CategoryTotal, grandTotalMinor: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(total.name, style = MaterialTheme.typography.bodyLarge)
            Text(Money(total.totalMinor, Currency.THB).display(), style = MaterialTheme.typography.bodyLarge)
        }
        val fraction = if (grandTotalMinor > 0) {
            (total.totalMinor.toFloat() / grandTotalMinor).coerceIn(0f, 1f)
        } else {
            0f
        }
        SegmentedBar(fraction)
    }
}

/** A 24-block HP-style share bar, filled proportionally to the category total. */
@Composable
private fun SegmentedBar(fraction: Float, modifier: Modifier = Modifier) {
    val blocks = 24
    val filled = (fraction * blocks).roundToInt()
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(blocks) { block ->
            val lit = block < filled
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(
                        if (lit) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScanIndicator(status: ScanStatus) {
    when (status) {
        ScanStatus.Working -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Square,
            )
            Text(
                "Scanning slips",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ScanStatus.Parked -> Text(
            "${status.count} slips waiting to be read",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScanStatus.Idle -> Unit
    }
}

@Composable
private fun ModelDownloadCard(downloadInfo: WorkInfo?, onDownload: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Gemma model for slip reading",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Reads decorative slips on device. One 2.0 GB download over WiFi only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloadInfo?.state == WorkInfo.State.RUNNING) {
                val percent = downloadInfo.progress.getInt(ModelDownloadWorker.PROGRESS_PERCENT, 0)
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Square,
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(onClick = onDownload, shape = RectangleShape) {
                    Text("Download model (2.0 GB, WiFi)")
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequestImagePermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "อนุญาตให้ Sauron เข้าถึงรูปภาพเพื่ออ่านสลิป",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRequestImagePermission, shape = RectangleShape) {
                Text("อนุญาต")
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseWithCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(expense.expense.merchant, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${expense.category.name} · ${formatOccurred(expense.expense.occurredAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Money(expense.expense.amountMinor, expense.expense.currency).display(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
