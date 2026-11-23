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
    private var finishCrossfade: (() -> Unit)? = null
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
                        // EOF is not a user Pause: keep the next track going.
                        // The outgoing player must not publish a false Pause
                        // state after the MediaSession has switched songs.
                        if (this@apply.playbackState == Player.STATE_ENDED &&
                            incoming != null) {
                            finishCrossfade?.invoke()
                            return
                        }
                        // Headset/notification/lock-screen Pause synchronizes
                        // both decoders and freezes the fade clock.
                        incoming?.let { fading ->
                            if (fading !== player) {
                                if (isPlaying) fading.play() else fading.pause()
                            }
                        }
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
        finishCrossfade = null
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
                // A single guarded handoff handles both full-duration fades
                // and a song that reaches the end during the overlap.
                val complete: () -> Unit = {
                    if (generation == fadeGeneration && incoming === next) {
                        fadeRunnable?.let(handler::removeCallbacks)
                        fadeRunnable = null
                        runCatching {
                            next.volume = 1f
                            session?.setPlayer(next)
                            player = next
                            incoming = null
                            finishCrossfade = null
                            outgoing.release()
                            next.setAudioAttributes(AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(), true)
                            AurelisWidgetProvider.publishFromSession(
                                this@AudioService, next.currentMediaItem, next.isPlaying)
                        }.onFailure { Log.e(TAG, "Crossfade handoff failed", it) }
                    }
                }
                finishCrossfade = complete
                if (outgoing.playbackState == Player.STATE_ENDED) {
                    // The incoming stream was still buffering when the old
                    // track ended. Resume the next song at full volume.
                    next.play()
                    complete()
                    return
                }
                // The user may have paused while the incoming stream was
                // buffering, so never force an unexpected resume.
                if (outgoing.isPlaying) next.play() else next.pause()
                val tick = object : Runnable {
                    private var activeElapsedMs = 0L
                    private var lastTick = SystemClock.uptimeMillis()
                    override fun run() {
                        if (generation != fadeGeneration || incoming !== next) return
                        if (outgoing.playbackState == Player.STATE_ENDED) {
                            next.play()
                            complete()
                            return
                        }
                        val now = SystemClock.uptimeMillis()
                        // Freeze the fade while paused; no silent completion
                        // and no volume jump after a long pause.
                        if (outgoing.isPlaying && next.isPlaying)
                            activeElapsedMs += (now - lastTick).coerceAtLeast(0L)
                        lastTick = now
                        val progress = (activeElapsedMs.toFloat() /
                            durationMs).coerceIn(0f, 1f)
                        // Equal-power blend keeps the perceived loudness more
                        // consistent than simply dipping both volume values.
                        outgoing.volume = kotlin.math.cos(progress * Math.PI / 2).toFloat()
                        next.volume = kotlin.math.sin(progress * Math.PI / 2).toFloat()
                        if (progress < 1f) {
                            fadeRunnable = this
                            handler.postDelayed(this, 45L)
                        } else complete()
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
