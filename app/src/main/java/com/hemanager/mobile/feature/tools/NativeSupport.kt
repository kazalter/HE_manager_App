package com.hemanager.mobile.feature.tools

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ui.op.IconBtn4
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

internal fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k,v) -> if (v != null) put(k,v) } }
internal fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
internal fun JSONObject.text(key: String, fallback: String = ""): String = if (isNull(key)) fallback else optString(key,fallback)
internal fun query(path: String, vararg pairs: Pair<String, Any?>): String = Uri.parse(path).buildUpon().apply {
    pairs.forEach { (key,value) -> if (value != null && value.toString().isNotBlank()) appendQueryParameter(key,value.toString()) }
}.build().toString()
internal fun encoded(value: String) = Uri.encode(value)
internal fun mediaTypeName(value: String)=mapOf("manga" to "漫画","video" to "视频","image" to "图片","audio" to "音频")[value] ?: value
internal fun statusName(value: String): String = mapOf("queued" to "排队中", "running" to "进行中", "downloading" to "下载中", "pending" to "待处理", "paused" to "已暂停", "pausing" to "暂停中", "cancelling" to "取消中", "done" to "已完成", "completed" to "已完成", "error" to "失败", "failed" to "失败", "cancelled" to "已取消", "canceled" to "已取消", "idle" to "空闲", "ok" to "成功", "checking" to "检查中", "cloning" to "下载中", "pulling" to "更新中")[value] ?: value
internal fun activeJob(job: JSONObject?) = job?.text("status") in setOf("queued","running","downloading","checking","cloning","pulling","pausing","cancelling","canceling")

internal class NativeApiException(val status: Int, message: String, val sessionExpired: Boolean=true) : Exception(message)
internal data class NativeResponse(val value: Any, val total: Int?)
internal class NativeApi(val origin: String, val token: String) {
    companion object {
        private val client=okhttp3.OkHttpClient.Builder().connectTimeout(10,java.util.concurrent.TimeUnit.SECONDS).readTimeout(60,java.util.concurrent.TimeUnit.SECONDS).writeTimeout(120,java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        private val jsonType="application/json; charset=utf-8".toMediaType()
    }
    private fun builder(path: String): okhttp3.Request.Builder {
        require(path.startsWith("/")&&!path.startsWith("//"))
        return okhttp3.Request.Builder().url(origin.trimEnd('/')+path).header("Authorization","Bearer $token").header("Accept","application/json")
    }
    private suspend fun execute(request: okhttp3.Request): okhttp3.Response = suspendCancellableCoroutine {continuation->
        val call=client.newCall(request)
        continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object: okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call,e: java.io.IOException) {if(continuation.isActive) continuation.resumeWith(Result.failure(e))}
            override fun onResponse(call: okhttp3.Call,response: okhttp3.Response) {
                continuation.resume(response,onCancellation={response.close()})
            }
        })
    }
    private fun response(r: okhttp3.Response): NativeResponse {
        val text=r.body?.string().orEmpty();val status=r.code
        val value=if(text.isBlank()) JSONObject() else runCatching {JSONTokener(text).nextValue()}.getOrElse {text}
        if(!r.isSuccessful) {
            val detail=(value as? JSONObject)?.opt("detail")
            val message=when(detail) {is JSONObject->detail.text("message","请求失败（HTTP $status）");is String->detail;else->"请求失败（HTTP $status）"}
            val pawchiveSession=r.request.url.encodedPath.startsWith("/external/pawchive/account")&&(detail is JSONObject||message.contains("Pawchive",true))
            throw NativeApiException(status,message,!pawchiveSession)
        }
        return NativeResponse(value,r.header("X-Total-Count")?.toIntOrNull())
    }
    suspend fun request(path: String,method: String="GET",body: JSONObject?=null): NativeResponse = withContext(Dispatchers.IO) {
        val data=if(body!=null) body.toString().toRequestBody(jsonType) else if(method in setOf("POST","PUT","PATCH")) "{}".toRequestBody(jsonType) else null
        execute(builder(path).method(method,data).build()).use {response(it)}
    }
    suspend fun get(path: String)=request(path).value as JSONObject
    suspend fun list(path: String)=(request(path).value as JSONArray).objects()
    suspend fun send(path: String,body: JSONObject=JSONObject(),method: String="POST")=request(path,method,body).value as JSONObject
    suspend fun upload(path: String,name: String,stream: ()->InputStream): JSONObject = withContext(Dispatchers.IO) {
        val file=object: okhttp3.RequestBody() {
            override fun contentType()="application/octet-stream".toMediaType()
            override fun writeTo(sink: okio.BufferedSink) {
                stream().use {input->val buffer=ByteArray(64*1024);while(true) {val n=input.read(buffer);if(n<0) break;sink.write(buffer,0,n)}}
            }
        }
        val multipart=okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).addFormDataPart("file",name.replace('\r','_').replace('\n','_'),file).build()
        execute(builder(path).post(multipart).build()).use {response(it).value as JSONObject}
    }
    suspend fun bytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        execute(builder(path).get().build()).use {r->if(!r.isSuccessful) throw NativeApiException(r.code,"素材下载失败（HTTP ${r.code}）");r.body?.bytes() ?: error("素材内容为空")}
    }
}

