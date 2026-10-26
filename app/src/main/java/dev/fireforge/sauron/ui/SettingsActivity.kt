package dev.fireforge.sauron.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                )
            }
        }
    }
}

@Composable
private fun SettingsForm(initialApiKey: String, initialModelId: String, onSave: (String, String) -> Unit) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var modelId by remember { mutableStateOf(initialModelId) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "AI settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "When a key is set, slips the on-device model cannot read are sent to Google's Gemini API. Images leave this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            label = { Text("Model id") },
            singleLine = true,
        )
        Button(onClick = { onSave(apiKey.trim(), modelId.trim()) }) {
            Text("Save")
        }
    }
}
