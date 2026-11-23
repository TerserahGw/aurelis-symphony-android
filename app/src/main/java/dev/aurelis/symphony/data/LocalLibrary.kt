package dev.aurelis.symphony.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("aurelis_native", Context.MODE_PRIVATE)
    var favorites: List<Song> = restore("favorites")
        private set
    var history: List<Song> = restore("history")
        private set
    var playlists: Map<String, List<Song>> = restoreLists()
        private set
    var settings: AppSettings = AppSettings(
        audioMode = runCatching { AudioMode.valueOf(prefs.getString("audioMode", "AUTO") ?: "AUTO") }.getOrDefault(AudioMode.AUTO),
        glassOpacity = prefs.getFloat("glassOpacity", .68f),
        visualStyle = runCatching {
            VisualStyle.valueOf(prefs.getString("visualStyle", "GLASS") ?: "GLASS")
        }.getOrDefault(VisualStyle.GLASS),
        language = runCatching {
            AppLanguage.valueOf(prefs.getString("language", "INDONESIAN") ?: "INDONESIAN")
        }.getOrDefault(AppLanguage.INDONESIAN),
        ambientIntensity = prefs.getFloat("ambientIntensity", .58f),
        motion = prefs.getBoolean("motion", true), ambient = prefs.getBoolean("ambient", true),
        romanize = prefs.getBoolean("romanize", true),
        lyricOffsetMs = prefs.getInt("lyricOffsetMs", 0),
        checkUpdates = prefs.getBoolean("checkUpdates", true),
        landscapeRailRight = prefs.getBoolean("landscapeRailRight", false),
        crossfadeEnabled = prefs.getBoolean("crossfadeEnabled", false),
        crossfadeSeconds = prefs.getInt("crossfadeSeconds", 5).coerceIn(1, 12)
    )
        private set

    private fun restore(key: String): List<Song> = runCatching { Song.list(JSONArray(prefs.getString(key, "[]"))) }.getOrDefault(emptyList())
    private fun put(key: String, list: List<Song>) {
        prefs.edit().putString(key, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
    }
    private fun restoreLists(): Map<String, List<Song>> = runCatching {
        val all = JSONObject(prefs.getString("playlists", "{}") ?: "{}")
        buildMap { val names = all.keys(); while (names.hasNext()) {
            val name = names.next(); put(name, Song.list(all.optJSONArray(name)))
        } }
    }.getOrDefault(emptyMap())
    private fun saveLists() {
        val obj = JSONObject()
        playlists.forEach { (name, songs) -> obj.put(name, JSONArray().apply { songs.forEach { put(it.toJson()) } }) }
        prefs.edit().putString("playlists", obj.toString()).apply()
    }
    fun toggleLike(song: Song) {
        favorites = if (favorites.any { it.key == song.key }) favorites.filterNot { it.key == song.key }
        else listOf(song) + favorites
        put("favorites", favorites)
    }
    fun addHistory(song: Song) {
        // Preserve every playback event (including replays), newest first.
        // LazyColumn renders large history lists efficiently.
        history = (listOf(song) + history).take(500)
        put("history", history)
    }
    fun newPlaylist(name: String) {
        val n = name.trim().take(60)
        if (n.isEmpty() || n in playlists) return
        playlists = playlists + (n to emptyList())
        saveLists()
    }
    fun addToPlaylist(name: String, song: Song) {
        val songs = playlists[name] ?: return
        playlists = playlists + (name to (songs + song).distinctBy { it.key })
        saveLists()
    }
    /** Remove one song from one local playlist only; other playlists are untouched. */
    fun removeFromPlaylist(name: String, song: Song): Boolean {
        val tracks = playlists[name] ?: return false
        if (tracks.none { it.key == song.key }) return false
        playlists = playlists + (name to tracks.filterNot { it.key == song.key })
        saveLists()
        return true
    }

    fun updateSettings(s: AppSettings) {
        settings = s
        prefs.edit().putString("audioMode", s.audioMode.name)
            .putFloat("glassOpacity", s.glassOpacity).putFloat("ambientIntensity", s.ambientIntensity)
            .putString("visualStyle", s.visualStyle.name)
            .putString("language", s.language.name)
            .putBoolean("motion", s.motion).putBoolean("ambient", s.ambient)
            .putBoolean("romanize", s.romanize)
            .putInt("lyricOffsetMs", s.lyricOffsetMs.coerceIn(-5000, 5000))
            .putBoolean("checkUpdates", s.checkUpdates)
            .putBoolean("landscapeRailRight", s.landscapeRailRight)
            .putBoolean("crossfadeEnabled", s.crossfadeEnabled)
            .putInt("crossfadeSeconds", s.crossfadeSeconds.coerceIn(1, 12)).apply()
    }
}