internal class NativeActions(val api: NativeApi, private val scope: CoroutineScope, private val onLogout: () -> Unit) {
    var busy by mutableStateOf(false)
    var backLocked by mutableStateOf(false)
    var error by mutableStateOf("")
    var message by mutableStateOf("")
    var confirmation by mutableStateOf<Pair<String, suspend () -> Unit>?>(null)
    fun run(lockBack: Boolean=false, block: suspend () -> Unit) {
        if(busy) return
        scope.launch {
            busy=true; backLocked=lockBack; error=""; message=""
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(e is NativeApiException && e.status==401 && e.sessionExpired) onLogout() else error=e.message ?: "操作失败，请重试" }
            finally { busy=false; backLocked=false }
        }
    }
    fun confirm(message: String, block: suspend () -> Unit) { confirmation=message to block }
}

@Composable internal fun rememberNativeActions(server: String, token: String, onLogout: () -> Unit): NativeActions {
    val scope=rememberCoroutineScope(); val logout by rememberUpdatedState(onLogout)
    return remember(server,token) { NativeActions(NativeApi(server,token),scope) { logout() } }
}

@Composable internal fun NativeScene(
    title: String,
    state: NativeActions,
    onBack: (() -> Unit)? = null,
    refresh: () -> Unit,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit
) {
    BackHandler { if(onBack != null && !state.backLocked) onBack() }
    ToolPage(
        title = title,
        onBack = if (onBack != null) { { if(!state.backLocked) onBack() } } else null,
        subtitle = subtitle,
        actions = {
            if (state.busy) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = HeColors.Yellow)
                }
            } else {
                IconBtn4(icon = Icons.Default.Refresh, contentDescription = "刷新", onClick = refresh)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.busy) item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.5.dp),
                    color = HeColors.Yellow,
                    trackColor = HeColors.OpSurface
                )
            }
            if (state.error.isNotBlank()) item { ToolError(state.error, refresh) }
            if (state.message.isNotBlank()) item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HeColors.Cyan.copy(alpha = 0.12f))
                        .border(1.dp, HeColors.Cyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(HeColors.Cyan))
                        Text(
                            text = state.message,
                            color = HeColors.Cyan,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            content()
        }
    }
    state.confirmation?.let { (message, action) ->
        val dialogShape = RoundedCornerShape(20.dp)
        AlertDialog(
            onDismissRequest = { state.confirmation = null },
            shape = dialogShape,
            containerColor = HeColors.PanelLight,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(HeColors.Yellow))
                    Text("确认操作", fontFamily = NotoSansSC, fontWeight = FontWeight.Bold, color = HeColors.OpWhite)
                }
            },
            text = { Text(message, color = HeColors.OpWhiteSoft, lineHeight = 20.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        state.confirmation = null
                        state.run(lockBack = true, block = action)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeColors.Yellow,
                        contentColor = HeColors.OnYellow
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("确认", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.confirmation = null }) {
                    Text("取消", color = HeColors.OpWhiteSoft)
                }
            },
            modifier = Modifier.border(1.dp, HeColors.HairlineRim, dialogShape)
        )
    }
}

@Composable internal fun NativeSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sectionShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(sectionShape)
            .background(HeColors.PanelGradient)
            .border(1.dp, HeColors.HairlineRim, sectionShape)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .width(3.5.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(HeColors.Yellow)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeColors.OpWhite,
                    fontFamily = NotoSansSC
                )
            }
            content()
        }
    }
}

