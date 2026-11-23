package dev.aurelis.symphony.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.aurelis.symphony.widget.AurelisWidgetProvider

/**
 * Media3 music service. Crossfade is opt-in and uses two genuine decoders so
 * outgoing and incoming PCM overlap instead of fading an empty single player.
 * No secondary player or fade timer exists while the preference is disabled.
 */
class AudioService : MediaSessionService() {
    companion object {
        private const val TAG = "AurelisAudio"
        @Volatile private var instance: AudioService? = null

        /**
         * This app's MediaController and MediaSessionService share a process.
         * Run only from the main looper, where Media3 players are created.
         * If the service is unavailable, caller uses standard gapless replace.
         */
        fun crossfadeTo(item: MediaItem, durationMs: Long): Boolean {
            if (Looper.myLooper() != Looper.getMainLooper()) return false
            return instance?.startCrossfade(item, durationMs.coerceIn(1000L, 12_000L)) ?: false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var incoming: ExoPlayer? = null
    private var fadeRunnable: Runnable? = null
    private var fadeGeneration = 0

    private fun newPlayer(audioFocus: Boolean): ExoPlayer =
        ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), audioFocus)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (this@apply === player) {
                        AurelisWidgetProvider.publishFromSession(
                            this@AudioService, currentMediaItem, isPlaying)
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (this@apply === player) {
                        AurelisWidgetProvider.publishFromSession(
                            this@AudioService, mediaItem, isPlaying)
                    }
                }
            })
        }

    override fun onCreate() {
        super.onCreate()
        player = newPlayer(audioFocus = true)
        session = MediaSession.Builder(this, player).build()
        instance = this
    }

    private fun stopCrossfade(releaseIncoming: Boolean) {
        fadeGeneration++
        fadeRunnable?.let(handler::removeCallbacks)
        fadeRunnable = null
        if (::player.isInitialized) player.volume = 1f
        if (releaseIncoming) incoming?.let {
            if (it !== player) it.release()
            incoming = null
        }
    }

    private fun startCrossfade(item: MediaItem, durationMs: Long): Boolean {
        if (session == null || !::player.isInitialized || !player.isPlaying)
            return false
        stopCrossfade(releaseIncoming = true)
        val outgoing = player
        val generation = fadeGeneration
        val next = newPlayer(audioFocus = false)
        incoming = next
        next.volume = 0f
        next.addListener(object : Player.Listener {
            private var started = false
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_READY || started ||
                    generation != fadeGeneration || incoming !== next) return
                started = true
                next.play()
                val startedAt = SystemClock.uptimeMillis()
                val tick = object : Runnable {
                    override fun run() {
                        if (generation != fadeGeneration || incoming !== next) return
                        val progress = ((SystemClock.uptimeMillis() - startedAt).toFloat() /
                            durationMs).coerceIn(0f, 1f)
                        // Equal-power blend keeps the perceived loudness more
                        // consistent than simply dipping both volume values.
                        outgoing.volume = kotlin.math.cos(progress * Math.PI / 2).toFloat()
                        next.volume = kotlin.math.sin(progress * Math.PI / 2).toFloat()
                        if (progress < 1f) {
                            fadeRunnable = this
                            handler.postDelayed(this, 45L)
                        } else {
                            fadeRunnable = null
                            runCatching {
                                next.volume = 1f
                                session?.setPlayer(next)
                                player = next
                                incoming = null
                                outgoing.release()
                                // The new session player is now responsible
                                // for audio focus and unplugging headphones.
                                next.setAudioAttributes(AudioAttributes.Builder()
                                    .setUsage(C.USAGE_MEDIA)
                                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                            }.onFailure { Log.e(TAG, "Crossfade handoff failed", it) }
                        }
                    }
                }
                tick.run()
            }
            override fun onPlayerError(error: PlaybackException) {
                if (generation == fadeGeneration && incoming === next) {
                    Log.w(TAG, "Incoming crossfade track could not be decoded", error)
                    stopCrossfade(releaseIncoming = true)
                }
            }
        })
        next.setMediaItem(item)
        next.prepare()
        return true
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        stopCrossfade(releaseIncoming = true)
        if (instance === this) instance = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
