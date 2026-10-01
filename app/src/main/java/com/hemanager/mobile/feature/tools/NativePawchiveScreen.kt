@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun NativePawchiveScreen(server: String, token: String, onBack: () -> Unit, onLogout: () -> Unit) {
    val state = rememberNativeActions(server, token, onLogout)
    var connected by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var advancedSearch by rememberSaveable { mutableStateOf(false) }
    var service by rememberSaveable { mutableStateOf("") }
    var creatorId by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("all") }
    var posts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var cursor by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var openedJson by rememberSaveable { mutableStateOf<String?>(null) }
    val opened = remember(openedJson) { openedJson?.let(::JSONObject) }
    var jobs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var tab by rememberSaveable { mutableStateOf("browse") }
    var expandedJob by rememberSaveable { mutableStateOf("") }
    var jobDetail by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun loadFavorites() {
        favorites = if (connected) state.api.get("/external/pawchive/account/favorites").optJSONArray("items").objects() else emptyList()
    }

    suspend fun loadJobs() {
        jobs = state.api.get("/external/pawchive/downloads").optJSONArray("items").objects()
        if (expandedJob.isNotBlank()) jobDetail = state.api.get("/external/pawchive/downloads/${encoded(expandedJob)}")
    }

    suspend fun browse(append: Boolean = false) {
        if (creatorId.isBlank() && applied.length < 3) {
            posts = emptyList()
            cursor = ""
            more = false
            return
        }
        val response = state.api.get(
            query(
                "/external/pawchive/posts",
                "q" to applied,
                "service" to service,
                "creator_id" to creatorId,
                "tag" to if (creatorId.isNotBlank()) tag else "",
                "media_type" to type,
                "cursor" to if (append) cursor else ""
            )
        )
        val fresh = response.optJSONArray("items").objects()
        posts = (if (append) posts + fresh else fresh).distinctBy { it.text("post_key") }
        cursor = response.text("next_cursor")
        more = response.optBoolean("has_more")
        state.message = response.optJSONArray("warnings")?.let { a -> (0 until a.length()).joinToString("\n") { a.optString(it) } }.orEmpty()
        if (!append) selected = emptySet()
    }

    suspend fun refresh() {
        connected = state.api.get("/external/pawchive/account/status").optBoolean("connected")
        loadJobs()
        loadFavorites()
        if (posts.isNotEmpty()) browse()
    }

    fun selection(post: JSONObject, keys: List<String>? = null) = obj(
        "service" to post.text("service"),
        "creator_id" to post.text("creator_id"),
        "post_id" to post.text("post_id"),
        "attachment_keys" to keys?.let { JSONArray(it) }
    )

    fun download(selections: List<JSONObject>) {
        state.run {
            val body = obj("selections" to JSONArray(selections))
            val preview = state.api.send("/external/pawchive/downloads/preview", body)
            val count = preview.optInt("supported") - preview.optInt("already_downloaded")
            require(count > 0) { "所选内容没有待下载的受支持附件" }
            state.confirm("将 ${preview.optInt("selected_posts")} 篇帖子中的 $count 个附件下载入库；跳过 ${preview.optInt("unsupported")} 个不支持的附件。") {
                state.api.send("/external/pawchive/downloads", body)
                selected = emptySet()
                loadJobs()
                state.message = "下载任务已提交"
            }
        }
    }

    LaunchedEffect(Unit) { state.run { refresh() } }

    NativePoll(tab, tab == "downloads" && jobs.any { activeJob(it) }) {
        try {
            loadJobs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.error = e.message ?: "下载任务更新失败"
        }
    }

    if (opened != null) {
        NativePawchivePost(state, opened, type, { openedJson = null }, { p, keys -> download(listOf(selection(p, keys))) })
        return
    }

    NativeScene("Pawchive", state, onBack, { state.run { refresh() } }, subtitle = "// CREATOR ARCHIVE") {
        item {
            NativeChoice(
                "页面",
                tab,
                listOf("browse" to "作者与帖子", "downloads" to "下载任务"),
                !state.busy
            ) { tab = it }
        }

        if (tab == "browse") {
            item {
                NativeSection("Pawchive 账号") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (connected) HeColors.Online else HeColors.OpWhiteMuted)
                            )
                            Text(
                                text = if (connected) "账号已连接" else "未连接 Pawchive 账号",
                                color = HeColors.OpWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }

                        if (connected) {
                            OutlinedButton(
                                onClick = {
                                    state.confirm("退出 Pawchive 账号？") {
                                        state.api.request("/external/pawchive/account/session", "DELETE")
                                        connected = false
                                        favorites = emptyList()
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("退出 Pawchive", fontSize = 12.sp)
                            }
                        }
                    }

                    if (!connected) {
                        Text("连接账号后可读取和管理喜欢的作者", color = HeColors.OpWhiteSoft, fontSize = 12.5.sp)
                        NativeField("用户名", username, { username = it }, enabled = !state.busy)
                        NativeField("密码", password, { password = it }, secret = true, enabled = !state.busy)
                        Button(
                            onClick = {
                                state.run {
                                    try {
                                        state.api.send("/external/pawchive/account/login", obj("username" to username, "password" to password))
                                        connected = true
                                        loadFavorites()
                                        username = ""
                                    } finally {
                                        password = ""
                                    }
                                }
                            },
                            enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HeColors.Yellow,
                                contentColor = HeColors.OnYellow
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("连接账号", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                NativeSection("查找作者与帖子") {
                    NativeField("作者、关键词或作者网址", search, { search = it }, enabled = !state.busy)
                    Text("关键词至少 3 个字符；为空时仅展示喜欢的作者。", color = HeColors.OpWhiteSoft, fontSize = 12.sp)

                    TextButton(onClick = { advancedSearch = !advancedSearch }) {
                        Text(if (advancedSearch) "收起作者筛选" else "按服务和作者 ID 查找", color = HeColors.Yellow, fontWeight = FontWeight.Bold)
                    }

                    if (advancedSearch) {
                        NativeField("服务（直接查找作者时填写）", service, { service = it }, enabled = !state.busy)
                        NativeField("作者 ID", creatorId, { creatorId = it }, enabled = !state.busy)
                        if (creatorId.isNotBlank()) NativeField("作者标签", tag, { tag = it }, enabled = !state.busy)
                    }

                    NativeChoice("附件类型", type, listOf("all" to "全部", "image" to "图片", "video" to "视频"), !state.busy) { type = it }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                state.run {
                                    val url = android.net.Uri.parse(search.trim())
                                    val segments = url.pathSegments
                                    if (segments.size >= 3 && segments[1] == "user") {
                                        service = segments[0]
                                        creatorId = segments[2]
                                        applied = ""
                                    } else applied = search.trim()
                                    require((creatorId.isBlank() && service.isBlank()) || (creatorId.isNotBlank() && service.isNotBlank())) { "请同时填写服务和作者 ID" }
                                    require(applied.isBlank() || applied.length >= 3) { "关键词至少 3 个字符" }
                                    browse()
                                }
                            },
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HeColors.Yellow,
                                contentColor = HeColors.OnYellow
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("搜索", fontWeight = FontWeight.Bold)
                        }

                        if (creatorId.isNotBlank() || posts.isNotEmpty()) {
                            TextButton(onClick = {
                                service = ""
                                creatorId = ""
                                applied = ""
                                search = ""
                                tag = ""
                                posts = emptyList()
                                selected = emptySet()
                                more = false
                            }) {
                                Text("返回喜欢的作者", color = HeColors.OpWhiteSoft)
                            }
                        }
                    }
                }
            }

            if (creatorId.isBlank()) {
                val creators = if (posts.isEmpty()) favorites else posts.map {
                    obj(
                        "service" to it.text("service"),
                        "creator_id" to it.text("creator_id"),
                        "creator_name" to it.text("creator_name", it.text("creator_id"))
                    )
                }.distinctBy { it.text("service") + ":" + it.text("creator_id") }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFFAB47BC)))
                        Text(
                            text = if (posts.isEmpty()) "喜欢的作者 · ${favorites.size}" else "搜索结果中的作者 · ${creators.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HeColors.OpWhite,
                            fontFamily = NotoSansSC
                        )
                    }
                }

                items(creators, key = { it.text("service") + ":" + it.text("creator_id") }) { c ->
                    val shape = RoundedCornerShape(16.dp)
                    Column(Modifier.fillMaxWidth().clip(shape).background(HeColors.PanelGradient)
                        .border(1.dp, HeColors.HairlineRim, shape).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = HeColors.CyanSoft, shape = RoundedCornerShape(14.dp)) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Text(c.text("creator_name", c.text("creator_id")).take(1).uppercase(),
                                        color = HeColors.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(c.text("creator_name", c.text("creator_id")), color = HeColors.OpWhite,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("${c.text("service")} · ${c.text("creator_id")}", color = HeColors.OpWhiteSoft,
                                    fontFamily = GeistMono, fontSize = 12.sp, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                state.run {
                                    service = c.text("service"); creatorId = c.text("creator_id")
                                    applied = ""; tag = ""; browse()
                                }
                            }, enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("浏览帖子")
                            }
                            val favorite = favorites.any { it.text("service") == c.text("service") && it.text("creator_id") == c.text("creator_id") }
                            OutlinedButton(onClick = {
                                state.run {
                                    state.api.request("/external/pawchive/account/favorites/${encoded(c.text("service"))}/${encoded(c.text("creator_id"))}",
                                        if (favorite) "DELETE" else "PUT")
                                    loadFavorites()
                                }
                            }, enabled = !state.busy && connected, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text(if (favorite) "取消喜欢" else "喜欢作者")
                            }
                        }
                    }
                }
            }

            if (creatorId.isNotBlank()) item {
                NativeSection("作者操作") {
                    val favorite = favorites.any { it.text("service") == service && it.text("creator_id") == creatorId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                state.run {
                                    state.api.request(
                                        "/external/pawchive/account/favorites/${encoded(service)}/${encoded(creatorId)}",
                                        if (favorite) "DELETE" else "PUT"
                                    )
                                    loadFavorites()
                                }
                            },
                            enabled = !state.busy && connected,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (favorite) "取消喜欢作者" else "喜欢作者")
                        }

                        if (selected.isNotEmpty()) {
                            Button(
                                onClick = { download(posts.filter { it.text("post_key") in selected }.map { selection(it) }) },
                                enabled = !state.busy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HeColors.Yellow,
                                    contentColor = HeColors.OnYellow
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("下载选中的 ${selected.size} 篇帖子", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(posts, key = { it.text("post_key") }) { post ->
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(HeColors.PanelGradient)
                        .border(1.dp, HeColors.HairlineRim, shape)
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = post.text("title", "帖子"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = HeColors.OpWhite,
                            fontFamily = NotoSansSC,
                            fontSize = 15.sp
                        )

                        Text(
                            text = "${post.text("creator_name", post.text("creator_id"))} · ${post.text("published_at")}",
                            color = HeColors.OpWhiteSoft,
                            fontSize = 12.sp,
                            fontFamily = GeistMono
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !state.busy) {
                                        val yes = post.text("post_key") !in selected
                                        selected = if (yes) (selected + post.text("post_key")).take(20).toSet() else selected - post.text("post_key")
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = post.text("post_key") in selected,
                                    onCheckedChange = { yes ->
                                        selected = if (yes) (selected + post.text("post_key")).take(20).toSet() else selected - post.text("post_key")
                                    },
                                    enabled = !state.busy,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = HeColors.Yellow,
                                        checkmarkColor = HeColors.OnYellow
                                    )
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("选择下载", color = HeColors.OpWhite, fontSize = 13.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "附件 ${post.optInt("reported_attachment_count")}",
                                    color = HeColors.OpWhiteSoft,
                                    fontSize = 12.sp,
                                    fontFamily = GeistMono
                                )
                                Button(
                                    onClick = {
                                        state.run {
                                            openedJson = state.api.get("/external/pawchive/posts/${encoded(post.text("service"))}/${encoded(post.text("creator_id"))}/${encoded(post.text("post_id"))}").toString()
                                        }
                                    },
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("打开附件")
                                }
                            }
                        }
                    }
                }
            }

            if (more) {
                item {
                    Button(
                        onClick = { state.run { browse(true) } },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("加载更多")
                    }
                }
            }
        } else {
            if (jobs.isEmpty() && !state.busy) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(HeColors.PanelGradient)
                            .border(1.dp, HeColors.HairlineRim, RoundedCornerShape(14.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无下载任务", color = HeColors.OpWhiteSoft, fontFamily = NotoSansSC)
                    }
                }
            }
            items(jobs, key = { it.text("job_id") }) { j ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JobStatus(j)
                    NativeSection("附件状态") {
                        TextButton(
                            onClick = {
                                state.run {
                                    expandedJob = j.text("job_id")
                                    jobDetail = state.api.get("/external/pawchive/downloads/${encoded(expandedJob)}")
                                }
                            },
                            enabled = !state.busy
                        ) {
                            Text("查看附件状态", color = HeColors.Yellow)
                        }
                        (if (expandedJob == j.text("job_id")) jobDetail else j)?.optJSONArray("attachments").objects().forEach { a ->
                            Text(
                                "${a.text("filename")} · ${statusName(a.text("status"))}${if (a.text("error").isNotBlank()) "\n${a.text("error")}" else ""}",
                                fontSize = 12.sp,
                                fontFamily = GeistMono,
                                color = HeColors.OpWhiteSoft
                            )
                        }
                        if (activeJob(j)) {
                            OutlinedButton(
                                onClick = {
                                    state.confirm("取消此下载任务？") {
                                        state.api.send("/external/pawchive/downloads/${encoded(j.text("job_id"))}/cancel")
                                        loadJobs()
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("取消任务")
                            }
                        } else if (j.text("status") in setOf("failed", "canceled", "interrupted")) {
                            Button(
                                onClick = {
                                    state.run {
                                        state.api.send("/external/pawchive/downloads/${encoded(j.text("job_id"))}/retry")
                                        loadJobs()
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("重试未完成附件")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativePawchivePost(
    state: NativeActions,
    post: JSONObject,
    type: String,
    onBack: () -> Unit,
    download: (JSONObject, List<String>?) -> Unit
) {
    val attachments = post.optJSONArray("attachments").objects().filter {
        it.text("availability") == "playable" && (type == "all" || it.text("media_type") == type)
    }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewer by rememberSaveable { mutableIntStateOf(-1) }

    NativeScene("帖子附件", state, onBack, {}, subtitle = "// PAWCHIVE GALLERY") {
        item {
            NativeSection(post.text("title", "帖子")) {
                Text("${post.text("creator_name")} · ${attachments.size} 个可播放附件",
                    color = HeColors.OpWhiteSoft, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewer = 0 }, enabled = attachments.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = HeColors.Yellow, contentColor = HeColors.OnYellow),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("开始阅读", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { download(post, if (selected.isEmpty()) null else selected.toList()) },
                        enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Text(if (selected.isEmpty()) "下载整篇帖子" else "下载所选 ${selected.size} 项")
                    }
                }
                Text("轻点封面进入全屏阅读；勾选附件可批量下载。", color = HeColors.OpWhiteSoft, fontSize = 12.sp)
            }
        }

        items(attachments.chunked(2), key = { it.first().text("attachment_key") }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { a ->
                    val position = attachments.indexOf(a)
                    val shape = RoundedCornerShape(16.dp)
                    val checked = a.text("attachment_key") in selected
                    Column(Modifier.weight(1f).clip(shape).background(HeColors.Panel)
                        .border(1.dp, if (checked) HeColors.Yellow else HeColors.HairlineMid, shape)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.78f).background(HeColors.Void)
                            .clickable { viewer = position }, contentAlignment = Alignment.Center) {
                            val ref = a.text("preview_ref", a.text("stream_ref"))
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(state.api.origin + "/external/pawchive/media/" + encoded(ref))
                                    .addHeader("Authorization", "Bearer ${state.api.token}").crossfade(true).build(),
                                contentDescription = if (a.text("media_type") == "video") "播放视频" else "全屏查看",
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Surface(color = HeColors.Ink.copy(alpha = 0.9f), shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                                Text("${position + 1}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = HeColors.OpWhite, fontFamily = GeistMono, fontSize = 12.sp)
                            }
                            if (a.text("media_type") == "video") {
                                Surface(color = HeColors.Ink.copy(alpha = 0.8f), shape = CircleShape) {
                                    Icon(Icons.Default.PlayArrow, null, tint = HeColors.OpWhite, modifier = Modifier.padding(12.dp).size(24.dp))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.text("filename"), Modifier.weight(1f), color = HeColors.OpWhite,
                                fontSize = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Checkbox(checked = checked,
                                onCheckedChange = { yes ->
                                    selected = if (yes) selected + a.text("attachment_key") else selected - a.text("attachment_key")
                                }, enabled = !state.busy,
                                modifier = Modifier.semantics { contentDescription = "选择下载 ${a.text("filename")}" },
                                colors = CheckboxDefaults.colors(checkedColor = HeColors.Yellow, checkmarkColor = HeColors.OnYellow))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        post.optJSONArray("attachments").objects().filter { it.text("availability") != "playable" }.forEach { a ->
            item { Text("${a.text("filename")} · 暂不支持预览", color = HeColors.OpWhiteSoft, fontSize = 12.sp) }
        }
    }

    if (viewer >= 0 && attachments.isNotEmpty()) {
        NativeAttachmentViewer(state.api, attachments, viewer, { viewer = it }, { viewer = -1 })
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun NativeVideo(api: NativeApi, path: String, modifier: Modifier, autoplay: Boolean = false, onEnded: () -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val ended by rememberUpdatedState(onEnded)
    var error by remember(path) { mutableStateOf("") }
    val player = remember(api.origin, path) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(mapOf("Authorization" to "Bearer ${api.token}"))
                )
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(api.origin + path))
                prepare()
                playWhenReady = autoplay
            }
    }

    LaunchedEffect(player, autoplay) { player.playWhenReady = autoplay }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended()
            }

            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = "播放失败：${e.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(owner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Box(modifier) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null }
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
