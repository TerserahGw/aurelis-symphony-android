package dev.aurelis.symphony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurelis.symphony.ui.AurelisScreen
import dev.aurelis.symphony.ui.AurelisState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            // The same ViewModel survives portrait/landscape Activity recreation.
            // A new AurelisState would discard the active mini player, queue,
            // session controller and resolved streams on every rotation.
            val factory = remember {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        require(modelClass.isAssignableFrom(AurelisState::class.java))
                        return AurelisState(applicationContext) as T
                    }
                }
            }
            val state: AurelisState = viewModel(factory = factory)
            AurelisScreen(state)
        }
    }
}
