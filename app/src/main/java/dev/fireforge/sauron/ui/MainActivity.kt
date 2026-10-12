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
import dev.fireforge.sauron.SauronApplication
import dev.fireforge.sauron.data.CategoryTotal
import dev.fireforge.sauron.data.ExpenseWithCategory
import dev.fireforge.sauron.domain.Currency
import dev.fireforge.sauron.domain.Money
import dev.fireforge.sauron.domain.MonthWindow
import java.time.Instant
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val expenseDao = (application as SauronApplication).container.expenseDao
        val window = MonthWindow.current(Instant.now(), ZoneOffset.ofHours(7))
        setContent {
            SauronTheme {
                val (hasImagePermission, requestImagePermission) = rememberImagePermission(applicationContext)
                val expenses by expenseDao.observeAll().collectAsState(initial = emptyList())
                val totals by expenseDao.totalByCategory(window.from, window.to).collectAsState(initial = emptyList())
                ExpenseScreen(expenses, totals, hasImagePermission, requestImagePermission)
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
    hasImagePermission: Boolean,
    onRequestImagePermission: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (expenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No expenses yet")
            }
        } else {
            LazyColumn {
                if (!hasImagePermission) {
                    item { PermissionBanner(onRequestImagePermission) }
                }
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
