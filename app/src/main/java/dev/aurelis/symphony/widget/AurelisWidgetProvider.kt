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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
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
 * Lightweight, real Android Home Screen widget.
 *
 * RemoteViews cannot blur the *live wallpaper* behind a widget. Instead the
 * GLASS theme uses a rounded, translucent artwork-reactive canvas with a
 * diffuse highlight, while AMBIENT is an opaque charcoal/evergreen card.
 * Both use actual playback data; no permanent background polling is needed.
 */
class AurelisWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, widgetId: Int, options: Bundle
    ) {
        updateWidgets(context, intArrayOf(widgetId))
    }

    override fun onEnabled(context: Context) {
        updateWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> transportToggle(context)
            ACTION_NEXT, ACTION_PREVIOUS -> {
                // Next/previous are playlist-aware while the Aurelis ViewModel
                // is alive. If the process was reclaimed, let the user pick
                // music in-app rather than silently skipping the wrong stream.
                if (!WidgetPlaybackBridge.transport(intent.action.orEmpty())) {
                    openAurelis(context)
                }
            }
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
        @Volatile private var attemptedArt: String? = null

        /**
         * Invoked only when playback, artwork, or theme changes. Theme is read
         * from the app's existing 'aurelis_native' settings, so no second user
         * preference can drift out of sync with Glass Ambient / Ambient.
         */
        fun publish(context: Context, song: Song?, playing: Boolean, accent: Int) {
            val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val change = prefs.edit()
            if (song != null) {
                change.putString("title", song.title)
                change.putString("artist", song.artist)
                change.putString("artwork", song.artwork)
                change.putString("song_key", song.key)
            }
            change.putBoolean("playing", playing)
                .putInt("accent", accent).apply()
            updateWidgets(context)
        }

        fun publishFromSession(context: Context, media: MediaItem?, playing: Boolean) {
            if (media == null) {
                val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                prefs.edit().putBoolean("playing", playing).apply()
                updateWidgets(context)
                return
            }
            val metadata = media.mediaMetadata
            val title = metadata.title?.toString().orEmpty()
            if (title.isBlank()) return
            val snapshot = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val oldAccent = snapshot.getInt("accent", 0xFF315A4C.toInt())
            publish(context, Song(
                id = media.mediaId.ifEmpty { title },
                title = title,
                artist = metadata.artist?.toString() ?: "Aurelis Symphony",
                artwork = metadata.artworkUri?.toString().orEmpty()
            ), playing, oldAccent)
        }

        fun updateWidgets(context: Context, widgetIds: IntArray? = null) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = widgetIds ?: manager.getAppWidgetIds(
                ComponentName(app, AurelisWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val store = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            val theme = app.getSharedPreferences("aurelis_native", Context.MODE_PRIVATE)
                .getString("visualStyle", "GLASS")
            val glass = theme != "AMBIENT"
            val artUrl = store.getString("artwork", "").orEmpty()
            val file = File(app.cacheDir, COVER_CACHE)
            val art = if (artUrl.isNotEmpty() &&
                artUrl == store.getString("cached_artwork", "") && file.isFile) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
            val accent = store.getInt("accent", 0xFF315A4C.toInt())
            val playing = store.getBoolean("playing", false)
            val title = store.getString("title", "Aurelis Symphony").orEmpty()
            val artist = store.getString("artist", "Musik untuk harimu").orEmpty()
            val background = renderBackground(glass, accent, art)

            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
                val compact = width < 225 || height < 137
                val remote = RemoteViews(app.packageName, if (compact)
                    R.layout.widget_aurelis_compact else R.layout.widget_aurelis)
                remote.setImageViewBitmap(R.id.widget_background, background)
                if (art != null) remote.setImageViewBitmap(R.id.widget_art, art)
                else remote.setImageViewResource(R.id.widget_art, R.drawable.ic_aurelis_foreground)
                remote.setTextViewText(R.id.widget_title, title)
                remote.setTextViewText(R.id.widget_artist, artist)
                if (!compact) {
                    remote.setTextViewText(R.id.widget_status, when {
                        title == "Aurelis Symphony" -> "READY"
                        playing -> "PLAYING"
                        else -> "PAUSED"
                    })
                }
                remote.setImageViewResource(R.id.widget_toggle,
                    if (playing) R.drawable.widget_pause else R.drawable.widget_play)
                val openIntent = Intent(app, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val openPending = PendingIntent.getActivity(
                    app, id * 10, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )
                remote.setOnClickPendingIntent(R.id.widget_root, openPending)
                remote.setOnClickPendingIntent(R.id.widget_art, openPending)
                remote.setOnClickPendingIntent(R.id.widget_title, openPending)
                remote.setOnClickPendingIntent(R.id.widget_artist, openPending)
                remote.setOnClickPendingIntent(R.id.widget_toggle,
                    actionIntent(app, id * 10 + 1, ACTION_TOGGLE))
                remote.setOnClickPendingIntent(R.id.widget_previous,
                    actionIntent(app, id * 10 + 2, ACTION_PREVIOUS))
                remote.setOnClickPendingIntent(R.id.widget_next,
                    actionIntent(app, id * 10 + 3, ACTION_NEXT))
                manager.updateAppWidget(id, remote)
            }
            if (artUrl.startsWith("https://") && art == null) loadArtwork(app, artUrl)
        }

        private fun actionIntent(context: Context, code: Int, action: String): PendingIntent {
            val intent = Intent(context, AurelisWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun openAurelis(context: Context) {
            runCatching {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }

        private fun loadArtwork(context: Context, url: String) {
            if (inFlightArt == url || attemptedArt == url) return
            synchronized(this) {
                if (inFlightArt == url || attemptedArt == url) return
                inFlightArt = url
                attemptedArt = url
            }
            images.execute {
                try {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 4500
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "AurelisWidget/1.2")
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
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = 2 })
                        ?: return@execute
                    val small = Bitmap.createScaledBitmap(bmp, 160, 160, true)
                    if (small !== bmp) bmp.recycle()
                    val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                    if (prefs.getString("artwork", "") != url) {
                        small.recycle()
                        return@execute
                    }
                    val destination = File(context.cacheDir, COVER_CACHE)
                    val temporary = File(context.cacheDir, "$COVER_CACHE.tmp")
                    temporary.outputStream().use {
                        small.compress(Bitmap.CompressFormat.JPEG, 86, it)
                    }
                    small.recycle()
                    if (temporary.renameTo(destination)) {
                        prefs.edit().putString("cached_artwork", url).apply()
                        updateWidgets(context)
                    }
                } catch (_: Exception) {
                    // Network art is optional; widget remains usable offline.
                } finally {
                    if (inFlightArt == url) inFlightArt = null
                }
            }
        }

        private fun mix(first: Int, second: Int, rate: Float): Int {
            val t = rate.coerceIn(0f, 1f)
            return android.graphics.Color.rgb(
                (android.graphics.Color.red(first) * (1f - t) +
                    android.graphics.Color.red(second) * t).toInt(),
                (android.graphics.Color.green(first) * (1f - t) +
                    android.graphics.Color.green(second) * t).toInt(),
                (android.graphics.Color.blue(first) * (1f - t) +
                    android.graphics.Color.blue(second) * t).toInt()
            )
        }

        private fun renderBackground(glass: Boolean, accent: Int, artwork: Bitmap?): Bitmap {
            val width = 520
            val height = 260
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val border = RectF(3f, 3f, width - 3f, height - 3f)
            val path = Path().apply { addRoundRect(border, 46f, 46f, Path.Direction.CW) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.save()
            canvas.clipPath(path)
            val evergreen = android.graphics.Color.rgb(12, 27, 29)
            val base = mix(accent, evergreen, if (glass) .67f else .84f)
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                if (glass) intArrayOf(
                    android.graphics.Color.argb(229,
                        android.graphics.Color.red(base),
                        android.graphics.Color.green(base),
                        android.graphics.Color.blue(base)),
                    0xE8172829.toInt(), 0xEE0B1419.toInt()
                ) else intArrayOf(0xFF162824.toInt(), base, 0xFF0B1518.toInt()),
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            if (glass && artwork != null) {
                // Tiny softened artwork in the background: frosted appearance
                // without live wallpaper capture or expensive blur effects.
                val tiny = Bitmap.createScaledBitmap(artwork, 9, 9, true)
                paint.alpha = 49
                paint.isFilterBitmap = true
                canvas.drawBitmap(tiny, null,
                    Rect(170, -50, width + 80, height + 40), paint)
                tiny.recycle()
                paint.alpha = 255
            }
            paint.shader = RadialGradient(
                width * .24f, height * .07f, width * .73f,
                intArrayOf(if (glass) 0x47C6FFE2 else 0x153B836C,
                    0x00000000), null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.2f
            paint.color = if (glass) 0x86C7F7DD.toInt() else 0x455F8D77
            canvas.drawRoundRect(border, 46f, 46f, paint)
            paint.style = Paint.Style.FILL
            return out
        }
    }
}
