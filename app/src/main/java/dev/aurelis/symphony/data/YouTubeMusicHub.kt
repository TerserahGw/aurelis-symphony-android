package dev.aurelis.symphony.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class YouTubePlaylist(val id: String, val title: String, val artwork: String)
data class YouTubeShelf(val title: String, val tracks: List<Song>)
data class YouTubeSession(
    val cookies: String,
    val version: String,
    val visitor: String,
    val authUser: String,
    val pageId: String,
    val dataSyncId: String
)

/**
 * Best-effort private WEB_REMIX client, inspired by BitChord's architecture,
 * not a Google-supported Music API. Never sends a session cookie to Aurelis.
 * Reading YouTube Music pages requires an active user-provided browser session.
 * Refresh is pull-based (not Google push/realtime socket).
 */
class YouTubeMusicHub(context: Context) {
    private val app = context.applicationContext
    private val storage = File(app.noBackupFilesDir, "ytmusic_session.aes")
    private val client = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(27, TimeUnit.SECONDS).build()
    private val origin = "https://music.youtube.com"
    private val fallbackVersion = "1.20260701.01.00"
    private val keyAlias = "aurelis_ytmusic_v1"
    private val videoPattern = Regex("[A-Za-z0-9_-]{11}")
    private var session: YouTubeSession? = loadSession()
    private var lastRefreshMs = 0L
    private var discoveredVersion: String? = null

    var isConnected by mutableStateOf(session != null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var shelves by mutableStateOf<List<YouTubeShelf>>(emptyList())
        private set
    var history by mutableStateOf<List<Song>>(emptyList())
        private set
    var playlists by mutableStateOf<List<YouTubePlaylist>>(emptyList())
        private set
    var selectedPlaylist by mutableStateOf<YouTubePlaylist?>(null)
        private set
    var playlistTracks by mutableStateOf<List<Song>>(emptyList())
        private set
    var playlistLoading by mutableStateOf(false)
        private set

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun saveSession(value: YouTubeSession) {
        val payload = JSONObject().put("cookies", value.cookies)
            .put("version", value.version).put("visitor", value.visitor)
            .put("authUser", value.authUser).put("pageId", value.pageId)
            .put("dataSyncId", value.dataSyncId).toString().toByteArray(Charsets.UTF_8)
        // AndroidKeyStore AES-GCM keys enforce randomized encryption.
        // The provider MUST generate the IV during ENCRYPT_MODE init;
        // supplying our own IV throws "Caller-provided IV not permitted".
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val iv = cipher.iv
        check(iv.size == 12) { "Android Keystore returned an invalid GCM nonce" }
        val bytes = byteArrayOf(1) + iv + cipher.doFinal(payload)
        val temp = File(app.noBackupFilesDir, "ytmusic_session.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(storage)) {
            temp.delete()
            error("Tidak dapat menyimpan sesi secara aman")
        }
    }

    private fun loadSession(): YouTubeSession? = runCatching {
        if (!storage.isFile) return@runCatching null
        val bytes = storage.readBytes()
        if (bytes.size < 32 || bytes[0].toInt() != 1) return@runCatching null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        }
        val o = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
        YouTubeSession(
            o.optString("cookies"), o.optString("version"),
            o.optString("visitor"), o.optString("authUser"),
            o.optString("pageId"), o.optString("dataSyncId")
        ).takeIf { apiSid(it.cookies).isNotBlank() }
    }.getOrNull()

    private fun apiSid(cookie: String): String {
        val values = cookie.split(';').mapNotNull {
            val name = it.substringBefore('=').trim()
            val value = it.substringAfter('=', "").trim()
            if (name.isBlank() || value.isBlank()) null else name to value
        }.toMap()
        return listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")
            .firstNotNullOfOrNull { values[it] }.orEmpty()
    }

    fun connect(value: YouTubeSession) {
        require(apiSid(value.cookies).isNotBlank()) { "Login YouTube Music belum selesai" }
        saveSession(value)
        session = value
        isConnected = true
        status = null
        lastRefreshMs = 0L
    }

