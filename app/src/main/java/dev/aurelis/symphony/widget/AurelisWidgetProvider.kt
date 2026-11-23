package dev.aurelis.symphony.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.Log
import android.view.View
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.aurelis.symphony.MainActivity
import dev.aurelis.symphony.R
import dev.aurelis.symphony.data.Song
import dev.aurelis.symphony.playback.AudioService
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Widget layouts are FIXED per receiver rather than chosen by the launcher's
 * arbitrary width/height (which distorted corners and broke larger grids).
 * Android RemoteViews cannot blur the launcher wallpaper. Glass Ambient uses
 * a transparent, artwork-reactive acrylic render with fixed-dp XML clipping.
 */
open class AurelisWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, widgetId: Int, options: Bundle
    ) {
        // Some OEM launchers still dispatch resize events with resizeMode=none.
        // Keep the selected widget layout; never swap based on measured width.
        updateWidgets(context, intArrayOf(widgetId))
    }

    override fun onEnabled(context: Context) { updateWidgets(context) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> transportToggle(context)
            ACTION_NEXT, ACTION_PREVIOUS ->
                if (!WidgetPlaybackBridge.transport(intent.action.orEmpty())) openAurelis(context)
            ACTION_REFRESH -> updateWidgets(context)
        }
    }

    private fun transportToggle(context: Context) {
        val pending = goAsync()
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, AudioService::class.java))
        val connection = MediaController.Builder(app, token).buildAsync()
        connection.addListener({
            try {
                val player = connection.get()
                if (player.isPlaying) player.pause()
                else if (player.mediaItemCount > 0) player.play()
                else openAurelis(app)
            } catch (_: Exception) {
                openAurelis(app)
            } finally {
                MediaController.releaseFuture(connection)
                pending.finish()
            }
        }, app.mainExecutor)
    }

    companion object {
        const val ACTION_TOGGLE = "dev.aurelis.symphony.WIDGET_TOGGLE"
        const val ACTION_PREVIOUS = "dev.aurelis.symphony.WIDGET_PREVIOUS"
        const val ACTION_NEXT = "dev.aurelis.symphony.WIDGET_NEXT"
        const val ACTION_REFRESH = "dev.aurelis.symphony.WIDGET_REFRESH"
        private const val STORE = "aurelis_widget_state"
        private const val COVER_CACHE = "aurelis_widget_cover.jpg"
        private const val TAG = "AurelisWidget"
        private val images = Executors.newSingleThreadExecutor()
        // Cached per active song and theme. Frequent play/pause, queue and
        // launcher callbacks reuse the same modest-sized bitmaps instead of
        // recomputing multiple gradients, JPEG decodes and bottom blur layers.
        private val bitmapCacheLock = Any()
        private var cachedCoverKey: String? = null
        private var cachedCoverBitmap: Bitmap? = null
        private var cachedBackdropKey: String? = null
        private val cachedBackdrops = mutableMapOf<Size, Bitmap>()
        private var cachedHeroKey: String? = null
        private val cachedHeroArt = mutableMapOf<Size, Bitmap>()
        private val queueArtLock = Any()
        private val pendingQueueArt = mutableSetOf<String>()
        private val failedQueueArt = mutableMapOf<String, Long>()
        @Volatile private var inFlightArt: String? = null
        @Volatile private var failedArtwork: String? = null
        @Volatile private var failedAt: Long = 0L

        private enum class Size(val layout: Int, val width: Int, val height: Int, val controls: Boolean) {
            TWO_ONE(R.layout.widget_aurelis_small, 252, 112, false),
            TWO_TWO(R.layout.widget_aurelis_compact, 240, 240, true),
            FOUR_ONE(R.layout.widget_aurelis_slim, 480, 112, true),
            FOUR_TWO(R.layout.widget_aurelis, 480, 244, true)
        }

        private val providers = listOf(
            AurelisWidgetProvider::class.java,
            AurelisWidgetTwoByOneProvider::class.java,
            AurelisWidgetTwoByTwoProvider::class.java,
            AurelisWidgetFourByOneProvider::class.java
        )

        private val videoPattern = Regex("[A-Za-z0-9_-]{11}")

        private fun videoId(song: Song): String {
            if (videoPattern.matches(song.videoId)) return song.videoId
            val fromKey = song.id.removePrefix("yt-")
            return if (song.id.startsWith("yt-") && videoPattern.matches(fromKey)) fromKey else ""
        }

        private fun primaryArtwork(song: Song): String {
            val raw = song.artwork.trim()
            if (raw.startsWith("https://")) return raw
            if (raw.startsWith("/") && !raw.startsWith("//"))
                return "https://aurelis-symphony.go.altare.global$raw"
            val id = videoId(song)
            return if (id.isNotBlank()) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else ""
        }

        fun publish(context: Context, song: Song?, playing: Boolean, accent: Int) {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            // Avoid waking launchers and sending RemoteViews repeatedly when
            // a session callback re-publishes exactly the same information.
            val artworkUrl = song?.let(::primaryArtwork)
            val video = song?.let(::videoId)
            val trackChanged = song != null && (
                prefs.getString("title", "") != song.title ||
                prefs.getString("artist", "") != song.artist ||
                prefs.getString("artwork", "") != artworkUrl ||
                prefs.getString("video_id", "") != video ||
                prefs.getString("song_key", "") != song.key
            )
            val statusChanged =
                prefs.getBoolean("playing", false) != playing ||
                prefs.getInt("accent", 0xFF315A4C.toInt()) != accent
            if (!trackChanged && !statusChanged) return
            val editor = prefs.edit()
            if (song != null && trackChanged) {
                editor.putString("title", song.title)
                    .putString("artist", song.artist)
                    .putString("artwork", artworkUrl)
                    .putString("video_id", video)
                    .putString("song_key", song.key)
            }
            editor.putBoolean("playing", playing).putInt("accent", accent).apply()
            updateWidgets(context)
        }


        /** Decode toward ~700px before scaling, with bounded peak memory. */
        private fun decodeArtwork(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0 || longest > 16000) return null
            var sample = 1
            while (longest / sample > 720) sample *= 2
            return BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }

        /**
         * 2x2 and 4x2 Now Playing: keep source artwork crisp up top, but
         * gently blur only the lower half under white controls and title.
         * A low-res blurred pass is alpha-feathered in, so no horizontal seam.
         * Final sizes are fixed per widget and cached until the cover changes.
         */
        private fun renderHeroArtwork(source: Bitmap, width: Int, height: Int): Bitmap {
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val sourceRatio = source.width.toFloat() / source.height.coerceAtLeast(1)
            val targetRatio = width.toFloat() / height.coerceAtLeast(1)
            val srcRect = if (sourceRatio > targetRatio) {
                val crop = (source.height * targetRatio).toInt().coerceIn(1, source.width)
                val x = (source.width - crop) / 2
                Rect(x, 0, x + crop, source.height)
            } else {
                val crop = (source.width / targetRatio).toInt().coerceIn(1, source.height)
                val y = (source.height - crop) / 2
                Rect(0, y, source.width, y + crop)
            }
            canvas.drawBitmap(source, srcRect, Rect(0, 0, width, height), paint)

            val bottomStart = (height * .38f).toInt()
            val bottomHeight = (height - bottomStart).coerceAtLeast(1)
            val portion = Bitmap.createBitmap(result, 0, bottomStart, width, bottomHeight)
            val low = Bitmap.createScaledBitmap(
                portion, (width / 17).coerceAtLeast(12),
                (bottomHeight / 17).coerceAtLeast(10), true
            )
            val blurredLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val blurredCanvas = Canvas(blurredLayer)
            blurredCanvas.drawBitmap(low, null, Rect(0, bottomStart, width, height), paint)
            val mask = Paint(Paint.ANTI_ALIAS_FLAG)
            mask.shader = LinearGradient(
                0f, bottomStart.toFloat(), 0f, height.toFloat(),
                intArrayOf(Color.TRANSPARENT, 0xCFFFFFFF.toInt(), Color.WHITE),
                floatArrayOf(0f, .63f, 1f), Shader.TileMode.CLAMP
            )
            mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            blurredCanvas.drawRect(
                0f, bottomStart.toFloat(), width.toFloat(), height.toFloat(), mask
            )
            mask.xfermode = null
            mask.shader = null
            canvas.drawBitmap(blurredLayer, 0f, 0f, paint)
            portion.recycle()
            low.recycle()
            blurredLayer.recycle()
            return result
        }

        /**
         * Reuse artwork bytes downloaded by the main player for its ambient
         * palette, instead of depending on a second widget-only request.
         */
        fun cacheArtworkBytes(context: Context, song: Song, bytes: ByteArray) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val url = primaryArtwork(song)
            if (url.isBlank() || prefs.getString("song_key", "") != song.key) return
            runCatching {
                if (bytes.size > 5_000_000) return
                val decoded = decodeArtwork(bytes) ?: return
                val small = Bitmap.createScaledBitmap(decoded, 384, 384, true)
                if (small !== decoded) decoded.recycle()
                val tmp = File(app.cacheDir, "$COVER_CACHE.player.tmp")
                tmp.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 89, it) }
                small.recycle()
                if (prefs.getString("song_key", "") != song.key) {
                    tmp.delete()
                    return
                }
                if (tmp.renameTo(File(app.cacheDir, COVER_CACHE))) {
                    prefs.edit().putString("cached_artwork", url).apply()
                    failedArtwork = null
                    updateWidgets(app)
                }
            }.onFailure { Log.w(TAG, "Could not cache main-player artwork", it) }
        }

        /**
         * Mirror AurelisState's playlist/radio queue, never invent tracks.
         * Three album cover rows remain readable in a compact 4x2 widget.
         */
        fun publishQueue(context: Context, queue: List<Song>) {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val tracks = listOf(queue.getOrNull(0), queue.getOrNull(1), queue.getOrNull(2))
            val names = listOf("one", "two", "three")
            val entries = mutableListOf<Pair<String, String>>()
            tracks.forEachIndexed { i, song ->
                val name = names[i]
                entries.add("next_$name" to song?.title.orEmpty())
                entries.add("next_${name}_artist" to song?.artist.orEmpty())
                entries.add("next_${name}_art" to (song?.let(::primaryArtwork).orEmpty()))
                entries.add("next_${name}_video" to (song?.let(::videoId).orEmpty()))
            }
            if (entries.all { (key, value) -> prefs.getString(key, "") == value } &&
                prefs.getInt("next_queue_total", 0) == queue.size) return
            val editor = prefs.edit()
            entries.forEach { (key, value) -> editor.putString(key, value) }
            editor.putInt("next_queue_total", queue.size)
            editor.apply()
            updateWidgets(context)
        }

        fun publishFromSession(context: Context, media: MediaItem?, playing: Boolean) {
            if (media == null) {
                context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                    .edit().putBoolean("playing", playing).apply()
                updateWidgets(context)
                return
            }
            val metadata = media.mediaMetadata
            val title = metadata.title?.toString().orEmpty()
            if (title.isBlank()) return
            val snapshot = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            publish(context, Song(
                id = media.mediaId.ifEmpty { title },
                title = title,
                artist = metadata.artist?.toString() ?: "Aurelis Symphony",
                artwork = metadata.artworkUri?.toString().orEmpty()
            ), playing, snapshot.getInt("accent", 0xFF315A4C.toInt()))
        }

        fun updateWidgets(context: Context, widgetIds: IntArray? = null) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = widgetIds ?: providers.flatMap { provider ->
                manager.getAppWidgetIds(ComponentName(app, provider)).toList()
            }.distinct().toIntArray()
            if (ids.isEmpty()) return
            val store = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val theme = app.getSharedPreferences("aurelis_native", Context.MODE_PRIVATE)
                .getString("visualStyle", "GLASS")
            val glass = theme != "AMBIENT"
            val artUrl = store.getString("artwork", "").orEmpty()
            val videoId = store.getString("video_id", "").orEmpty()
            val file = File(app.cacheDir, COVER_CACHE)
            val validArt = artUrl.isNotBlank() &&
                artUrl == store.getString("cached_artwork", "") && file.isFile
            val artCacheKey = if (validArt) {
                "$artUrl:${file.lastModified()}:${file.length()}"
            } else "none"
            val art = if (validArt) synchronized(bitmapCacheLock) {
                if (cachedCoverKey != artCacheKey) {
                    cachedCoverBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    cachedCoverKey = artCacheKey
                }
                cachedCoverBitmap
            } else null
            val accent = store.getInt("accent", 0xFF315A4C.toInt())
            val playing = store.getBoolean("playing", false)
            val title = store.getString("title", "Aurelis Symphony").orEmpty()
            val artist = store.getString("artist", "Musik untuk harimu").orEmpty()
            val first = store.getString("next_one", "").orEmpty()
            val second = store.getString("next_two", "").orEmpty()
            val backdropKey = "$glass:$accent:$artCacheKey"
            val queueThumbs = mutableMapOf<String, Bitmap?>()
            val queuedArtToFetch = mutableSetOf<Triple<String, String, String>>()
            val queueCoverIds = listOf(
                Triple("one", R.id.widget_next_one_art, R.id.widget_next_one_row),
                Triple("two", R.id.widget_next_two_art, R.id.widget_next_two_row),
                Triple("three", R.id.widget_next_three_art, R.id.widget_next_three_row)
            )

            ids.forEach { id ->
                val provider = manager.getAppWidgetInfo(id)?.provider?.className.orEmpty()
                val size = when (provider) {
                    AurelisWidgetTwoByOneProvider::class.java.name -> Size.TWO_ONE
                    AurelisWidgetTwoByTwoProvider::class.java.name -> Size.TWO_TWO
                    AurelisWidgetFourByOneProvider::class.java.name -> Size.FOUR_ONE
                    else -> Size.FOUR_TWO // Existing widget IDs remain valid.
                }
                val remote = RemoteViews(app.packageName, size.layout)
                val background = synchronized(bitmapCacheLock) {
                    if (cachedBackdropKey != backdropKey) {
                        cachedBackdrops.clear()
                        cachedBackdropKey = backdropKey
                    }
                    cachedBackdrops.getOrPut(size) {
                        renderBackground(glass, accent, art, size.width, size.height)
                    }
                }
                remote.setImageViewBitmap(R.id.widget_background, background)
                val hero = size == Size.TWO_TWO || size == Size.FOUR_TWO
                if (hero && art == null) {
                    // Let the unbroken tinted ambient background stand in
                    // instead of scaling a music-note vector to card size.
                    remote.setViewVisibility(R.id.widget_art, View.GONE)
                } else {
                    remote.setViewVisibility(R.id.widget_art, View.VISIBLE)
                    if (art != null && hero) {
                        val heroBitmap = synchronized(bitmapCacheLock) {
                            if (cachedHeroKey != artCacheKey) {
                                cachedHeroArt.clear()
                                cachedHeroKey = artCacheKey
                            }
                            cachedHeroArt.getOrPut(size) {
                                if (size == Size.TWO_TWO) renderHeroArtwork(art, 240, 240)
                                else renderHeroArtwork(art, 256, 244)
                            }
                        }
                        remote.setImageViewBitmap(R.id.widget_art, heroBitmap)
                    } else if (art != null) {
                        remote.setImageViewBitmap(R.id.widget_art, art)
                    } else {
                        remote.setImageViewResource(
                            R.id.widget_art, R.drawable.widget_music_note
                        )
                    }
                }
                remote.setTextViewText(R.id.widget_title, title)
                remote.setTextViewText(R.id.widget_artist, artist)

                val openIntent = Intent(app, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val openPending = PendingIntent.getActivity(
                    app, id * 10, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                remote.setOnClickPendingIntent(R.id.widget_root, openPending)
                remote.setOnClickPendingIntent(R.id.widget_art, openPending)
                remote.setOnClickPendingIntent(R.id.widget_title, openPending)
                remote.setOnClickPendingIntent(R.id.widget_artist, openPending)
                if (size.controls) {
                    remote.setImageViewResource(R.id.widget_toggle,
                        if (playing) R.drawable.widget_pause else R.drawable.widget_play)
                    remote.setOnClickPendingIntent(R.id.widget_toggle,
                        actionIntent(app, id * 10 + 1, ACTION_TOGGLE))
                    remote.setOnClickPendingIntent(R.id.widget_prev,
                        actionIntent(app, id * 10 + 2, ACTION_PREVIOUS))
                    remote.setOnClickPendingIntent(R.id.widget_next,
                        actionIntent(app, id * 10 + 3, ACTION_NEXT))
                }
                if (size == Size.FOUR_TWO) {
                    val count = store.getInt("next_queue_total", 0)
                    remote.setTextViewText(R.id.widget_queue_header,
                        if (count > 3) "UP NEXT  ·  3 / $count"
                        else "UP NEXT")
                    queueCoverIds.forEachIndexed { index, (name, coverId, rowId) ->
                        val nextTitle = store.getString("next_$name", "").orEmpty()
                        val nextArtist = store.getString("next_${name}_artist", "").orEmpty()
                        val nextUrl = store.getString("next_${name}_art", "").orEmpty()
                        val nextVideo = store.getString("next_${name}_video", "").orEmpty()
                        val titleId = when (index) {
                            0 -> R.id.widget_next_one
                            1 -> R.id.widget_next_two
                            else -> R.id.widget_next_three
                        }
                        val artistId = when (index) {
                            0 -> R.id.widget_next_one_artist
                            1 -> R.id.widget_next_two_artist
                            else -> R.id.widget_next_three_artist
                        }
                        // Empty queue: show one honest status instead of
                        // multiple fake or duplicated suggestions.
                        remote.setViewVisibility(rowId,
                            if (nextTitle.isNotBlank() || index == 0) View.VISIBLE else View.GONE)
                        remote.setTextViewText(titleId, nextTitle.ifBlank { "Antrean masih kosong" })
                        remote.setTextViewText(artistId,
                            if (nextTitle.isBlank()) "Putar radio atau playlist" else nextArtist)
                        val thumbFile = File(app.cacheDir, "aurelis_widget_next_$name.jpg")
                        val valid = nextUrl.isNotBlank() && thumbFile.isFile &&
                            store.getString("cached_next_${name}_art", "") == nextUrl
                        val thumb = if (valid) queueThumbs.getOrPut("$name:$nextUrl") {
                            BitmapFactory.decodeFile(thumbFile.absolutePath)
                        } else null
                        if (thumb != null) remote.setImageViewBitmap(coverId, thumb)
                        else remote.setImageViewResource(coverId, R.drawable.widget_music_note)
                        if (nextTitle.isNotBlank() && nextUrl.startsWith("https://")
                            && thumb == null) {
                            queuedArtToFetch.add(Triple(name, nextUrl, nextVideo))
                        }
                    }
                }
                runCatching { manager.updateAppWidget(id, remote) }
                    .onFailure { Log.w(TAG, "Failed to update widget $id", it) }
            }
            if (artUrl.startsWith("https://") && art == null) loadArtwork(app, artUrl, videoId)
            queuedArtToFetch.forEach { (name, url, video) ->
                loadQueueArtwork(app, name, url, video)
            }
        }

        private fun actionIntent(context: Context, code: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, AurelisWidgetProvider::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openAurelis(context: Context) {
            runCatching {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }

        private fun downloadArtwork(url: String): ByteArray? {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            }
            return try {
                if (connection.responseCode !in 200..299) return null
                if (connection.contentLength > 5_000_000) return null
                connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 5_000_000) return null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun loadArtwork(context: Context, url: String, videoId: String) {
            if (inFlightArt == url) return
            val now = SystemClock.elapsedRealtime()
            if (failedArtwork == url && now - failedAt < 60_000L) return
            synchronized(this) {
                if (inFlightArt == url) return
                inFlightArt = url
            }
            images.execute {
                try {
                    val fallback = if (videoPattern.matches(videoId))
                        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
                    val candidates = listOf(url, fallback).filter { it.startsWith("https://") }.distinct()
                    var saved = false
                    for (candidate in candidates) {
                        val bytes = try { downloadArtwork(candidate) }
                        catch (e: Exception) {
                            Log.w(TAG, "Artwork unavailable from " + URL(candidate).host, e)
                            null
                        } ?: continue
                        val bitmap = decodeArtwork(bytes) ?: continue
                        val small = Bitmap.createScaledBitmap(bitmap, 384, 384, true)
                        if (small !== bitmap) bitmap.recycle()
                        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                        if (prefs.getString("artwork", "") != url) {
                            small.recycle()
                            return@execute
                        }
                        val temporary = File(context.cacheDir, "$COVER_CACHE.network.tmp")
                        temporary.outputStream().use {
                            small.compress(Bitmap.CompressFormat.JPEG, 89, it)
                        }
                        small.recycle()
                        if (prefs.getString("artwork", "") != url) {
                            temporary.delete()
                            return@execute
                        }
                        if (temporary.renameTo(File(context.cacheDir, COVER_CACHE))) {
                            prefs.edit().putString("cached_artwork", url).apply()
                            saved = true
                            failedArtwork = null
                            updateWidgets(context)
                            break
                        }
                    }
                    if (!saved) {
                        failedArtwork = url
                        failedAt = SystemClock.elapsedRealtime()
                    }
                } catch (e: Exception) {
                    failedArtwork = url
                    failedAt = SystemClock.elapsedRealtime()
                    Log.w(TAG, "Widget artwork cache unavailable", e)
                } finally {
                    if (inFlightArt == url) inFlightArt = null
                }
            }
        }

        /**
         * Queue covers are cached per slot. Requests are bounded, retried on
         * later updates, and skipped if the playlist changes mid-download.
         */
        private fun loadQueueArtwork(context: Context, name: String, url: String, videoId: String) {
            val key = "$name|$url"
            val now = SystemClock.elapsedRealtime()
            synchronized(queueArtLock) {
                if (pendingQueueArt.contains(key)) return
                if (now - (failedQueueArt[key] ?: -120_000L) < 60_000L) return
                pendingQueueArt.add(key)
            }
            images.execute {
                try {
                    val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                    if (prefs.getString("next_${name}_art", "") != url) return@execute
                    val fallback = if (videoPattern.matches(videoId))
                        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
                    var cached = false
                    for (candidate in listOf(url, fallback).filter {
                        it.startsWith("https://")
                    }.distinct()) {
                        val bytes = runCatching { downloadArtwork(candidate) }.getOrNull()
                            ?: continue
                        val decoded = BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inSampleSize = 3 }
                        ) ?: continue
                        val thumb = Bitmap.createScaledBitmap(decoded, 88, 88, true)
                        if (thumb !== decoded) decoded.recycle()
                        if (prefs.getString("next_${name}_art", "") != url) {
                            thumb.recycle()
                            return@execute
                        }
                        val temporary = File(context.cacheDir, "aurelis_widget_next_$name.tmp")
                        temporary.outputStream().use {
                            thumb.compress(Bitmap.CompressFormat.JPEG, 85, it)
                        }
                        thumb.recycle()
                        if (prefs.getString("next_${name}_art", "") != url) {
                            temporary.delete()
                            return@execute
                        }
                        if (temporary.renameTo(
                                File(context.cacheDir, "aurelis_widget_next_$name.jpg")
                            )) {
                            prefs.edit().putString("cached_next_${name}_art", url).apply()
                            cached = true
                            updateWidgets(context)
                            break
                        }
                    }
                    if (!cached) {
                        synchronized(queueArtLock) { failedQueueArt[key] = SystemClock.elapsedRealtime() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load Up Next thumbnail", e)
                    synchronized(queueArtLock) { failedQueueArt[key] = SystemClock.elapsedRealtime() }
                } finally {
                    synchronized(queueArtLock) { pendingQueueArt.remove(key) }
                }
            }
        }

        private fun mix(first: Int, second: Int, rate: Float): Int {
            val t = rate.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(first) * (1f - t) + Color.red(second) * t).toInt(),
                (Color.green(first) * (1f - t) + Color.green(second) * t).toInt(),
                (Color.blue(first) * (1f - t) + Color.blue(second) * t).toInt()
            )
        }

        private fun rgba(color: Int, a: Int) =
            Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

        /**
         * Single continuous colour field. The previous version stamped a
         * rectangular upscaled artwork bitmap onto the canvas, which produced
         * a hard vertical seam at one third of the widget on some launchers.
         * Sample the cover's color instead and apply full-canvas soft glows.
         */
        private fun renderBackground(
            glass: Boolean, accent: Int, artwork: Bitmap?, width: Int, height: Int
        ): Bitmap {
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val sample = if (artwork != null && !artwork.isRecycled &&
                artwork.width > 0 && artwork.height > 0) {
                artwork.getPixel(artwork.width / 2, artwork.height / 2)
            } else accent
            val coverAccent = mix(accent, sample, if (glass) .30f else .12f)
            val quietBase = mix(coverAccent, 0xFF14242A.toInt(), if (glass) .76f else .87f)
            val top = mix(quietBase, 0xFF273B42.toInt(), .30f)
            paint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                if (glass) intArrayOf(
                    rgba(top, 236),
                    rgba(quietBase, 241),
                    0xF00E1C23.toInt()
                ) else intArrayOf(
                    0xFF1A2D2B.toInt(), quietBase, 0xFF111D22.toInt()
                ),
                floatArrayOf(0f, .54f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            val softLight = mix(coverAccent, 0xFFB6FFE9.toInt(), .58f)
            // Shader is evaluated at every pixel of the full surface: no
            // crop / rectangles / hard gradient boundaries at any size.
            paint.shader = RadialGradient(
                width * .10f, height * .04f, width * 1.06f,
                intArrayOf(
                    rgba(softLight, if (glass) 61 else 22),
                    rgba(softLight, if (glass) 14 else 5),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = RadialGradient(
                width * .94f, height * 1.04f, width * .86f,
                intArrayOf(
                    rgba(mix(coverAccent, 0xFF6CA1C8.toInt(), .60f), if (glass) 26 else 8),
                    Color.TRANSPARENT
                ), null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            return output
        }

    }
}

/** Four separate picker entries; no app-widget resize handles. */
class AurelisWidgetTwoByOneProvider : AurelisWidgetProvider()
class AurelisWidgetTwoByTwoProvider : AurelisWidgetProvider()
class AurelisWidgetFourByOneProvider : AurelisWidgetProvider()
