package dev.aurelis.symphony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import dev.aurelis.symphony.ui.AurelisScreen
import dev.aurelis.symphony.ui.AurelisState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            val state = remember { AurelisState(applicationContext) }
            DisposableEffect(state) { onDispose { state.close() } }
            AurelisScreen(state)
        }
    }
}
