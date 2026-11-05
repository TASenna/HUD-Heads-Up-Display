package com.taydox.hudtool

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

class DiscoveryHubActivity : ComponentActivity() {

    private val viewModel: DiscoveryHubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00C0BF),
                    background = Color(0xFF2A2A2A),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                ReferenceDiscoveryHubScreen(
                    viewModel = viewModel,
                    onNavigateToWebView = { url ->
                        val intent = Intent(this, WebSearchActivity::class.java).apply {
                            putExtra("TARGET_URL", url)
                        }
                        startActivity(intent)
                    },
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onNavigateToUpgrade = {
                        ThemeUtils.showPremiumUpgradePrompt(this)
                    },
                    onNavigateBack = {
                        finish()
                    }
                )
            }
        }
    }
}
