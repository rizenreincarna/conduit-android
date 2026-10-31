package app.conduit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xffb1abff), background = Color(0xff111218))) {
                FoundationScreen()
            }
        }
    }
}

// android: Temporary development entry point while the upstream login/AppState batches
// are ported. No credentials or connection success are simulated here.
@Composable
private fun FoundationScreen() {
    val languageStore = AppLanguageStore.shared
    val selected = languageStore.selection
    var address by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(modifier = Modifier.semantics { testTagsAsResourceId = true }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Conduit", style = MaterialTheme.typography.headlineLarge)
            Text("Android port · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)
            Text("The Android foundation is running. Dashboard sign-in and chat are still being ported.")
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; error = null },
                label = { Text(AppLocalization.string("Dashboard URL")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag("dashboardURL"),
            )
            TextButton(onClick = {
                try {
                    address = ConnectionURLPolicy.normalizedBaseURL(address)
                    error = "Address passes the transport policy. Sign-in is not available in this build."
                } catch (failure: ConnectionURLPolicyError) { error = failure.errorDescription }
            }, modifier = Modifier.testTag("validateAddress")) { Text("Validate address") }
            error?.let { Text(it, modifier = Modifier.testTag("addressResult")) }
            Text(AppLocalization.string("App Language"), style = MaterialTheme.typography.titleMedium)
            AppLanguage.selectable.forEach { language ->
                TextButton(onClick = { languageStore.select(language) }) {
                    Text((if (selected == language) "✓ " else "") + language.displayName)
                }
            }
        }
    }
}
