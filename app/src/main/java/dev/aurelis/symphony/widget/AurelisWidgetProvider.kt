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
import android.graphics.Shader
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
        private val images = Executors.newSingleThreadExecutor()
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

        fun publish(context: Context, song: Song?, playing: Boolean, accent: Int) {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (song != null) {
                editor.putString("title", song.title)
                    .putString("artist", song.artist)
                    .putString("artwork", song.artwork)
                    .putString("song_key", song.key)
            }
            editor.putBoolean("playing", playing).putInt("accent", accent).apply()
            updateWidgets(context)
        }

        /** The same real Up Next list shown by AurelisState, not a fake preview. */
        fun publishQueue(context: Context, queue: List<Song>) {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val first = queue.getOrNull(0)
            val second = queue.getOrNull(1)
            val values = listOf(
                first?.title.orEmpty(), first?.artist.orEmpty(),
                second?.title.orEmpty(), second?.artist.orEmpty()
            )
            val keys = listOf("next_one", "next_one_artist", "next_two", "next_two_artist")
            if (keys.indices.all { prefs.getString(keys[it], "") == values[it] }) return
            val editor = prefs.edit()
            keys.indices.forEach { editor.putString(keys[it], values[it]) }
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
            val file = File(app.cacheDir, COVER_CACHE)
            val art = if (artUrl.isNotBlank() &&
                artUrl == store.getString("cached_artwork", "") && file.isFile) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
            val accent = store.getInt("accent", 0xFF315A4C.toInt())
            val playing = store.getBoolean("playing", false)
            val title = store.getString("title", "Aurelis Symphony").orEmpty()
            val artist = store.getString("artist", "Musik untuk harimu").orEmpty()
            val first = store.getString("next_one", "").orEmpty()
            val second = store.getString("next_two", "").orEmpty()
            val backdrops = mutableMapOf<Size, Bitmap>()

            ids.forEach { id ->
                val provider = manager.getAppWidgetInfo(id)?.provider?.className.orEmpty()
                val size = when (provider) {
                    AurelisWidgetTwoByOneProvider::class.java.name -> Size.TWO_ONE
                    AurelisWidgetTwoByTwoProvider::class.java.name -> Size.TWO_TWO
                    AurelisWidgetFourByOneProvider::class.java.name -> Size.FOUR_ONE
                    else -> Size.FOUR_TWO // Existing widget IDs remain valid.
                }
                val remote = RemoteViews(app.packageName, size.layout)
                val background = backdrops.getOrPut(size) {
                    renderBackground(glass, accent, art, size.width, size.height)
                }
                remote.setImageViewBitmap(R.id.widget_background, background)
                if (art != null) remote.setImageViewBitmap(R.id.widget_art, art)
                else remote.setImageViewResource(R.id.widget_art, R.drawable.ic_aurelis_foreground)
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
                    remote.setTextViewText(R.id.widget_next_one,
                        first.ifBlank { "Belum ada antrean" })
                    remote.setTextViewText(R.id.widget_next_one_artist,
                        store.getString("next_one_artist", "").orEmpty()
                            .ifBlank { "Pilih lagu untuk memulai" })
                    remote.setTextViewText(R.id.widget_next_two,
                        second.ifBlank { "Rekomendasi berikutnya" })
                    remote.setTextViewText(R.id.widget_next_two_artist,
                        store.getString("next_two_artist", "").orEmpty()
                            .ifBlank { "Menunggu Up Next" })
                }
                manager.updateAppWidget(id, remote)
            }
            if (artUrl.startsWith("https://") && art == null) loadArtwork(app, artUrl)
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

        private fun loadArtwork(context: Context, url: String) {
            if (inFlightArt == url) return
            val now = SystemClock.elapsedRealtime()
            if (failedArtwork == url && now - failedAt < 180_000L) return
            synchronized(this) {
                if (inFlightArt == url) return
                inFlightArt = url
            }
            images.execute {
                try {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 4500
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "AurelisWidget/1.2.8")
                    }
                    val bytes = try {
                        if (connection.contentLength > 2_000_000) return@execute
                        connection.inputStream.use { stream ->
                            val out = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = stream.read(buffer)
                                if (n < 0) break
                                if (out.size() + n > 2_000_000) return@execute
                                out.write(buffer, 0, n)
                            }
                            out.toByteArray()
                        }
                    } finally { connection.disconnect() }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = 2 })
                        ?: return@execute
                    val small = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
                    if (small !== bitmap) bitmap.recycle()
                    val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                    if (prefs.getString("artwork", "") != url) {
                        small.recycle()
                        return@execute
                    }
                    val destination = File(context.cacheDir, COVER_CACHE)
                    val temporary = File(context.cacheDir, COVER_CACHE + ".tmp")
                    temporary.outputStream().use {
                        small.compress(Bitmap.CompressFormat.JPEG, 86, it)
                    }
                    small.recycle()
                    if (temporary.renameTo(destination)) {
                        prefs.edit().putString("cached_artwork", url).apply()
                        failedArtwork = null
                        updateWidgets(context)
                    }
                } catch (_: Exception) {
                    // Offline: leave the usable cover placeholder intact.
                } finally {
                    if (context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                            .getString("cached_artwork", "") != url) {
                        failedArtwork = url
                        failedAt = SystemClock.elapsedRealtime()
                    }
                    if (inFlightArt == url) inFlightArt = null
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

        private fun renderBackground(
            glass: Boolean, accent: Int, artwork: Bitmap?, width: Int, height: Int
        ): Bitmap {
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val base = mix(accent, 0xFF11252A.toInt(), if (glass) .63f else .82f)
            val bright = mix(accent, 0xFFA9F3DD.toInt(), .42f)
            paint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                if (glass) intArrayOf(
                    rgba(mix(base, 0xFF26383C.toInt(), .22f), 222),
                    rgba(base, 228), 0xE90A161D.toInt()
                ) else intArrayOf(
                    0xFF1A302E.toInt(), rgba(base, 255), 0xFF0C171D.toInt()
                ),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            if (glass && artwork != null) {
                // Artwork tint is subdued enough to keep small titles readable.
                val tiny = Bitmap.createScaledBitmap(artwork, 10, 10, true)
                paint.alpha = 38
                canvas.drawBitmap(tiny, null, Rect(width / 3, -height / 3,
                    width + width / 4, height + height / 3), paint)
                tiny.recycle()
                paint.alpha = 255
            }
            paint.shader = RadialGradient(
                width * .09f, height * .01f, width * .82f,
                intArrayOf(rgba(bright, if (glass) 87 else 34), 0x00000000),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = RadialGradient(
                width * .98f, height * .92f, width * .76f,
                intArrayOf(if (glass) 0x365A9BC6 else 0x142A7064, 0x00000000),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            // Soft top lip. Rounded contour is applied in dp by the XML root,
            // not pre-rasterized into a bitmap that the launcher can stretch.
            paint.color = if (glass) 0x27FFFFFF else 0x12FFFFFF
            canvas.drawRect(12f, 2f, width - 12f, 3.5f, paint)
            return image
        }
    }
}

/** Four separate picker entries; no app-widget resize handles. */
class AurelisWidgetTwoByOneProvider : AurelisWidgetProvider()
class AurelisWidgetTwoByTwoProvider : AurelisWidgetProvider()
class AurelisWidgetFourByOneProvider : AurelisWidgetProvider()
