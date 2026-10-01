package com.hemanager.mobile.feature.tools

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.*
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.ui.theme.HeColors
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Images can keep onPageFinished pending. Size the shell as soon as its document
// exists, and own the callback so navigation and disposal can cancel it.
private class ToolWebView(context: android.content.Context) : WebView(context) {
    private val viewportUpdate = object : Runnable {
        override fun run() {
            updateViewport()
            postDelayed(this, 200)
        }
    }
    init { addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() } }
    fun updateViewport() {
        if (height <= 0) return
        val cssHeight = (height / resources.displayMetrics.density).toInt().coerceAtLeast(1)
        evaluateJavascript("if(document.documentElement)document.documentElement.style.setProperty('--he-app-height', '${cssHeight}px', 'important');", null)
    }
    fun startViewportUpdates() {
        removeCallbacks(viewportUpdate)
        post(viewportUpdate)
    }
    fun stopViewportUpdates() { removeCallbacks(viewportUpdate) }
}

@Composable
internal fun WebToolScreen(origin: String, token: String, route: String, title: String, onBack: () -> Unit, onEditEndpoint: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember(origin, token, route) { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingDownload by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf("") }
    var error by remember(origin, route) { mutableStateOf("") }
    var verified by remember(origin, token) { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }
    val downloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data else null
        val source = pendingDownload
        pendingDownload = null
        if (uri != null && source != null) scope.launch {
            downloading = true; downloadMessage = "正在保存…"
            try {
                withContext(Dispatchers.IO) {
                    // Redirects must be checked before forwarding the credential.
                    var address = source
                    var redirects = 0
                    while (true) {
                        check(WebEndpoint.sameOrigin(origin, address)) { "下载地址不属于当前 HE Manager 服务" }
                        val connection = URL(address).openConnection() as HttpURLConnection
                        try {
                            connection.instanceFollowRedirects = false
                            connection.connectTimeout = 10000; connection.readTimeout = 30000
                            connection.setRequestProperty("Authorization", "Bearer $token")
                            val code = connection.responseCode
                            if (code in 300..399) {
                                check(redirects++ < 5) { "下载重定向过多" }
                                address = URL(URL(address), connection.getHeaderField("Location") ?: error("下载地址无效")).toString()
                                continue
                            }
                            check(code in 200..299) { "下载失败（HTTP $code）" }
                            connection.inputStream.use { input ->
                                (context.contentResolver.openOutputStream(uri) ?: error("无法写入文件")).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        output.write(buffer, 0, n)
                                    }
                                }
                            }
                            break
                        } finally { connection.disconnect() }
                    }
                }
                downloadMessage = "文件已保存"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { downloadMessage = e.message ?: "保存失败" }
            finally { downloading = false }
        }
    }
    LaunchedEffect(origin, token, revision) {
        checking = true; verified = false; error = ""
        try {
            withContext(Dispatchers.IO) { ApiClient(origin, token).getJsonObject("/auth/me") }
            verified = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = if (e is ApiClient.UnauthorizedException) "Web 服务没有接受当前登录，请确认这里连接的是同一个 HE Manager 服务。" else "无法连接 Web 服务：${e.message ?: "请检查地址"}" }
        finally { checking = false }
    }
    fun dismissFullscreen() { customView = null; customCallback?.onCustomViewHidden(); customCallback = null }
    fun goBack() {
        if (customView != null) dismissFullscreen()
        else if (canGoBack) webView?.goBack()
        else onBack()
    }
    BackHandler(onBack = ::goBack)
    DisposableEffect(origin, token, route) {
        onDispose {
            fileCallback?.onReceiveValue(null); fileCallback = null
            dismissFullscreen()
            // AndroidView.onRelease owns destruction, so this view is destroyed once.
            WebStorage.getInstance().deleteOrigin(origin)
            webView = null
        }
    }
    ToolPage(title, ::goBack, actions = {
        IconButton(onClick = onEditEndpoint) { Icon(Icons.Default.Link, "更改 Web 服务地址") }
        IconButton(onClick = { if (verified && error.isBlank()) webView?.reload() else revision++ }, enabled = !checking) { Icon(Icons.Default.Refresh, "重新加载") }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (checking || (verified && progress < 100 && error.isBlank())) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (downloadMessage.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(downloadMessage, modifier = Modifier.weight(1f).padding(vertical = 12.dp), color = HeColors.OpWhiteSoft)
                if (!downloading) TextButton(onClick = { downloadMessage = "" }) { Text("关闭") }
            }
            if (error.isNotBlank()) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolError(error) { revision++ }
                OutlinedButton(onClick = onEditEndpoint) { Text("设置 Web 地址") }
            }
            if (verified) key(origin, token, route, revision) {
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = {
                    ToolWebView(it).apply {
                        webView = this
                        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
                        setBackgroundColor(android.graphics.Color.rgb(14, 16, 22))
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.setSupportMultipleWindows(false)
                        var bootstrap = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { startViewportUpdates() }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (WebEndpoint.sameOrigin(origin, request.url.toString())) return false
                                if (!request.isForMainFrame) return true
                                if (request.url.scheme in listOf("https", "http") && request.hasGesture()) {
                                    // HE URLs may carry a token; never export one to another app.
                                    if (request.url.getQueryParameter("token") == null) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                }
                                return true
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                stopViewportUpdates()
                                if (bootstrap && WebEndpoint.sameOrigin(origin, url)) {
                                    bootstrap = false
                                    val script = "localStorage.setItem('he_manager_token', ${JSONObject.quote(token)});location.replace(${JSONObject.quote("$origin/#$route")});"
                                    view.evaluateJavascript(script, null)
                                } else if (WebEndpoint.sameOrigin(origin, url)) {
                                    updateViewport()
                                }
                                canGoBack = view.canGoBack()
                            }
                            override fun onPageCommitVisible(view: WebView, url: String) {
                                if (WebEndpoint.sameOrigin(origin, url)) updateViewport()
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                if (request.isForMainFrame) error = "页面加载失败：${failure.description}"
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) error = "页面加载失败（HTTP ${response.statusCode}）"
                            }
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { canGoBack = view.canGoBack() }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 40) updateViewport()
                            }
                            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                                fileCallback?.onReceiveValue(null); fileCallback = callback
                                return try { filePicker.launch(params.createIntent()); true }
                                catch (_: Exception) { callback.onReceiveValue(null); fileCallback = null; error = "无法打开文件选择器"; true }
                            }
                            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                                AlertDialog.Builder(context).setMessage(message).setPositiveButton("确定") { _, _ -> result.confirm() }.setOnCancelListener { result.cancel() }.show()
                                return true
                            }
                            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                                AlertDialog.Builder(context).setTitle("确认操作").setMessage(message).setPositiveButton("确认") { _, _ -> result.confirm() }.setNegativeButton("取消") { _, _ -> result.cancel() }.setOnCancelListener { result.cancel() }.show()
                                return true
                            }
                            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                                val input = EditText(context).apply { setText(defaultValue) }
                                AlertDialog.Builder(context).setMessage(message).setView(input).setPositiveButton("确定") { _, _ -> result.confirm(input.text.toString()) }.setNegativeButton("取消") { _, _ -> result.cancel() }.setOnCancelListener { result.cancel() }.show()
                                return true
                            }
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                if (customView != null) { callback.onCustomViewHidden(); return }
                                customView = view; customCallback = callback
                            }
                            override fun onHideCustomView() = dismissFullscreen()
                        }
                        setDownloadListener { url, _, disposition, mimeType, _ ->
                            if (!WebEndpoint.sameOrigin(origin, url)) downloadMessage = "此链接无法在 App 内保存，请使用原始来源的下载方式。"
                            else if (!downloading && pendingDownload == null) {
                                pendingDownload = url
                                val filename = DownloadNames.choose(disposition, URLUtil.guessFileName(url, disposition, mimeType))
                                val inferredType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substringAfterLast('.', "").lowercase())
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType(inferredType ?: mimeType ?: "application/octet-stream")
                                    .putExtra(Intent.EXTRA_TITLE, filename)
                                try { downloadPicker.launch(intent) }
                                catch (_: Exception) { pendingDownload = null; downloadMessage = "无法打开文件保存窗口" }
                            }
                        }
                        // A local, empty document at the verified origin seeds storage before Vue loads.
                        // Use a different path: replacing only the hash on '/' would keep
                        // this empty document instead of requesting the actual Vue app.
                        val bootstrapUrl = "$origin/__he_android_session__"
                        loadDataWithBaseURL(bootstrapUrl, "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body style='background:#0e1016'></body></html>", "text/html", "UTF-8", bootstrapUrl)
                    }
                }, onRelease = { released ->
                    if (webView === released) webView = null
                    WebStorage.getInstance().deleteOrigin(origin)
                    released.stopViewportUpdates()
                    released.stopLoading(); released.webChromeClient = null; released.destroy()
                })
            }
        }
    }
    customView?.let { view ->
        androidx.compose.ui.window.Dialog(onDismissRequest = ::dismissFullscreen,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize(), onRelease = { (it.parent as? android.view.ViewGroup)?.removeView(it) })
        }
    }
}