@Composable internal fun NativeField(
    label: String,
    value: String,
    change: (String) -> Unit,
    secret: Boolean = false,
    enabled: Boolean = true,
    multiline: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = !multiline,
        maxLines = if (multiline) 5 else 1,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HeColors.Yellow,
            unfocusedBorderColor = HeColors.HairlineHi,
            focusedLabelColor = HeColors.Yellow,
            unfocusedLabelColor = HeColors.OpWhiteSoft,
            cursorColor = HeColors.Yellow,
            focusedTextColor = HeColors.OpWhite,
            unfocusedTextColor = HeColors.OpWhite,
            focusedContainerColor = HeColors.Void.copy(alpha = 0.5f),
            unfocusedContainerColor = HeColors.Void.copy(alpha = 0.35f),
            disabledContainerColor = HeColors.Void.copy(alpha = 0.15f),
            disabledBorderColor = HeColors.Hairline,
            disabledTextColor = HeColors.OpWhiteFaint,
            disabledLabelColor = HeColors.OpWhiteFaint
        ),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun NativeChoice(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    change: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = HeColors.OpWhiteSoft,
            fontFamily = NotoSansSC,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (key, title) ->
                val selected = value == key
                val chipShape = RoundedCornerShape(10.dp)
                val bgMod = if (selected) {
                    Modifier.background(HeColors.Yellow).border(1.dp, HeColors.Yellow, chipShape)
                } else {
                    Modifier.background(HeColors.PanelLight.copy(alpha = 0.8f)).border(1.dp, HeColors.HairlineMid, chipShape)
                }
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .then(bgMod)
                        .clickable(enabled = enabled) { change(key) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selected) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(HeColors.OnYellow))
                        }
                        Text(
                            text = title,
                            color = if (selected) HeColors.OnYellow else HeColors.OpWhiteSoft,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable internal fun JobStatus(job: JSONObject?) {
    if (job == null || job.text("status").isBlank()) return
    val status = job.text("status")
    val isActive = activeJob(job)
    val isError = status in setOf("failed", "error", "cancelled", "canceled")
    val accent = when {
        isActive -> HeColors.Yellow
        isError -> HeColors.OpDanger
        else -> HeColors.Online
    }

    NativeSection("任务状态 · ${statusName(status)}") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = job.text("message").ifBlank { job.text("step") },
                style = MaterialTheme.typography.bodyMedium,
                color = HeColors.OpWhite,
                fontFamily = GeistMono
            )
        }

        val done = job.optInt("completed", job.optInt("completed_posts"))
        val total = job.optInt("total", job.optInt("total_posts"))
        if (total > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$done / $total · 失败 ${job.optInt("failed", job.optInt("failed_posts"))}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GeistMono,
                        color = HeColors.OpWhiteSoft
                    )
                    Text(
                        text = "${(done * 100 / total).coerceIn(0, 100)}%",
                        fontFamily = Oxanium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = HeColors.OpSurface
                )
            }
        }

        listOf("current_title", "current_book_title", "current_file", "error").forEach {
            if (job.text(it).isNotBlank()) {
                Text(
                    text = job.text(it),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GeistMono,
                    color = if (it == "error") HeColors.OpDanger else HeColors.OpWhiteSoft
                )
            }
        }

        val errors = job.optJSONArray("errors")
        if (errors != null) {
            for (i in 0 until minOf(errors.length(), 5)) {
                Text(
                    text = errors.opt(i).toString(),
                    color = HeColors.OpDanger,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GeistMono
                )
            }
        }
    }
}
@Composable internal fun NativePoll(key: Any?, enabled: Boolean, refresh: suspend () -> Unit) {
    val owner=LocalLifecycleOwner.current; val latest by rememberUpdatedState(refresh)
    LaunchedEffect(key,enabled,owner) { if(enabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while(isActive) { delay(2500); latest() }
    } }
}
internal class NativeJobStore(context: Context, server: String) {
    private val prefs=context.getSharedPreferences("he_native_jobs",Context.MODE_PRIVATE)
    private val prefix=server+":"
    fun get(key: String)=prefs.getString(prefix+key,"").orEmpty()
    fun set(key: String,id: String) { prefs.edit().putString(prefix+key,id).apply() }
}
