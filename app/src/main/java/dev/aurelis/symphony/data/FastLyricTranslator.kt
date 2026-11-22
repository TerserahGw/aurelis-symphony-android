package dev.aurelis.symphony.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Lightweight opt-in translator modeled on BitChord's batching strategy,
 * but independently implemented. Unofficial Google web translate endpoint:
 * it can change/break; Aurelis /api/v2/translate remains the fallback.
 *
 * An intact index marker for every line is required to accept a batch.
 * Never misalign a translated line with a different lyric timestamp.
 */
internal object FastLyricTranslator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(11, TimeUnit.SECONDS)
        .build()
    private const val endpoint = "https://translate.googleapis.com/translate_a/single"
    private const val maxChars = 2100
    private val marker = Regex("\\uE000(\\d{1,3})\\uE001")
    private val cache = object : LinkedHashMap<String, Map<Int, String>>(24, .75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Map<Int, String>>) =
            size > 20
    }

    private fun batches(entries: List<Pair<Int, String>>): List<List<Pair<Int, String>>> {
        val groups = mutableListOf<List<Pair<Int, String>>>()
        var group = mutableListOf<Pair<Int, String>>()
        var length = 0
        for (entry in entries) {
            val amount = entry.second.length + 14
            if (group.isNotEmpty() && (length + amount > maxChars || group.size >= 12)) {
                groups += group
                group = mutableListOf()
                length = 0
            }
            group += entry
            length += amount
        }
        if (group.isNotEmpty()) groups += group
        return groups
    }

    private suspend fun batch(items: List<Pair<Int, String>>, target: String): Map<Int, String> =
        withContext(Dispatchers.IO) {
            val original = items.joinToString("\n") { (index, text) ->
                "\uE000${index}\uE001$text"
            }
            val req = Request.Builder().url(endpoint)
                .post(FormBody.Builder()
                    .add("client", "gtx").add("sl", "auto")
                    .add("tl", target).add("dt", "t")
                    .add("q", original).build())
                .header("User-Agent", "Mozilla/5.0 (Android; AurelisSymphony)")
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error("Translation HTTP ${response.code}")
                val json = JSONArray(response.body?.string() ?: "[]")
                val segments = json.optJSONArray(0) ?: error("Translation response empty")
                val translated = buildString {
                    for (i in 0 until segments.length()) {
                        append(segments.optJSONArray(i)?.optString(0).orEmpty())
                    }
                }
                val found = marker.findAll(translated).toList()
                if (found.size != items.size) error("Unstable translation line boundaries")
                val entries = LinkedHashMap<Int, String>()
                found.forEachIndexed { pos, match ->
                    val idx = match.groupValues[1].toIntOrNull()
                    if (idx != items[pos].first) error("Translation index changed")
                    val until = found.getOrNull(pos + 1)?.range?.first ?: translated.length
                    entries[idx!!] = translated.substring(match.range.last + 1, until).trim()
                }
                if (entries.size != items.size || entries.values.any { it.isBlank() })
                    error("Incomplete translation batch")
                entries
            }
        }

    suspend fun translate(texts: List<Pair<Int, String>>, target: String): Map<Int, String> {
        if (texts.isEmpty()) return emptyMap()
        val signature = target + "|" + texts.joinToString("\u001F") { it.first.toString() + ":" + it.second }
        synchronized(cache) { cache[signature] }?.let { return it }
        val result = LinkedHashMap<Int, String>()
        coroutineScope {
            for (group in batches(texts).chunked(2)) {
                val answers = group.map { chunk ->
                    async { try { batch(chunk, target) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { emptyMap() } }
                }.awaitAll()
                answers.forEach { result.putAll(it) }
            }
        }
        if (result.isNotEmpty()) synchronized(cache) { cache[signature] = result }
        return result
    }
}
