package com.example.pccontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private var sharedUrlState = mutableStateOf<String?>(null)
    private val themeState = mutableStateOf("cyber")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractSharedUrl(intent)?.let { sharedUrlState.value = it }

        val appPrefs = getSharedPreferences("pc_control", MODE_PRIVATE)
        themeState.value = appPrefs.getString("app_theme", "cyber") ?: "cyber"

        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(themeState.value)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PcControlMainApp(
                        incomingSharedUrl = sharedUrlState.value,
                        onSharedUrlConsumed = { sharedUrlState.value = null },
                        themeId = themeState.value,
                        onThemeChange = { id ->
                            themeState.value = id
                            appPrefs.edit().putString("app_theme", id).apply()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrl(intent)?.let { sharedUrlState.value = it }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("""https?://\S+""").find(text)?.value
    }
}
