package dev.aurelis.symphony.playback

import dev.aurelis.symphony.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as HttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Resolves publicly available sources, does not bypass DRM, captcha, or authentication. */
class NewPipeSource {
    private val client = OkHttpClient.Builder().connectTimeout(13, TimeUnit.SECONDS)
        .readTimeout(23, TimeUnit.SECONDS).followRedirects(true).build()
    private val downloader = object : Downloader() {
        @Throws(IOException::class)
        override fun execute(request: ExtractorRequest): ExtractorResponse {
            val method = request.httpMethod().uppercase()
            val bytes = request.dataToSend()
            val contentType = request.headers().entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value?.firstOrNull()?.toMediaTypeOrNull()
            val body = if (method == "GET" || method == "HEAD") null
                else (bytes ?: ByteArray(0)).toRequestBody(contentType)
            val req = HttpRequest.Builder().url(request.url())
            request.headers().forEach { (name, values) -> values.forEach { req.addHeader(name, it) } }
            if (request.headers().keys.none { it.equals("User-Agent", true) }) {
                req.header("User-Agent", "Mozilla/5.0 (Android 16; Mobile) AppleWebKit/537.36 Chrome/137.0 Safari/537.36")
            }
            req.method(method, body)
            return client.newCall(req.build()).execute().use { response ->
                ExtractorResponse(response.code, response.message, response.headers.toMultimap(),
                    response.body?.string().orEmpty(), response.request.url.toString())
            }
        }
    }

    init { synchronized(NewPipeSource::class.java) { NewPipe.init(downloader) } }

    suspend fun resolve(song: Song): String = withContext(Dispatchers.IO) {
        val videoId = song.videoId.ifBlank { song.id.removePrefix("yt-") }
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Unsupported YouTube ID" }
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        val stream = info.audioStreams.filter { it.isUrl && it.content.startsWith("https://") }
            .maxByOrNull { it.averageBitrate }
            ?: throw IOException("No usable audio stream returned by NewPipe")
        stream.content
    }
}
