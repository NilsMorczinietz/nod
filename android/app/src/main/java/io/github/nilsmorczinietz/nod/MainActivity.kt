package io.github.nilsmorczinietz.nod

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Color
import io.github.nilsmorczinietz.nod.data.store.DEFAULT_THEME
import io.github.nilsmorczinietz.nod.data.store.ThemeMode
import io.github.nilsmorczinietz.nod.ui.NodRoot
import io.github.nilsmorczinietz.nod.ui.theme.NodTheme
import io.github.nilsmorczinietz.nod.ui.theme.ProvideNodFonts
import kotlinx.coroutines.flow.MutableStateFlow

/** Pending navigation requests coming from intents (notification taps, nod:// links). */
class DeepLinks {
    val itemId = MutableStateFlow<String?>(null)
    val pairLink = MutableStateFlow<String?>(null)
}

class MainActivity : ComponentActivity() {
    private val deepLinks = DeepLinks()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as NodApp).container
        val themeMode = container.sessionStore.themeMode
        val useSystemBold = container.appearance.useSystemBold
        setContent {
            val mode by themeMode.collectAsStateWithLifecycle(DEFAULT_THEME)
            val systemBold by useSystemBold.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Keep status/navigation bar icons readable when the app theme differs from the system.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ProvideNodFonts(useSystemBold = systemBold) {
                NodTheme(darkTheme = dark) {
                    NodRoot(deepLinks)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_ITEM_ID)?.let { deepLinks.itemId.value = it }
        intent.data?.takeIf { it.scheme == "nod" && it.host == "pair" }?.let { deepLinks.pairLink.value = it.toString() }
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
    }
}
