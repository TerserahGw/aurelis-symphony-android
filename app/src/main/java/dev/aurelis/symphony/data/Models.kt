package dev.aurelis.symphony.data

import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String = "",
    val duration: Long = 0L,
    val audio: String = "",
    val videoId: String = "",
    val album: String = ""
) {
    val isYouTube: Boolean get() = videoId.matches(Regex("[A-Za-z0-9_-]{11}")) || id.startsWith("yt-")
    val key: String get() = if (id.isNotBlank()) id else "${title}_${artist}"

    /**
     * YouTube Music can return station/browse tiles with a thumbnail and a
     * video-like ID. Those are NOT audio tracks. Filter their explicit labels
     * at the model boundary, regardless of source (API, history, search or
     * YouTube Music). Do not exclude real songs titled STATION (e.g. YOASOBI)
     * or tracks simply named Radio.
     */
    fun isPlayableTrack(): Boolean {
        val t = title.trim().lowercase(java.util.Locale.ROOT)
        val a = artist.trim().lowercase(java.util.Locale.ROOT)
        if (t.isBlank()) return false
        val stationMarkers = listOf(
            "youtube station", "you tube station", "youtube radio",
            "youtube music station", "radio station",
            "personalized station", "personalised station",
            "start radio", "start station", "station based on",
            "artist radio", "music radio station"
        )
        if (stationMarkers.any { it in t }) return false
        val genericSource = a == "youtube music" || a == "unknown artist" ||
            a == "youtube" || a == "radio"
        if (genericSource && (t.endsWith(" radio") ||
            t.endsWith(" station") || t.endsWith(" mix"))) return false
        if (id.startsWith("RDAMVM") || id.startsWith("RDMM") ||
            id.startsWith("RDCLAK")) return false
        return true
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist); put("artwork", artwork)
        put("duration", duration); put("audio", audio); put("videoId", videoId); put("album", album)
    }

    companion object {
        fun fromJson(o: JSONObject): Song? {
            val title = o.optString("title")
            if (title.isBlank()) return null
            val id = o.optString("id").ifBlank { "${title}_${o.optString("artist")}" }
            return Song(
                id = id, title = title, artist = o.optString("artist", "Unknown artist"),
                artwork = o.optString("artwork"), duration = o.optLong("duration"),
                audio = o.optString("audio"), videoId = o.optString("videoId"), album = o.optString("album")
            ).takeIf(Song::isPlayableTrack)
        }
        fun list(arr: JSONArray?): List<Song> = buildList {
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { fromJson(it)?.let(::add) }
        }
    }
}

data class LyricWord(val text: String, val start: Double, val end: Double)
data class LyricLine(
    val text: String,
    val time: Double?,
    val end: Double?,
    val translation: String,
    val words: List<LyricWord>
)
data class Lyrics(val source: String, val lines: List<LyricLine>) {
    companion object {
        fun fromResult(result: JSONObject?): Lyrics {
            val arr = result?.optJSONArray("lines")
            val lines = buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    val l = arr.optJSONObject(i) ?: continue
                    val words = buildList {
                        l.optJSONArray("words")?.let { a -> for (j in 0 until a.length()) {
                            val w = a.optJSONObject(j) ?: continue
                            val text = w.optString("text")
                            val start = w.optDouble("start", Double.NaN)
                            val end = w.optDouble("end", Double.NaN)
                            if (text.isNotEmpty() && start.isFinite() && end.isFinite()) add(LyricWord(text, start, end))
                        } }
                    }
                    val t = if (!l.isNull("time")) l.optDouble("time", Double.NaN).takeIf { it.isFinite() } else null
                    val end = if (!l.isNull("endTime")) l.optDouble("endTime", Double.NaN).takeIf { it.isFinite() } else null
                    val value = l.optString("text")
                    if (value.isNotBlank()) add(LyricLine(value, t, end, l.optString("translation"), words))
                }
            }
            return Lyrics(result?.optString("sourceLabel") ?: "", lines)
        }
    }
}

enum class AudioMode { AUTO, NEWPIPE, AURELIS }
enum class VisualStyle { GLASS, AMBIENT }
enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    SPANISH("es", "Español"),
    KOREAN("ko", "한국어")
}

data class AppSettings(
    val audioMode: AudioMode = AudioMode.AUTO,
    val glassOpacity: Float = .68f,
    val visualStyle: VisualStyle = VisualStyle.GLASS,
    val language: AppLanguage = AppLanguage.INDONESIAN,
    val ambientIntensity: Float = .58f,
    val motion: Boolean = true,
    val ambient: Boolean = true,
    val romanize: Boolean = true,
    val lyricOffsetMs: Int = 0,
    val checkUpdates: Boolean = true,
    /** Landscape full-player close, lyrics/queue and overflow rail position. */
    val landscapeRailRight: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeSeconds: Int = 5
)
