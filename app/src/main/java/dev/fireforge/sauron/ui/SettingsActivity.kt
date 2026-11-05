package dev.fireforge.sauron.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.fireforge.sauron.ai.AiSettings

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AiSettings(this)
        setContent {
            SauronTheme {
                SettingsForm(
                    initialApiKey = settings.apiKey.orEmpty(),
                    initialModelId = settings.modelId,
                    onSave = { apiKey, modelId ->
                        settings.apiKey = apiKey
                        settings.modelId = modelId
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun SettingsForm(
    initialApiKey: String,
    initialModelId: String,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        var apiKey by remember { mutableStateOf(initialApiKey) }
        var modelId by remember { mutableStateOf(initialModelId) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(
                onClick = onBack,
                shape = RectangleShape,
                contentPadding = PaddingValues(),
            ) {
                Text("Back")
            }
            Text(
                "AI settings",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "When a key is set, slips the on-device model cannot read are sent " +
                    "to Google's Gemini API. Images leave this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                shape = RectangleShape,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model id") },
                singleLine = true,
                shape = RectangleShape,
            )
            Button(onClick = { onSave(apiKey.trim(), modelId.trim()) }, shape = RectangleShape) {
                Text("Save")
            }
        }
    }
}
