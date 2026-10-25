package dev.fireforge.sauron.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SauronApplication).container
        val expenseDao = container.expenseDao
        val window = MonthWindow.current(Instant.now(), ZoneOffset.ofHours(7))
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
                ExpenseScreen(expenses, totals, status, hasImagePermission, requestImagePermission) {
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
    modelCard: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            modelCard()
            if (!hasImagePermission) {
                PermissionBanner(onRequestImagePermission)
            }
            ScanIndicator(status)
            if (expenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No expenses yet")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            "This month",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(totals) { total ->
                        CategoryTotalRow(total)
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    items(expenses) { expense ->
                        ExpenseRow(expense)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanIndicator(status: ScanStatus) {
    when (status) {
        ScanStatus.Working -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Scanning slips")
        }
        is ScanStatus.Parked -> Text(
            "${status.count} slips waiting to be read",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ScanStatus.Idle -> Unit
    }
}

@Composable
private fun ModelDownloadCard(downloadInfo: WorkInfo?, onDownload: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Gemma model for slip reading")
            if (downloadInfo?.state == WorkInfo.State.RUNNING) {
                LinearProgressIndicator(
                    progress = {
                        downloadInfo.progress.getInt(ModelDownloadWorker.PROGRESS_PERCENT, 0) / 100f
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(onClick = onDownload) {
                    Text("Download model (2.0 GB, WiFi)")
                }
            }
        }
    }
}

@Composable
private fun CategoryTotalRow(total: CategoryTotal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(total.name)
        Text(Money(total.totalMinor, Currency.THB).display())
    }
}

@Composable
private fun PermissionBanner(onRequestImagePermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("อนุญาตให้ Sauron เข้าถึงรูปภาพเพื่ออ่านสลิป")
            Button(onClick = onRequestImagePermission) {
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
        Column {
            Text(expense.expense.merchant, fontWeight = FontWeight.SemiBold)
            Text(expense.category.name, style = MaterialTheme.typography.bodySmall)
        }
        Text(Money(expense.expense.amountMinor, expense.expense.currency).display())
    }
}
