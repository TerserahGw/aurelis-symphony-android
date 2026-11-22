package dev.aurelis.symphony.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.aurelis.symphony.data.YouTubeMusicHub
import dev.aurelis.symphony.data.YouTubeSession
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val MUSIC = "https://music.youtube.com"
private const val LOGIN =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com%2F"

/**
 * User-authenticated Google web login; password is never read by Aurelis.
 * Only after the user presses Confirm does Aurelis snapshot its own
 * WebView's YouTube Music cookie and ytcfg identity for local encrypted storage.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeMusicLogin(
    hub: YouTubeMusicHub,
    onClose: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var onMusic by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF0B1217))
                .statusBarsPadding().navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hubungkan YouTube Music", fontSize = 17.sp, color = Color.White)
                    Text("Login berlangsung langsung di halaman Google",
                        fontSize = 11.sp, color = Color(0xFF95B0A5))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, "Tutup login", tint = Color.White)
                }
            }
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                onMusic = url?.startsWith(MUSIC) == true
                                if (onMusic) problem = null
                            }
                        }
                        webView = this
                        loadUrl(LOGIN)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            problem?.let {
                Text(it, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    fontSize = 12.sp, color = Color(0xFFFFB9C0))
            }
            Text(
                "Pilih profil YouTube Music di halaman atas. Setelah halaman musik terbuka, " +
                    "tekan Hubungkan. Sesi hanya disimpan terenkripsi di HP ini dan " +
                    "tidak dikirim ke server Aurelis.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color(0xFFA7B8B5), fontSize = 11.sp, lineHeight = 16.sp
            )
            Button(
                enabled = onMusic && !working,
                onClick = {
                    val view = webView
                    if (view == null) { problem = "Browser belum siap"; return@Button }
                    working = true
                    view.evaluateJavascript("""
                        (function() {
                          try {
                            var y = window.ytcfg;
                            if (!y || !y.get) return null;
                            var get = function(k){ var v=y.get(k); return v==null ? "" : String(v); };
                            return JSON.stringify({
                              loggedIn: !!y.get('LOGGED_IN'),
                              version: get('INNERTUBE_CLIENT_VERSION'),
                              visitor: get('VISITOR_DATA'),
                              authUser: get('SESSION_INDEX'),
                              pageId: get('DELEGATED_SESSION_ID'),
                              dataSyncId: get('DATASYNC_ID')
                            });
                          } catch(e) { return null; }
                        })()
                    """.trimIndent()) { raw ->
                        try {
                            val decoded = if (raw.startsWith("\""))
                                JSONArray("[$raw]").getString(0) else raw
                            val config = JSONObject(decoded)
                            if (!config.optBoolean("loggedIn")) error(
                                "Selesaikan login dan pilih profil di YouTube Music."
                            )
                            val cookies = CookieManager.getInstance().getCookie(MUSIC)
                                ?: error("Cookie YouTube Music belum tersedia")
                            val pageId = config.optString("pageId")
                            hub.connect(YouTubeSession(
                                cookies = cookies,
                                version = config.optString("version"),
                                visitor = config.optString("visitor"),
                                authUser = config.optString("authUser"),
                                pageId = pageId,
                                dataSyncId = pageId.ifBlank {
                                    config.optString("dataSyncId").substringBefore("||")
                                }
                            ))
                            CookieManager.getInstance().flush()
                            scope.launch { hub.refresh(force = true) }
                            onClose()
                        } catch (e: Exception) {
                            problem = e.message ?: "Gagal mengambil sesi akun."
                        } finally {
                            working = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .padding(bottom = 16.dp).height(49.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5B927F),
                    contentColor = Color.White
                )
            ) {
                if (working) CircularProgressIndicator(
                    Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp
                )
                else {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Hubungkan akun")
                }
            }
        }
    }
}
