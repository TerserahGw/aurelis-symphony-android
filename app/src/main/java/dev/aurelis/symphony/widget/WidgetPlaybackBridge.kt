package dev.aurelis.symphony.widget

import dev.aurelis.symphony.ui.AurelisState
import java.lang.ref.WeakReference

/**
 * Queue navigation belongs to AurelisState, not Media3's single resolved
 * stream item. Keep a weak reference only while the actual UI ViewModel lives.
 * A widget tap can safely skip or rewind without launching the Activity while
 * that state is running. If Android has reclaimed it, the widget opens Aurelis
 * rather than calling Media3 seekToNextMediaItem on a one-item queue.
 */
object WidgetPlaybackBridge {
    private var owner: WeakReference<AurelisState>? = null

    fun attach(state: AurelisState) {
        owner = WeakReference(state)
    }

    fun detach(state: AurelisState) {
        if (owner?.get() === state) owner = null
    }

    fun transport(action: String): Boolean {
        val state = owner?.get() ?: return false
        if (state.current == null) return false
        return when (action) {
            AurelisWidgetProvider.ACTION_NEXT -> {
                state.next()
                true
            }
            AurelisWidgetProvider.ACTION_PREVIOUS -> {
                state.previous()
                true
            }
            else -> false
        }
    }
}
