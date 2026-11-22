package dev.aurelis.symphony.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AurelisRelease(
    val tag: String,
    val url: String,
    val size: Long,
    val notes: String,
    val digest: String?
)

class AurelisUpdater(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .connectTimeout(16, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val repo = "TerserahGw/aurelis-symphony-android"
    private val assetPrefix = "https://github.com/$repo/releases/download/"

    var available by mutableStateOf<AurelisRelease?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var downloaded by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var apk: File? = null

    private fun versionParts(tag: String): List<Int> {
        val match = Regex("""(\d+)\.(\d+)\.(\d+)""").find(tag) ?: return emptyList()
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
    }

    private fun isNewer(remote: String, installed: String): Boolean {
        val a = versionParts(remote)
        val b = versionParts(installed)
        if (a.size != 3 || b.size != 3) return false
        return (0..2).firstOrNull { a[it] != b[it] }?.let { a[it] > b[it] } ?: false
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", "AurelisSymphony-Android")
        .header("Accept", "application/vnd.github+json")
        .build()

    @Suppress("DEPRECATION")
    private fun installedVersion(): String =
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"

    fun check(manual: Boolean = false) {
        if (checking || downloading) return
        scope.launch {
            checking = true
            if (manual) message = null
            val response = runCatching {
                withContext(Dispatchers.IO) {
                    client.newCall(request("https://api.github.com/repos/$repo/releases?per_page=15"))
                        .execute().use { http ->
                            if (!http.isSuccessful) error("GitHub HTTP ${http.code}")
                            val releases = JSONArray(http.body?.string() ?: "[]")
                            var found: AurelisRelease? = null
                            for (i in 0 until releases.length()) {
                                val item = releases.optJSONObject(i) ?: continue
                                if (item.optBoolean("draft")) continue
                                val tag = item.optString("tag_name")
                                if (!isNewer(tag, installedVersion())) continue
                                val assets = item.optJSONArray("assets") ?: continue
                                for (j in 0 until assets.length()) {
                                    val asset = assets.optJSONObject(j) ?: continue
                                    val name = asset.optString("name")
                                    val url = asset.optString("browser_download_url")
                                    if (!name.endsWith(".apk", ignoreCase = true) ||
                                        !name.contains("arm64", ignoreCase = true) ||
                                        !url.startsWith(assetPrefix) ||
                                        !url.startsWith("https://")) continue
                                    val candidate = AurelisRelease(
                                        tag, url, asset.optLong("size"), item.optString("body"),
                                        asset.optString("digest").takeIf { it.startsWith("sha256:") }
                                    )
                                    if (found == null || isNewer(candidate.tag, found.tag)) found = candidate
                                }
                            }
                            found
                        }
                }
            }
            response.onSuccess { release ->
                available = release
                if (release == null && manual) message = "Aurelis sudah versi terbaru."
            }.onFailure { if (manual) message = "Pemeriksaan update gagal: ${it.message}" }
            checking = false
        }
    }

    fun dismiss() {
        available = null
        message = null
    }

    fun clearMessage() { message = null }

    fun download() {
        val release = available ?: return
        if (downloading) return
        scope.launch {
            downloading = true
            downloaded = false
            progress = 0f
            message = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val directory = File(app.cacheDir, "updates").apply { mkdirs() }
                    val file = File(directory, "Aurelis-${release.tag.replace(Regex("[^A-Za-z0-9._-]"), "")}.apk")
                    val temp = File(directory, "pending.apk")
                    val hash = MessageDigest.getInstance("SHA-256")
                    try {
                        client.newCall(request(release.url)).execute().use { http ->
                            if (!http.isSuccessful) error("Unduhan HTTP ${http.code}")
                            val body = http.body ?: error("File APK kosong")
                            var readTotal = 0L
                            val expected = release.size.takeIf { it > 0 } ?: body.contentLength()
                            var reportedAt = SystemClock.elapsedRealtime()
                            body.byteStream().use { input ->
                                FileOutputStream(temp).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        output.write(buffer, 0, n)
                                        hash.update(buffer, 0, n)
                                        readTotal += n
                                        if (readTotal > 350_000_000L) error("APK terlalu besar")
                                        if (expected > 0 && SystemClock.elapsedRealtime() - reportedAt >= 130) {
                                            withContext(Dispatchers.Main) {
                                                progress = (readTotal.toFloat() / expected).coerceIn(0f, 1f)
                                            }
                                            reportedAt = SystemClock.elapsedRealtime()
                                        }
                                    }
                                }
                            }
                            if (expected > 0 && readTotal != expected) error("Unduhan belum lengkap")
                            val sha = hash.digest().joinToString("") { "%02x".format(it) }
                            if (release.digest != null &&
                                !sha.equals(release.digest.removePrefix("sha256:"), ignoreCase = true))
                                error("Checksum SHA-256 tidak cocok")
                            val info = app.packageManager.getPackageArchiveInfo(
                                temp.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                                ?: error("APK tidak valid")
                            if (info.packageName != app.packageName)
                                error("Paket APK berbeda dari Aurelis")
                            val self = app.packageManager.getPackageInfo(
                                app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                            val oldKeys = self.signingInfo?.apkContentsSigners
                            val newKeys = info.signingInfo?.apkContentsSigners
                            if (oldKeys.isNullOrEmpty() || newKeys.isNullOrEmpty() ||
                                oldKeys.none { old -> newKeys.any { fresh ->
                                    old.toByteArray().contentEquals(fresh.toByteArray())
                                } }) {
                                error("Tanda tangan APK berbeda. Update langsung tidak memungkinkan. Simpan data sebelum reinstall satu kali dengan kunci rilis tetap.")
                            }
                            if (file.exists()) file.delete()
                            if (!temp.renameTo(file)) error("Gagal menyimpan APK")
                            file
                        }
                    } finally {
                        if (temp.exists()) temp.delete()
                    }
                }
            }
            result.onSuccess {
                apk = it
                downloaded = true
                progress = 1f
                message = "APK siap. Android akan meminta persetujuan instalasi."
            }.onFailure { message = "Gagal memperbarui: ${it.message}" }
            downloading = false
        }
    }

    fun install() {
        val target = apk?.takeIf { it.isFile } ?: run {
            message = "Unduh APK terlebih dahulu."
            return
        }
        runCatching {
            if (!app.packageManager.canRequestPackageInstalls()) {
                app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                message = "Izinkan instalasi dari Aurelis, lalu tekan Instal lagi."
            } else {
                val uri = FileProvider.getUriForFile(
                    app, "${app.packageName}.updateprovider", target)
                app.startActivity(Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }.onFailure { message = "Tidak dapat membuka installer: ${it.message}" }
    }
}
