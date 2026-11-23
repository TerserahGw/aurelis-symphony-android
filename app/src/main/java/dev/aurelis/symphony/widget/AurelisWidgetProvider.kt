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
        private var cachedMiniKey: String? = null
        private var cachedMiniBitmap: Bitmap? = null
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
            // Smaller gradient bitmap prevents launcher Binder oversize when
            // five queue covers and a sharper hero are present.
            FOUR_TWO(R.layout.widget_aurelis, 360, 184, true)
        }

        private val providers = listOf(
            AurelisWidgetProvider::class.java,
            AurelisWidgetTwoByOneProvider::class.java,
            AurelisWidgetTwoByTwoProvider::class.java,
            AurelisWidgetFourByOneProvider::class.java
        )

        private val videoPattern = Regex("[A-Za-z0-9_-]{11}")
        private const val ART_CACHE_VERSION = 2
        private const val HERO_CACHE_SIZE = 768
        private val googleSizePattern = Regex("=w[0-9]+-h[0-9]+[^?]*")
        private val googleSquarePattern = Regex("=s[0-9]+[^?]*")
        private val youtubeThumbPattern = Regex("/(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg")

        private fun videoId(song: Song): String {
            if (videoPattern.matches(song.videoId)) return song.videoId
            val fromKey = song.id.removePrefix("yt-")
            return if (song.id.startsWith("yt-") && videoPattern.matches(fromKey)) fromKey else ""
        }

        private fun originalArtwork(song: Song): String {
            val raw = song.artwork.trim()
            if (raw.startsWith("https://")) return raw
            if (raw.startsWith("/") && !raw.startsWith("//"))
                return "https://aurelis-symphony.go.altare.global$raw"
            return videoId(song).takeIf { it.isNotBlank() }
                ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
        }

        private fun upgradedArtwork(url: String): String {
            if (!url.startsWith("https://")) return url
            val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
            return when {
                host.endsWith("googleusercontent.com") || host.endsWith("ggpht.com") -> {
                    when {
                        googleSizePattern.containsMatchIn(url) ->
                            url.replace(googleSizePattern, "=w1080-h1080-l90-rj")
                        googleSquarePattern.containsMatchIn(url) ->
                            url.replace(googleSquarePattern, "=s1080")
                        else -> url
                    }
                }
                host.endsWith("ytimg.com") && youtubeThumbPattern.containsMatchIn(url) ->
                    url.replace(youtubeThumbPattern, "/maxresdefault.jpg")
                else -> url
            }
        }

        private fun artworkCandidates(
            url: String, videoId: String, originalUrl: String = ""
        ): List<String> {
            val candidates = mutableListOf<String>()
            fun add(value: String) {
                if (value.startsWith("https://") && !candidates.contains(value))
                    candidates.add(value)
            }
            add(upgradedArtwork(url))
            add(url)
            // Keep the original source when the higher-res variant is blocked.
            add(originalUrl)
            // Only fall back to a YouTube video thumbnail if the genuine album
            // cover cannot be fetched in good resolution.
            val embeddedVideo = Regex("/vi/([A-Za-z0-9_-]{11})/")
                .find(url)?.groupValues?.getOrNull(1).orEmpty()
            val fallbackVideo = if (videoPattern.matches(videoId))
                videoId else embeddedVideo
            if (videoPattern.matches(fallbackVideo)) {
                for (quality in listOf("maxresdefault", "sddefault", "hqdefault"))
                    add("https://i.ytimg.com/vi/$fallbackVideo/$quality.jpg")
            }
            return candidates
        }

        private fun primaryArtwork(song: Song) = upgradedArtwork(originalArtwork(song))

        fun publish(context: Context, song: Song?, playing: Boolean, accent: Int) {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            // Avoid waking launchers and sending RemoteViews repeatedly when
            // a session callback re-publishes exactly the same information.
            val artworkUrl = song?.let(::primaryArtwork)
            val originalUrl = song?.let(::originalArtwork)
            val video = song?.let(::videoId)
            val trackChanged = song != null && (
                prefs.getString("title", "") != song.title ||
                prefs.getString("artist", "") != song.artist ||
                prefs.getString("artwork", "") != artworkUrl ||
                prefs.getString("original_artwork", "") != originalUrl ||
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
                    .putString("original_artwork", originalUrl)
                    .putString("video_id", video)
                    .putString("song_key", song.key)
            }
            editor.putBoolean("playing", playing).putInt("accent", accent).apply()
            updateWidgets(context)
        }


        /** Decode HD art toward 1280px; final RemoteViews bitmaps stay small. */
        private fun decodeArtwork(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0 || longest > 16000) return null
            var sample = 1
            while (longest / sample > 1280) sample *= 2
            return BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }

        /**
         * Preserve the square crop without distorting album art. The file is
         * high enough resolution for launcher xhdpi/xxhdpi hero cards; only
         * the fixed-size 320/336px render is sent through RemoteViews.
         */
        private fun squareCover(source: Bitmap, size: Int): Bitmap {
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val shortest = minOf(source.width, source.height)
            val x = (source.width - shortest) / 2
            val y = (source.height - shortest) / 2
            Canvas(output).drawBitmap(
                source, Rect(x, y, x + shortest, y + shortest),
                Rect(0, 0, size, size),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            return output
        }

        private fun saveCover(
            context: Context, url: String, decoded: Bitmap,
            sourceQuality: Int, temporaryName: String, cacheVersion: Int
        ): Boolean {
            val app = context.applicationContext
            val store = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            if (store.getString("artwork", "") != url) return false
            val oldQuality = store.getInt("cached_art_quality", 0)
            // Never replace an already fetched HD image with a later,
            // lower-resolution palette image or stale network fallback.
            if (store.getString("cached_artwork", "") == url &&
                store.getInt("cached_art_version", 0) >= cacheVersion &&
                oldQuality >= sourceQuality) return false
            val square = squareCover(decoded, HERO_CACHE_SIZE)
            val temp = File(app.cacheDir, temporaryName)
            try {
                temp.outputStream().use {
                    square.compress(Bitmap.CompressFormat.JPEG, 94, it)
                }
                if (store.getString("artwork", "") != url) return false
                if (!temp.renameTo(File(app.cacheDir, COVER_CACHE))) return false
                store.edit().putString("cached_artwork", url)
                    .putInt("cached_art_quality", sourceQuality)
                    .putInt("cached_art_version", cacheVersion).apply()
                failedArtwork = null
                updateWidgets(app)
                return true
            } finally {
                square.recycle()
                temp.delete()
            }
        }

        /**
         * 2x2 and 4x2 Now Playing: keep source artwork crisp up top, but
         * gently blur only the lower half under white controls and title.
         * A low-res blurred pass is alpha-feathered in, so no horizontal seam.
         * Final sizes are fixed per widget and cached until the cover changes.
         */
        private fun renderHeroArtwork(
            source: Bitmap, width: Int, height: Int, fadeRight: Boolean = false
        ): Bitmap {
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

            if (fadeRight) {
                // Soften the vertical meeting line between the cover hero
                // and the Up Next card. A second blurred right-side strip
                // avoids a hard crop; alpha then feathers it gradually into
                // the widget's existing continuous glass background.
                val featherStart = width * .74f
                val blurred = Bitmap.createScaledBitmap(
                    result, (width / 14).coerceAtLeast(12),
                    (height / 14).coerceAtLeast(12), true
                )
                val softRight = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val softCanvas = Canvas(softRight)
                softCanvas.drawBitmap(
                    blurred, null, Rect(0, 0, width, height), paint
                )
                val softMask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        featherStart, 0f, width.toFloat(), 0f,
                        intArrayOf(Color.TRANSPARENT, 0xAAFFFFFF.toInt()),
                        null, Shader.TileMode.CLAMP
                    )
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                softCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), softMask)
                softMask.shader = null
                softMask.xfermode = null
                canvas.drawBitmap(softRight, 0f, 0f, paint)
                val edgeMask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        featherStart, 0f, width.toFloat(), 0f,
                        intArrayOf(Color.WHITE, 0x00FFFFFF),
                        null, Shader.TileMode.CLAMP
                    )
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), edgeMask)
                edgeMask.shader = null
                edgeMask.xfermode = null
                blurred.recycle()
                softRight.recycle()
            }
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
            if (url.isBlank() || prefs.getString("song_key", "") != song.key ||
                bytes.size > 5_000_000) return
            runCatching {
                val decoded = decodeArtwork(bytes) ?: return
                try {
                    val quality = minOf(decoded.width, decoded.height)
                    // The player may have fetched a tiny thumbnail for its
                    // palette. Show it temporarily, but allow the widget's
                    // dedicated HD request to replace it.
                    saveCover(
                        app, url, decoded, quality,
                        "$COVER_CACHE.player.tmp",
                        if (quality >= 520) ART_CACHE_VERSION else 1
                    )
                } finally {
                    decoded.recycle()
                }
            }.onFailure { Log.w(TAG, "Could not cache main-player artwork", it) }
        }

        /**
         * Mirror AurelisState's playlist/radio queue, never invent tracks.
         * Up to five compact, readable covers appear in the 4x2 widget.
         */
        fun publishQueue(context: Context, queue: List<Song>) {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val tracks = (0 until 5).map(queue::getOrNull)
            val names = listOf("one", "two", "three", "four", "five")
            val entries = mutableListOf<Pair<String, String>>()
            tracks.forEachIndexed { i, song ->
                val name = names[i]
                entries.add("next_$name" to song?.title.orEmpty())
                entries.add("next_${name}_artist" to song?.artist.orEmpty())
                entries.add("next_${name}_art" to (song?.let(::primaryArtwork).orEmpty()))
                entries.add("next_${name}_original_art" to (song?.let(::originalArtwork).orEmpty()))
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

        /**
         * Native widget TextView.setSelected is not a safe RemoteViews action
         * on every Android launcher. ViewFlipper is a supported remote view,
         * so long titles slide between two readable overlapping segments.
         * Short titles remain static (no unnecessary looping/recomposition).
         */
        private fun updateSlidingTitle(
            views: RemoteViews, originalId: Int, flipperId: Int,
            firstId: Int, secondId: Int, title: String, maxCharacters: Int
        ) {
            val text = title.trim()
            val codePoints = text.codePointCount(0, text.length)
            val shouldSlide = codePoints > maxCharacters
            views.setViewVisibility(originalId, if (shouldSlide) View.GONE else View.VISIBLE)
            views.setViewVisibility(flipperId, if (shouldSlide) View.VISIBLE else View.GONE)
            if (!shouldSlide) return
            val estimatedEnd = text.offsetByCodePoints(
                0, minOf(maxCharacters, codePoints)
            )
            val lastSpace = text.lastIndexOf(' ', estimatedEnd.coerceAtMost(text.lastIndex))
            val split = if (lastSpace in (estimatedEnd - 10)..estimatedEnd &&
                lastSpace > 0) lastSpace else estimatedEnd
            val overlapStart = (split - 7).coerceAtLeast(0)
            val first = text.substring(0, split).trim()
            val second = text.substring(overlapStart).trim()
            views.setTextViewText(firstId, first)
            views.setTextViewText(secondId, second)
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
            val needsHdRefresh = store.getInt("cached_art_version", 0) < ART_CACHE_VERSION
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
                Triple("three", R.id.widget_next_three_art, R.id.widget_next_three_row),
                Triple("four", R.id.widget_next_four_art, R.id.widget_next_four_row),
                Triple("five", R.id.widget_next_five_art, R.id.widget_next_five_row)
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
                                if (size == Size.TWO_TWO) renderHeroArtwork(art, 320, 320)
                                else renderHeroArtwork(art, 336, 320, fadeRight = true)
                            }
                        }
                        remote.setImageViewBitmap(R.id.widget_art, heroBitmap)
                    } else if (art != null) {
                        // Never send a 768x768 source Bitmap through Binder.
                        // Fixed-size mini covers only need a 120px square and
                        // the same small bitmap can be shared by all mini IDs.
                        val mini = synchronized(bitmapCacheLock) {
                            if (cachedMiniKey != artCacheKey || cachedMiniBitmap == null) {
                                cachedMiniBitmap = squareCover(art, 120)
                                cachedMiniKey = artCacheKey
                            }
                            cachedMiniBitmap!!
                        }
                        remote.setImageViewBitmap(R.id.widget_art, mini)
                    } else {
                        remote.setImageViewResource(
                            R.id.widget_art, R.drawable.widget_music_note
                        )
                    }
                }
                remote.setTextViewText(R.id.widget_title, title)
                remote.setTextViewText(R.id.widget_artist, artist)
                val maxTitleLength = when (size) {
                    Size.TWO_ONE -> 16
                    Size.TWO_TWO -> 23
                    Size.FOUR_ONE -> 34
                    Size.FOUR_TWO -> 26
                }
                updateSlidingTitle(
                    remote, R.id.widget_title, R.id.widget_title_flipper,
                    R.id.widget_title_slide_one, R.id.widget_title_slide_two,
                    title, maxTitleLength
                )

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
                    remote.setTextViewText(R.id.widget_queue_header, "UP NEXT")
                    queueCoverIds.forEachIndexed { index, (name, coverId, rowId) ->
                        val nextTitle = store.getString("next_$name", "").orEmpty()
                        val nextArtist = store.getString("next_${name}_artist", "").orEmpty()
                        val nextUrl = store.getString("next_${name}_art", "").orEmpty()
                        val nextVideo = store.getString("next_${name}_video", "").orEmpty()
                        val titleId = when (index) {
                            0 -> R.id.widget_next_one
                            1 -> R.id.widget_next_two
                            2 -> R.id.widget_next_three
                            3 -> R.id.widget_next_four
                            else -> R.id.widget_next_five
                        }
                        val artistId = when (index) {
                            0 -> R.id.widget_next_one_artist
                            1 -> R.id.widget_next_two_artist
                            2 -> R.id.widget_next_three_artist
                            3 -> R.id.widget_next_four_artist
                            else -> R.id.widget_next_five_artist
                        }
                        // Empty queue: show one honest status instead of
                        // multiple fake or duplicated suggestions.
                        remote.setViewVisibility(rowId,
                            if (nextTitle.isNotBlank() || index == 0) View.VISIBLE else View.GONE)
                        remote.setTextViewText(titleId, nextTitle.ifBlank { "Antrean masih kosong" })
                        val titleFlipperId = when (index) {
                            0 -> R.id.widget_next_one_flipper
                            1 -> R.id.widget_next_two_flipper
                            2 -> R.id.widget_next_three_flipper
                            3 -> R.id.widget_next_four_flipper
                            else -> R.id.widget_next_five_flipper
                        }
                        val titlePageOneId = when (index) {
                            0 -> R.id.widget_next_one_slide_one
                            1 -> R.id.widget_next_two_slide_one
                            2 -> R.id.widget_next_three_slide_one
                            3 -> R.id.widget_next_four_slide_one
                            else -> R.id.widget_next_five_slide_one
                        }
                        val titlePageTwoId = when (index) {
                            0 -> R.id.widget_next_one_slide_two
                            1 -> R.id.widget_next_two_slide_two
                            2 -> R.id.widget_next_three_slide_two
                            3 -> R.id.widget_next_four_slide_two
                            else -> R.id.widget_next_five_slide_two
                        }
                        updateSlidingTitle(remote, titleId, titleFlipperId,
                            titlePageOneId, titlePageTwoId, nextTitle, 23)
                        remote.setTextViewText(artistId,
                            if (nextTitle.isBlank()) "Putar radio atau playlist" else nextArtist)
                        val thumbFile = File(app.cacheDir, "aurelis_widget_next_$name.jpg")
                        val valid = nextUrl.isNotBlank() && thumbFile.isFile &&
                            store.getString("cached_next_${name}_art", "") == nextUrl
                        val needsThumbRefresh =
                            store.getInt("cached_next_${name}_version", 0) < ART_CACHE_VERSION
                        val thumb = if (valid) queueThumbs.getOrPut("$name:$nextUrl") {
                            BitmapFactory.decodeFile(thumbFile.absolutePath)
                        } else null
                        if (thumb != null) remote.setImageViewBitmap(coverId, thumb)
                        else remote.setImageViewResource(coverId, R.drawable.widget_music_note)
                        if (nextTitle.isNotBlank() && nextUrl.startsWith("https://")
                            && (thumb == null || needsThumbRefresh)) {
                            queuedArtToFetch.add(Triple(name, nextUrl, nextVideo))
                        }
                    }
                }
                runCatching { manager.updateAppWidget(id, remote) }
                    .onFailure { Log.w(TAG, "Failed to update widget $id", it) }
            }
            if (artUrl.startsWith("https://") && (art == null || needsHdRefresh))
                loadArtwork(app, artUrl, videoId)
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
            if (failedArtwork == url && now - failedAt < 90_000L) return
            synchronized(this) {
                if (inFlightArt == url) return
                inFlightArt = url
            }
            images.execute {
                try {
                    var best: Bitmap? = null
                    var bestQuality = 0
                    val original = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                        .getString("original_artwork", "").orEmpty()
                    for (candidate in artworkCandidates(url, videoId, original)) {
                        val bytes = try { downloadArtwork(candidate) }
                        catch (e: Exception) {
                            Log.w(TAG, "HD artwork unavailable from " +
                                URL(candidate).host, e)
                            null
                        } ?: continue
                        val decoded = decodeArtwork(bytes) ?: continue
                        val quality = minOf(decoded.width, decoded.height)
                        if (quality > bestQuality) {
                            best?.recycle()
                            best = decoded
                            bestQuality = quality
                        } else decoded.recycle()
                        // A genuinely large square source is already enough.
                        // Stop here instead of burning network and CPU for
                        // lower-quality alternate video-frame images.
                        if (quality >= 680) break
                    }
                    val chosen = best
                    if (chosen != null) {
                        try {
                            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                            val currentQuality = if (
                                prefs.getString("cached_artwork", "") == url)
                                prefs.getInt("cached_art_quality", 0) else 0
                            if (prefs.getInt("cached_art_version", 0) < ART_CACHE_VERSION ||
                                bestQuality > currentQuality) {
                                saveCover(context, url, chosen, bestQuality,
                                    "$COVER_CACHE.network.tmp", ART_CACHE_VERSION)
                            }
                        } finally { chosen.recycle() }
                    } else {
                        failedArtwork = url
                        failedAt = SystemClock.elapsedRealtime()
                    }
                } catch (e: Exception) {
                    failedArtwork = url
                    failedAt = SystemClock.elapsedRealtime()
                    Log.w(TAG, "Widget HD artwork unavailable", e)
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
                    var cached = false
                    val original = prefs.getString("next_${name}_original_art", "").orEmpty()
                    for (candidate in artworkCandidates(url, videoId, original)) {
                        val bytes = runCatching { downloadArtwork(candidate) }.getOrNull()
                            ?: continue
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val longest = maxOf(bounds.outWidth, bounds.outHeight)
                        if (longest <= 0 || longest > 16000) continue
                        var sample = 1
                        while (longest / sample > 480) sample *= 2
                        val decoded = BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inSampleSize = sample }
                        ) ?: continue
                        val thumb = squareCover(decoded, 104)
                        if (thumb !== decoded) decoded.recycle()
                        if (prefs.getString("next_${name}_art", "") != url) {
                            thumb.recycle()
                            return@execute
                        }
                        val temporary = File(context.cacheDir, "aurelis_widget_next_$name.tmp")
                        temporary.outputStream().use {
                            thumb.compress(Bitmap.CompressFormat.JPEG, 90, it)
                        }
                        thumb.recycle()
                        if (prefs.getString("next_${name}_art", "") != url) {
                            temporary.delete()
                            return@execute
                        }
                        if (temporary.renameTo(
                                File(context.cacheDir, "aurelis_widget_next_$name.jpg")
                            )) {
                            prefs.edit()
                                .putString("cached_next_${name}_art", url)
                                .putInt("cached_next_${name}_version", ART_CACHE_VERSION)
                                .apply()
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
