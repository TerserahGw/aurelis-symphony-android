package dev.aurelis.symphony.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.google.common.util.concurrent.ListenableFuture
import dev.aurelis.symphony.data.*
import dev.aurelis.symphony.playback.AudioService
import dev.aurelis.symphony.playback.NewPipeSource
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AurelisState(context: Context) {
    private val app = context.applicationContext
    val library = LocalLibrary(app)
    val api = AurelisApi()
    private val newPipe by lazy { NewPipeSource() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture: ListenableFuture<MediaController>
    private val imageClient = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private var playJob: Job? = null
    private var lyricJob: Job? = null
    private var searchJob: Job? = null
    private var lastResolveUsedNewPipe = false
    private var triedFallbackFor: String? = null

    var controller by mutableStateOf<MediaController?>(null)
        private set
    var current by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0L)
        private set
    var duration by mutableStateOf(0L)
        private set
    var ambientColor by mutableStateOf(Color(0xFF315A4C))
        private set
    var home by mutableStateOf<List<Song>>(emptyList())
        private set
    var originals by mutableStateOf<List<Song>>(emptyList())
        private set
    var results by mutableStateOf<List<Song>>(emptyList())
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var lyrics by mutableStateOf(Lyrics("", emptyList()))
        private set
    var romanized by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadingHome by mutableStateOf(true)
        private set
    var searching by mutableStateOf(false)
        private set
    var libraryStamp by mutableStateOf(0)
        private set
    var settings by mutableStateOf(library.settings)
        private set

    init {
        val token = SessionToken(app, ComponentName(app, AudioService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.onSuccess { player ->
                controller = player
                isPlaying = player.isPlaying
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingNow: Boolean) { isPlaying = isPlayingNow }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) next()
                        if (playbackState == Player.STATE_READY) preparing = false
                    }
                    override fun onPlayerError(exception: PlaybackException) {
                        if (settings.audioMode == AudioMode.AUTO && lastResolveUsedNewPipe &&
                            triedFallbackFor != current?.key) {
                            val song = current ?: return
                            triedFallbackFor = song.key
                            scope.launch {
                                runCatching { api.playback(song) }.onSuccess { stream ->
                                    lastResolveUsedNewPipe = false
                                    startResolvedPlayback(song, stream)
                                }.onFailure { error = "NewPipe dan server gagal memutar lagu ini." }
                            }
                        } else error = "Audio tidak dapat diputar: ${exception.errorCodeName}"
                        preparing = false
                    }
                })
            }.onFailure { error = "Pemutar audio gagal tersambung: ${it.message}" }
        }, app.mainExecutor)
        scope.launch {
            while (isActive) {
                controller?.let { c ->
                    progress = c.currentPosition.coerceAtLeast(0)
                    duration = c.duration.takeIf { it > 0 } ?: current?.duration?.times(1000) ?: 0
                }
                delay(if (isPlaying) 65 else 500)
            }
        }
        refreshHome()
    }

    fun dismissError() { error = null }
    fun refreshHome() {
        scope.launch {
            loadingHome = true
            val catalog = runCatching { api.catalog() }.getOrElse { emptyList() }
            originals = catalog
            home = runCatching { api.home(library.favorites, library.history) }.getOrElse { catalog }
            if (home.isEmpty()) home = catalog
            if (home.isEmpty()) error = "Tidak bisa terhubung ke Aurelis Server. Cek koneksi atau status server."
            loadingHome = false
        }
    }
    fun search(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) { results = emptyList(); searching = false; return }
        searchJob = scope.launch {
            searching = true
            results = runCatching { api.search(q.take(160)) }.getOrElse {
                error = "Pencarian gagal: ${it.message}"
                emptyList()
            }
            searching = false
        }
    }
    fun play(song: Song, addHistory: Boolean = true) {
        playJob?.cancel()
        playJob = scope.launch {
            current = song; preparing = true; progress = 0; lyrics = Lyrics("", emptyList()); romanized = emptyMap()
            error = null; triedFallbackFor = null
            if (addHistory) { library.addHistory(song); libraryStamp++ }
            refreshAmbient(song)
            lyricJob?.cancel()
            lyricJob = scope.launch {
                val fetched = runCatching { api.lyrics(song) }.getOrDefault(Lyrics("", emptyList()))
                if (current?.key != song.key) return@launch
                lyrics = fetched
                if (settings.romanize && fetched.lines.isNotEmpty()) {
                    romanized = runCatching { api.romanize(fetched) }.getOrDefault(emptyMap())
                }
            }
            val stream = try {
                if (!song.isYouTube && song.audio.isNotBlank()) {
                    lastResolveUsedNewPipe = false
                    if (song.audio.startsWith("https://")) song.audio else api.base.trimEnd('/') + "/" + song.audio.trimStart('/')
                } else if (settings.audioMode == AudioMode.AURELIS) {
                    lastResolveUsedNewPipe = false; api.playback(song)
                } else if (settings.audioMode == AudioMode.NEWPIPE) {
                    lastResolveUsedNewPipe = true; newPipe.resolve(song)
                } else {
                    try {
                        val uri = newPipe.resolve(song)
                        lastResolveUsedNewPipe = true
                        uri
                    } catch (_: Exception) {
                        lastResolveUsedNewPipe = false; api.playback(song)
                    }
                }
            } catch (e: Exception) {
                preparing = false
                error = "Sumber audio tidak tersedia: ${e.message}"
                return@launch
            }
            startResolvedPlayback(song, stream)
        }
    }
    private fun startResolvedPlayback(song: Song, stream: String) {
        val c = controller ?: run { error = "Pemutar belum siap. Coba ketuk lagi."; preparing = false; return }
        val meta = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
            .apply { if (song.artwork.startsWith("https://")) setArtworkUri(android.net.Uri.parse(song.artwork)) }.build()
        c.setMediaItem(MediaItem.Builder().setMediaId(song.id).setUri(stream).setMediaMetadata(meta).build())
        c.prepare(); c.play()
    }
    private fun refreshAmbient(song: Song) {
        if (!song.artwork.startsWith("https://")) { ambientColor = Color(0xFF315A4C); return }
        scope.launch(Dispatchers.IO) {
            runCatching {
                imageClient.newCall(Request.Builder().url(song.artwork).build()).execute().use { resp ->
                    val bytes = resp.body?.bytes() ?: return@use null
                    if (bytes.size > 3_000_000) return@use null
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { bmp ->
                        val palette = Palette.from(bmp).maximumColorCount(12).generate()
                        val color = palette.getMutedColor(palette.getVibrantColor(0xFF315A4C.toInt()))
                        bmp.recycle(); color
                    }
                }
            }.getOrNull()?.let { color -> withContext(Dispatchers.Main) {
                if (current?.key == song.key) ambientColor = Color(color)
            } }
        }
    }
    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    fun seek(fraction: Float) { controller?.let { if (duration > 0) it.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong()) } }
    fun enqueue(song: Song) { queue = (queue + song).distinctBy { it.key } }
    fun removeQueue(song: Song) { queue = queue.filterNot { it.key == song.key } }
    fun next() {
        val song = current ?: return
        val next = queue.firstOrNull()
        if (next != null) { queue = queue.drop(1); play(next); return }
        scope.launch {
            val picks = runCatching { api.recommendations(song) }.getOrDefault(emptyList())
                .filter { it.key != song.key }.distinctBy { it.key }
            if (picks.isEmpty()) { controller?.pause(); return@launch }
            queue = picks.drop(1).take(4)
            play(picks.first())
        }
    }
    fun previous() {
        if (progress > 3500) { controller?.seekTo(0); return }
        val prev = library.history.firstOrNull { it.key != current?.key }
        if (prev != null) play(prev) else controller?.seekTo(0)
    }
    fun like(song: Song) { library.toggleLike(song); libraryStamp++; }
    fun isLiked(song: Song) = library.favorites.any { it.key == song.key }
    fun addPlaylist(name: String) { library.newPlaylist(name); libraryStamp++ }
    fun addToPlaylist(name: String, song: Song) { library.addToPlaylist(name, song); libraryStamp++ }
    fun update(s: AppSettings) {
        settings = s; library.updateSettings(s)
        if (s.romanize && romanized.isEmpty() && lyrics.lines.isNotEmpty()) {
            scope.launch { romanized = runCatching { api.romanize(lyrics) }.getOrDefault(emptyMap()) }
        }
    }
    fun close() {
        playJob?.cancel(); lyricJob?.cancel(); searchJob?.cancel(); scope.cancel()
        if (controllerFuture.isDone) runCatching { MediaController.releaseFuture(controllerFuture) }
    }
}
