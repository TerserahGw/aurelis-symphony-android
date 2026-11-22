package dev.aurelis.symphony.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Uses existing Aurelis 2.7.0 API. Does not reproduce the developer API docs. */
class AurelisApi(val base: String = "https://aurelis-symphony.go.altare.global") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(17, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private suspend fun json(path: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base.trimEnd('/') + path).get().header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Aurelis HTTP ${resp.code}")
            JSONObject(resp.body?.string() ?: "{}")
        }
    }

    suspend fun catalog() = Song.list(json("/api/v1/catalog").optJSONArray("tracks"))
    suspend fun home(liked: List<Song>, recent: List<Song>): List<Song> {
        val l = JSONArray().apply { liked.take(5).forEach { put(it.toJson()) } }.toString()
        val r = JSONArray().apply { recent.take(5).forEach { put(it.toJson()) } }.toString()
        val url = "/api/v2/home?mode=balanced&liked=${encode(l)}&recent=${encode(r)}"
        return Song.list(json(url).optJSONArray("tracks"))
    }
    suspend fun search(q: String): List<Song> = Song.list(json("/api/v1/search?q=${encode(q)}&limit=24").optJSONArray("tracks"))
    suspend fun recommendations(song: Song): List<Song> = Song.list(json(
        "/api/v1/recommendations?track=${encode(song.title)}&artist=${encode(song.artist)}&exclude=${encode(song.id)}&limit=10"
    ).optJSONArray("tracks"))
    suspend fun lyrics(song: Song): Lyrics {
        val path = "/api/v1/lyrics?track=${encode(song.title)}&artist=${encode(song.artist)}&duration=${song.duration}"
        return Lyrics.fromResult(json(path).optJSONObject("result"))
    }
    suspend fun romanize(lyrics: Lyrics): Map<Int, String> = withContext(Dispatchers.IO) {
        val rows = lyrics.lines.mapIndexed { i, line -> i to line.text }
        val result = mutableMapOf<Int, String>()
        for (batch in rows.chunked(60)) {
            val payload = JSONObject().put("language", "auto").put("texts", JSONArray().apply { batch.forEach { put(it.second.take(320)) } })
            val request = Request.Builder().url(base.trimEnd('/') + "/api/v2/romanize")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val out = JSONObject(resp.body?.string() ?: "{}").optJSONArray("lines") ?: return@use
                batch.forEachIndexed { i, (index, value) ->
                    out.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() && it != value }?.let { result[index] = it }
                }
            }
        }
        result
    }
    /**
     * Explicit user opt-in only. The Aurelis backend forwards lyrics to
     * MyMemory; that free service has quotas and may return partial results.
     * Index alignment is preserved so translations stay with their line.
     */
    suspend fun translateLyrics(lyrics: Lyrics, target: String = "id"): Map<Int, String> =
        withContext(Dispatchers.IO) {
            require(target in setOf("id", "en", "ja", "es", "ko")) {
                "Unsupported translation target"
            }
            val translated = mutableMapOf<Int, String>()
            // Reuse translation text already embedded in the lyric source.
            if (target == "id") lyrics.lines.forEachIndexed { index, line ->
                if (line.translation.isNotBlank()) translated[index] = line.translation
            }
            val pending = lyrics.lines.indices.filter { index ->
                index !in translated && lyrics.lines[index].text.isNotBlank()
            }
            // BitChord-inspired fast path: Google lightweight translation in
            // at most two parallel bounded batches with strict index checking.
            // Existing Aurelis/MyMemory API remains the fallback if Google
            // rate-limits, strips markers, or stops supporting this endpoint.
            val quick = FastLyricTranslator.translate(
                pending.map { it to lyrics.lines[it].text.take(250) }, target
            )
            translated.putAll(quick)
            val fallback = pending.filterNot { it in quick }
            var failures = 0
            for (batch in fallback.chunked(16)) {
                val body = JSONObject()
                    .put("target", target)
                    .put("texts", JSONArray().apply {
                        batch.forEach { put(lyrics.lines[it].text.take(250)) }
                    })
                val req = Request.Builder()
                    .url(base.trimEnd('/') + "/api/v2/translate")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json")
                    .build()
                try {
                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) error("Translation HTTP ${response.code}")
                        val data = JSONObject(response.body?.string() ?: "{}")
                        val entries = data.optJSONArray("translations")
                            ?: error("Server did not return translations")
                        batch.forEachIndexed { localIndex, lyricIndex ->
                            val value = entries.optString(localIndex).trim()
                            if (value.isNotBlank()) translated[lyricIndex] = value
                            else failures++
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failures += batch.size
                }
            }
            if (translated.isEmpty() && failures > 0) {
                throw IllegalStateException(
                    "Layanan terjemahan sedang tidak tersedia atau mencapai batas kuota."
                )
            }
            translated
        }

    suspend fun playback(song: Song): String {
        val o = json("/api/v2/playback/${encode(song.id)}")
        val relative = o.optString("streamUrl")
        if (relative.isBlank()) throw IllegalStateException("No audio stream available")
        return if (relative.startsWith("https://")) relative else base.trimEnd('/') + "/" + relative.trimStart('/')
    }
}