    fun disconnect() {
        session = null
        storage.delete()
        isConnected = false
        shelves = emptyList()
        history = emptyList()
        playlists = emptyList()
        playlistTracks = emptyList()
        selectedPlaylist = null
        status = null
    }

    private fun jsonText(v: JSONObject?, key: String): String =
        v?.optString(key).orEmpty()

    private fun runs(node: JSONObject?): String {
        val a = node?.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until a.length()) append(a.optJSONObject(i)?.optString("text").orEmpty())
        }.trim()
    }

    private fun thumbnails(node: JSONObject?): String {
        val arr = node?.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: node?.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: node?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
        return arr?.optJSONObject((arr.length() - 1).coerceAtLeast(0))
            ?.optString("url").orEmpty()
    }

    private fun titleAndArtist(item: JSONObject): Pair<String, String> {
        val columns = item.optJSONArray("flexColumns")
        val title = runs(columns?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text"))
        val artist = runs(columns?.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text"))
        return title to artist
    }

    private fun songFromItem(item: JSONObject): Song? {
        val playlistItem = item.optJSONObject("playlistItemData")
        val playEndpoint = item.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")
        val nav = item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
        val id = listOf(playlistItem?.optString("videoId"),
            playEndpoint?.optString("videoId"), nav?.optString("videoId"),
            item.optString("videoId")).firstOrNull { it != null && videoPattern.matches(it) }
            ?: return null
        val (titleA, artistA) = titleAndArtist(item)
        val title = titleA.ifBlank { runs(item.optJSONObject("title")) }
        val artist = artistA.ifBlank { runs(item.optJSONObject("subtitle")) }
        if (title.isBlank()) return null
        return Song(
            id = "yt-$id",
            title = title, artist = artist.ifBlank { "YouTube Music" },
            artwork = thumbnails(item).ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" },
            videoId = id
        )
    }

    private fun songFromTwoRow(item: JSONObject): Song? {
        val nav = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
        val titleRun = item.optJSONObject("title")
            ?.optJSONArray("runs")?.optJSONObject(0)
        val id = listOf(nav?.optString("videoId"),
            titleRun?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")?.optString("videoId"))
            .firstOrNull { it != null && videoPattern.matches(it) } ?: return null
        val title = runs(item.optJSONObject("title"))
        if (title.isBlank()) return null
        return Song("yt-$id", title,
            runs(item.optJSONObject("subtitle")).ifBlank { "YouTube Music" },
            thumbnails(item).ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" },
            videoId = id)
    }

    private fun playlistFromItem(item: JSONObject): YouTubePlaylist? {
        val id = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?: item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?: ""
        if (!id.startsWith("VL") && !id.startsWith("PL")
            && id != "VLLM") return null
        val title = runs(item.optJSONObject("title")).ifBlank {
            titleAndArtist(item).first
        }
        if (title.isBlank()) return null
        return YouTubePlaylist(id, title, thumbnails(item))
    }

    private fun walk(node: Any?, depth: Int = 0, onItem: (String, JSONObject) -> Unit) {
        if (depth > 32) return
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    if (value is JSONObject &&
                        (key == "musicResponsiveListItemRenderer"
                            || key == "musicTwoRowItemRenderer")) onItem(key, value)
                    else walk(value, depth + 1, onItem)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), depth + 1, onItem)
        }
    }

    private fun parseSongs(root: JSONObject, max: Int = 70): List<Song> {
        val found = LinkedHashMap<String, Song>()
        walk(root) { type, node ->
            val song = if (type == "musicTwoRowItemRenderer")
                songFromTwoRow(node) else songFromItem(node)
            if (song != null) found.putIfAbsent(song.key, song)
        }
        return found.values.take(max)
    }

    private fun parsePlaylists(root: JSONObject): List<YouTubePlaylist> {
        val found = LinkedHashMap<String, YouTubePlaylist>()
        walk(root) { _, item ->
            playlistFromItem(item)?.let { found.putIfAbsent(it.id, it) }
        }
        return found.values.take(150)
    }

    private fun parseShelves(root: JSONObject): List<YouTubeShelf> {
        val collected = ArrayList<YouTubeShelf>()
        fun visit(node: Any?, depth: Int = 0) {
            if (depth > 32) return
            when (node) {
                is JSONObject -> {
                    val iterator = node.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        val item = node.opt(key)
                        if (item is JSONObject &&
                            (key == "musicCarouselShelfRenderer" ||
                                key == "musicShelfRenderer")) {
                            val header = item.optJSONObject("header")
                                ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                                ?.optJSONObject("title")
                            val title = runs(header).ifBlank {
                                runs(item.optJSONObject("title"))
                            }.ifBlank { "Rekomendasi YouTube Music" }
                            val songs = parseSongs(item, 16)
                            if (songs.isNotEmpty())
                                collected.add(YouTubeShelf(title, songs))
                        } else visit(item, depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), depth + 1)
            }
        }
        visit(root)
        return collected.distinctBy { it.title }.take(15)
    }

    private fun activeClientVersion(): String {
        session?.version?.takeIf { it.matches(Regex("[0-9.]{8,30}")) }
            ?.let { return it }
        discoveredVersion?.let { return it }
        val candidate = runCatching {
            client.newCall(Request.Builder()
                .url("$origin/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36")
                .build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string().orEmpty()
                    Regex("""INNERTUBE_CLIENT_VERSION["\\s:]+([0-9.]+)""")
                        .find(html)?.groupValues?.getOrNull(1)
                        ?: Regex("""INNERTUBE_CLIENT_VERSION[^0-9]{0,16}([0-9.]+)""")
                            .find(html)?.groupValues?.getOrNull(1)
                }
        }.getOrNull()?.takeIf { it.length in 10..30 }
        return (candidate ?: fallbackVersion).also { discoveredVersion = it }
    }

    private suspend fun post(endpoint: String, params: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val user = session
            val version = activeClientVersion()
            val context = JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX").put("clientVersion", version)
                .put("hl", "id").put("gl", "ID"))
                .put("user", JSONObject().put("lockedSafetyMode", false))
            if (user != null) {
                context.getJSONObject("client").apply {
                    if (user.visitor.isNotBlank()) put("visitorData", user.visitor)
                }
                if (user.dataSyncId.isNotBlank())
                    context.getJSONObject("user").put("onBehalfOfUser", user.dataSyncId)
            }
            val body = JSONObject(params.toString()).put("context", context)
            val builder = Request.Builder()
                .url("$origin/youtubei/v1/$endpoint?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
                .header("X-Origin", origin)
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", version)
            if (user != null) {
                builder.header("Cookie", user.cookies)
                builder.header("X-Goog-AuthUser", user.authUser.ifBlank { "0" })
                if (user.pageId.isNotBlank()) builder.header("X-Goog-PageId", user.pageId)
                if (user.visitor.isNotBlank()) builder.header("X-Goog-Visitor-Id", user.visitor)
                val sid = apiSid(user.cookies)
                if (sid.isNotBlank()) {
                    val t = System.currentTimeMillis() / 1000
                    val hash = MessageDigest.getInstance("SHA-1")
                        .digest("$t $sid $origin".toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(Locale.ROOT, it) }
                    builder.header("Authorization", "SAPISIDHASH ${t}_$hash")
                }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful)
                    error("YouTube Music HTTP ${response.code} (${endpoint})")
                JSONObject(response.body?.string() ?: "{}")
            }
        }

    suspend fun refresh(force: Boolean = false) {
        if (refreshing) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshMs < 45_000) return
        refreshing = true
        try {
            val results = coroutineScope {
                val home = async { runCatching { post("browse", JSONObject().put("browseId", "FEmusic_home")) }.getOrNull() }
                val remoteHistory = async {
                    if (session == null) null else runCatching {
                        post("browse", JSONObject().put("browseId", "FEmusic_history"))
                    }.getOrNull()
                }
                val remoteLists = async {
                    if (session == null) null else runCatching {
                        post("browse", JSONObject().put("browseId", "FEmusic_liked_playlists"))
                    }.getOrNull()
                }
                Triple(home.await(), remoteHistory.await(), remoteLists.await())
            }
            val (home, recent, library) = results
            if (home != null) {
                shelves = parseShelves(home)
                status = null
            } else if (session != null) {
                status = "Sesi YouTube Music tidak dapat dimuat. Coba refresh atau login ulang."
            }
            if (recent != null) history = parseSongs(recent, 100)
            if (library != null) playlists = parsePlaylists(library)
            if (home != null || recent != null || library != null)
                lastRefreshMs = System.currentTimeMillis()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            status = e.message ?: "YouTube Music belum tersedia"
        } finally { refreshing = false }
    }

    private fun continuation(root: JSONObject): String? {
        fun scan(node: Any?, depth: Int = 0): String? {
            if (depth > 25) return null
            return when (node) {
                is JSONObject -> {
                    val direct = node.optJSONObject("continuationItemRenderer")
                        ?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?: node.optJSONObject("nextContinuationData")
                            ?.optString("continuation")
                    if (!direct.isNullOrBlank()) direct
                    else {
                        val keys = node.keys()
                        var token: String? = null
                        while (keys.hasNext() && token == null)
                            token = scan(node.opt(keys.next()), depth + 1)
                        token
                    }
                }
                is JSONArray -> {
                    var token: String? = null
                    for (i in 0 until node.length()) {
                        if (token != null) break
                        token = scan(node.opt(i), depth + 1)
                    }
                    token
                }
                else -> null
            }
        }
        return scan(root)?.takeIf { it.length in 8..4096 }
    }

    /**
     * Browser URL or bare playlist ID. Private playlists can only load
     * when the connected YouTube Music session has access.
     */
    fun playlistFromUrl(value: String): YouTubePlaylist {
        val raw = value.trim()
        val extracted = if (raw.startsWith("https://")) {
            val uri = Uri.parse(raw)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            require(host == "youtube.com" || host == "www.youtube.com"
                || host == "music.youtube.com" || host == "m.youtube.com") {
                "Gunakan URL playlist YouTube atau YouTube Music."
            }
            uri.getQueryParameter("list").orEmpty()
        } else raw
        val id = extracted.removePrefix("VL")
        require(id.matches(Regex("[A-Za-z0-9_-]{10,128}"))) {
            "ID playlist YouTube tidak valid."
        }
        return YouTubePlaylist("VL$id", "Playlist YouTube", "")
    }

    suspend fun playlistTracks(playlist: YouTubePlaylist): List<Song> {
        selectedPlaylist = playlist
        playlistLoading = true
        playlistTracks = emptyList()
        status = null
        try {
            val id = if (playlist.id.startsWith("VL"))
                playlist.id else "VL${playlist.id}"
            val first = post("browse", JSONObject().put("browseId", id))
            val songs = ArrayList<Song>()
            songs += parseSongs(first, 500)
            val pagesVisited = HashSet<String>()
            var token = continuation(first)
            // Pagination is bounded so long imported playlists cannot exhaust
            // a small phone's RAM or flood the YouTube Music endpoint.
            while (token != null && pagesVisited.add(token) && songs.size < 800
                && pagesVisited.size <= 12) {
                val page = post("browse", JSONObject().put("continuation", token))
                songs += parseSongs(page, 500)
                token = continuation(page)
            }
            if (selectedPlaylist?.id == playlist.id) {
                playlistTracks = songs
                if (songs.isEmpty()) status =
                    "Playlist tidak memiliki lagu yang dapat diakses, atau API YouTube membatasinya."
            }
            return songs
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (selectedPlaylist?.id == playlist.id)
                status = e.message ?: "Gagal membuka playlist YouTube."
            return emptyList()
        } finally {
            if (selectedPlaylist?.id == playlist.id) playlistLoading = false
        }
    }

    fun closePlaylist() {
        selectedPlaylist = null
        playlistTracks = emptyList()
        playlistLoading = false
    }

    suspend fun radio(seed: Song): List<Song> {
        val id = seed.videoId.ifBlank { seed.id.removePrefix("yt-") }
        if (!videoPattern.matches(id)) return emptyList()
        return runCatching {
            parseSongs(post("next", JSONObject()
                .put("videoId", id).put("playlistId", "RDAMVM$id")), 20)
                .filterNot { it.videoId == id }
        }.getOrDefault(emptyList())
    }
}
