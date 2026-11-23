package dev.aurelis.symphony.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.aurelis.symphony.widget.AurelisWidgetProvider
import dev.aurelis.symphony.widget.WidgetPlaybackBridge
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AurelisState(context: Context) : ViewModel() {
    private val app = context.applicationContext
    val library = LocalLibrary(app)
    val api = AurelisApi()
    val youtube = YouTubeMusicHub(app)
    private val newPipe by lazy { NewPipeSource() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture: ListenableFuture<MediaController>
    private val imageClient = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private var playJob: Job? = null
    private var lyricJob: Job? = null
    private var translationJob: Job? = null
    private var searchJob: Job? = null
    private var settingsSaveJob: Job? = null
    private var searchRevision = 0L
    private var recommendationJob: Job? = null
    private var nextJob: Job? = null
    private var recommendationRevision = 0L
    private val skippedRecommendations = mutableSetOf<String>()
    private val queueTargetSize = 5
    private var playlistTracks: List<Song> = emptyList()
    private var playlistIndex = -1
    var activePlaylist by mutableStateOf<String?>(null)
        private set
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
    var translated by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    var translating by mutableStateOf(false)
        private set
    var translationError by mutableStateOf<String?>(null)
        private set
    private var translatedForKey: String? = null
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
        WidgetPlaybackBridge.attach(this)
        // Publish exactly the real upcoming queue whenever a playlist,
        // recommendation or manual queue edit changes it. Widgets show the
        // two immediate songs even while the Activity is in the background.
        scope.launch {
            snapshotFlow { queue }.distinctUntilChanged().collect { upcoming ->
                AurelisWidgetProvider.publishQueue(app, upcoming)
            }
        }
        val token = SessionToken(app, ComponentName(app, AudioService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.onSuccess { player ->
                controller = player
                isPlaying = player.isPlaying
                // Restore the currently playing MediaSession item if the
                // Activity was recreated after the old ViewModel was cleared.
                player.currentMediaItem?.let { item ->
                    if (current == null && item.mediaMetadata.title != null) {
                        current = Song(
                            id = item.mediaId,
                            title = item.mediaMetadata.title.toString(),
                            artist = item.mediaMetadata.artist?.toString().orEmpty(),
                            artwork = item.mediaMetadata.artworkUri?.toString().orEmpty()
                        )
                    }
                }
                AurelisWidgetProvider.publish(app, current, isPlaying,
                    ambientColor.toArgb())
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                        isPlaying = isPlayingNow
                        AurelisWidgetProvider.publish(app, current, isPlayingNow,
                            ambientColor.toArgb())
                    }
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
    /**
     * Cancel right away on each keystroke, not only after debounce.
     * Kotlin CancellationException is a control-flow signal, not a search error.
     */
    fun cancelSearch() {
        searchRevision++
        searchJob?.cancel()
        searchJob = null
        results = emptyList()
        searching = false
    }

    fun search(q: String) {
        searchRevision++
        val revision = searchRevision
        searchJob?.cancel()
        if (q.isBlank()) {
            searching = false
            results = emptyList()
            return
        }
        searchJob = scope.launch {
            searching = true
            try {
                val found = api.search(q.trim().take(160))
                if (revision == searchRevision) results = found
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (revision == searchRevision) {
                    results = emptyList()
                    error = "Pencarian gagal: ${failure.message ?: "koneksi bermasalah"}"
                }
            } finally {
                if (revision == searchRevision) searching = false
            }
        }
    }
    fun play(song: Song, addHistory: Boolean = true, preserveQueue: Boolean = false) {
        if (!preserveQueue) {
            activePlaylist = null
            playlistTracks = emptyList()
            playlistIndex = -1
        }
        recommendationJob?.cancel()
        nextJob?.cancel()
        recommendationRevision++
        if (!preserveQueue) {
            queue = emptyList()
            skippedRecommendations.clear()
        }
        playJob?.cancel()
        translationJob?.cancel()
        translatedForKey = null
        translated = emptyMap()
        translating = false
        translationError = null
        playJob = scope.launch {
            current = song; preparing = true; progress = 0; lyrics = Lyrics("", emptyList()); romanized = emptyMap()
            AurelisWidgetProvider.publish(app, song, isPlaying, ambientColor.toArgb())
            replenishQueue(song)
            error = null; triedFallbackFor = null
            if (addHistory) { library.addHistory(song); libraryStamp++ }
            refreshAmbient(song)
            lyricJob?.cancel()
            lyricJob = scope.launch {
                val fetched = runCatching { api.lyrics(song) }.getOrDefault(Lyrics("", emptyList()))
                if (current?.key != song.key) return@launch
                lyrics = fetched
                translated = fetched.lines.mapIndexedNotNull { index, line ->
                    line.translation.takeIf { it.isNotBlank() }?.let { index to it }
                }.toMap()
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
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        lastResolveUsedNewPipe = false; api.playback(song)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (current?.key == song.key) {
                    preparing = false
                    error = "Sumber audio tidak tersedia: ${e.message}"
                }
                return@launch
            }
            if (current?.key != song.key) return@launch
            startResolvedPlayback(song, stream)
        }
    }
    /**
     * A playlist is an ordered play context, not a radio seed. Up Next shows
     * ONLY the remaining songs of this playlist, in their original order.
     * Duplicate tracks are preserved. At the end, playback stops.
     */
    fun playPlaylist(name: String, tracks: List<Song>, index: Int = 0) {
        if (tracks.isEmpty()) {
            error = "Playlist tidak berisi lagu yang dapat diputar."
            return
        }
        val safe = index.coerceIn(0, tracks.lastIndex)
        playlistTracks = tracks.toList()
        playlistIndex = safe
        activePlaylist = name
        queue = playlistTracks.drop(safe + 1)
        skippedRecommendations.clear()
        play(tracks[safe], preserveQueue = true)
    }

    fun removeFromPlaylist(name: String, song: Song) {
        if (!library.removeFromPlaylist(name, song)) return
        libraryStamp++
        if (activePlaylist == name) {
            // The current track may keep playing, while Up Next reflects
            // the edited playlist rather than retaining removed songs.
            playlistTracks = playlistTracks.filterNot { it.key == song.key }
            val playingKey = current?.key
            playlistIndex = playlistTracks.indexOfFirst { it.key == playingKey }
            queue = if (playlistIndex >= 0) playlistTracks.drop(playlistIndex + 1)
                else queue.filterNot { it.key == song.key }
            if (playlistTracks.isEmpty()) {
                activePlaylist = null
                playlistIndex = -1
            }
        }
    }

    /** Jump to an item in Up Next without destroying the remaining playlist. */
    fun playFromQueue(song: Song, position: Int = queue.indexOfFirst { it.key == song.key }) {
        val idx = if (position in queue.indices) position else return
        val selected = queue[idx]
        queue = queue.drop(idx + 1)
        if (activePlaylist != null) {
            val found = playlistTracks.indexOfFirst {
                it.key == selected.key && playlistTracks.indexOf(it) > playlistIndex
            }
            playlistIndex = if (found >= 0) found else playlistIndex + idx + 1
        }
        play(selected, preserveQueue = true)
    }

    private fun startResolvedPlayback(song: Song, stream: String) {
        val c = controller ?: run { error = "Pemutar belum siap. Coba ketuk lagi."; preparing = false; return }
        val meta = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
            .apply { if (song.artwork.startsWith("https://")) setArtworkUri(android.net.Uri.parse(song.artwork)) }.build()
        c.setMediaItem(MediaItem.Builder().setMediaId(song.id).setUri(stream).setMediaMetadata(meta).build())
        c.prepare(); c.play()
    }
    private fun refreshAmbient(song: Song) {
        if (!song.artwork.startsWith("https://")) {
            ambientColor = Color(0xFF315A4C)
            AurelisWidgetProvider.publish(app, song, isPlaying, ambientColor.toArgb())
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                imageClient.newCall(Request.Builder().url(song.artwork).build()).execute().use { resp ->
                    val bytes = resp.body?.bytes() ?: return@use null
                    if (bytes.size > 5_000_000) return@use null
                    // The same cover is reused by home widgets so album art
                    // does not require a fragile second HTTP request.
                    AurelisWidgetProvider.cacheArtworkBytes(app, song, bytes)
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { bmp ->
                        val palette = Palette.from(bmp).maximumColorCount(12).generate()
                        val color = palette.getMutedColor(palette.getVibrantColor(0xFF315A4C.toInt()))
                        bmp.recycle(); color
                    }
                }
            }.getOrNull()?.let { color -> withContext(Dispatchers.Main) {
                if (current?.key == song.key) {
                    ambientColor = Color(color)
                    AurelisWidgetProvider.publish(app, song, isPlaying,
                        ambientColor.toArgb())
                }
            } }
        }
    }
    /**
     * Translations are never sent until the user explicitly taps Translate.
     * The backend may return partial results because the anonymous provider
     * rate-limits. Keep the original lyrics visible if translation fails.
     */
    fun loadTranslations() {
        val key = current?.key ?: return
        val target = settings.language.code
        val identity = "$key|$target"
        val captured = lyrics
        if (captured.lines.isEmpty() || translating || translatedForKey == identity) return
        translationJob?.cancel()
        translationJob = scope.launch {
            translating = true
            translationError = null
            try {
                val result = api.translateLyrics(captured, target = target)
                if (current?.key == key && settings.language.code == target) {
                    translated = result
                    translatedForKey = identity
                    if (result.size < captured.lines.size) {
                        translationError = "Sebagian baris belum dapat diterjemahkan."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (current?.key == key && settings.language.code == target) {
                    translationError = e.message ?: "Terjemahan tidak tersedia."
                }
            } finally {
                if (current?.key == key && settings.language.code == target) translating = false
            }
        }
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    fun seek(fraction: Float) { controller?.let { if (duration > 0) it.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong()) } }
    private fun makeRecommendationPool(seed: Song, remotelyRecommended: List<Song>): List<Song> {
        // The server ranks candidates first; local signals improve relevance and
        // diversify them. Use catalog/history fallback if the backend is offline.
        return remotelyRecommended +
            youtube.shelves.flatMap { it.tracks }.take(60) +
            youtube.history.take(25) +
            home.take(30) + originals.take(20) +
            library.favorites.take(40) + library.history.take(100)
    }

    private fun rankQueue(seed: Song, remote: List<Song>, want: Int): List<Song> {
        val excluded = (queue.map { it.key } +
            skippedRecommendations + listOfNotNull(current?.key)).toSet()
        return RecommendationRanker.pick(
            seed = seed,
            incoming = makeRecommendationPool(seed, remote),
            favorites = library.favorites,
            history = library.history,
            youtubeHistory = if (youtube.isConnected) youtube.history else emptyList(),
            youtubeRecommendations = if (youtube.isConnected)
                youtube.shelves.flatMap { it.tracks }.take(100) else emptyList(),
            excluded = excluded,
            maxCount = want
        )
    }

    private fun replenishQueue(seed: Song) {
        if (activePlaylist != null || queue.size >= queueTargetSize) return
        recommendationJob?.cancel()
        val revision = ++recommendationRevision
        recommendationJob = scope.launch {
            val candidates = coroutineScope {
                val fromAurelis = async {
                    try { api.recommendations(seed) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                }
                val radio = async {
                    if (seed.isYouTube) youtube.radio(seed) else emptyList()
                }
                // Prefer related YouTube Music radio, then existing Aurelis
                // suggestions; both are requested concurrently.
                radio.await() + fromAurelis.await()
            }
            if (revision != recommendationRevision || current?.key != seed.key) return@launch
            val needed = (queueTargetSize - queue.size).coerceAtLeast(0)
            if (needed == 0) return@launch
            val additions = rankQueue(seed, candidates, needed)
            if (additions.isNotEmpty()) {
                queue = (queue + additions).distinctBy { it.key }.take(10)
            }
        }
    }

    fun enqueue(song: Song) {
        if (song.key != current?.key && queue.none { it.key == song.key }) {
            skippedRecommendations.remove(song.key)
            queue = (queue + song).take(20)
        }
    }

    fun removeQueue(song: Song) {
        skippedRecommendations.add(song.key)
        queue = queue.filterNot { it.key == song.key }
        if (activePlaylist == null) current?.let(::replenishQueue)
    }

    fun next() {
        val seed = current ?: return
        nextJob?.cancel()
        if (activePlaylist != null) {
            val remaining = queue
            if (remaining.isEmpty()) {
                controller?.pause()
                controller?.seekTo(0)
                return
            }
            queue = remaining.drop(1)
            playlistIndex = (playlistIndex + 1).coerceAtMost(playlistTracks.lastIndex)
            play(remaining.first(), preserveQueue = true)
            return
        }
        val ready = queue.firstOrNull()
        if (ready != null) {
            queue = queue.drop(1)
            play(ready, preserveQueue = true)
            return
        }
        // If the user presses Next before prefetch returns, fetch once and
        // fall back gracefully instead of making an unrelated song choice.
        nextJob = scope.launch {
            val remote = coroutineScope {
                val yt = async { if (seed.isYouTube) youtube.radio(seed) else emptyList() }
                val server = async {
                    try { api.recommendations(seed) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                }
                yt.await() + server.await()
            }
            if (current?.key != seed.key) return@launch
            val picked = queue.firstOrNull() ?: rankQueue(seed, remote, 1).firstOrNull()
            if (picked == null) {
                controller?.pause()
                return@launch
            }
            queue = queue.filterNot { it.key == picked.key }
            play(picked, preserveQueue = true)
        }
    }

    fun previous() {
        if (progress > 3500) { controller?.seekTo(0); return }
        if (activePlaylist != null && playlistIndex > 0) {
            playlistIndex--
            queue = playlistTracks.drop(playlistIndex + 1)
            play(playlistTracks[playlistIndex], preserveQueue = true)
            return
        }
        val prev = library.history.firstOrNull { it.key != current?.key }
        if (prev != null) play(prev) else controller?.seekTo(0)
    }
    fun like(song: Song) { library.toggleLike(song); libraryStamp++; }
    fun isLiked(song: Song) = library.favorites.any { it.key == song.key }
    fun addPlaylist(name: String) { library.newPlaylist(name); libraryStamp++ }
    fun addToPlaylist(name: String, song: Song) { library.addToPlaylist(name, song); libraryStamp++ }
    fun update(s: AppSettings) {
        val previous = settings
        if (previous == s) return
        settings = s
        if (previous.language != s.language) {
            translationJob?.cancel()
            translatedForKey = null
            translated = emptyMap()
            translating = false
            translationError = null
        }
        settingsSaveJob?.cancel()
        // Smooth 60 fps sliders shouldn't enqueue a disk write every frame.
        // Toggles and other discrete settings are still persisted instantly.
        val sliderOnly = s.copy(
            glassOpacity = previous.glassOpacity,
            ambientIntensity = previous.ambientIntensity
        ) == previous
        if (sliderOnly) {
            settingsSaveJob = scope.launch {
                delay(220L)
                library.updateSettings(settings)
                // Avoid generating GPU/canvas widget RemoteViews 60 times per
                // second while the user drags a visual intensity slider.
                AurelisWidgetProvider.publish(app, current, isPlaying,
                    ambientColor.toArgb())
            }
        } else {
            library.updateSettings(s)
            // Switching Glass Ambient <-> Ambient re-themes the launcher
            // widget immediately without waiting for the next song.
            if (previous.visualStyle != s.visualStyle ||
                previous.ambient != s.ambient) {
                AurelisWidgetProvider.publish(app, current, isPlaying,
                    ambientColor.toArgb())
            }
        }
        if (s.romanize && romanized.isEmpty() && lyrics.lines.isNotEmpty()) {
            scope.launch { romanized = runCatching { api.romanize(lyrics) }.getOrDefault(emptyMap()) }
        }
    }
    fun close() {
        WidgetPlaybackBridge.detach(this)
        settingsSaveJob?.cancel()
        library.updateSettings(settings)
        playJob?.cancel(); lyricJob?.cancel(); translationJob?.cancel(); searchJob?.cancel()
        recommendationJob?.cancel(); nextJob?.cancel(); scope.cancel()
        if (controllerFuture.isDone) runCatching { MediaController.releaseFuture(controllerFuture) }
    }
    override fun onCleared() {
        close()
        super.onCleared()
    }

}
