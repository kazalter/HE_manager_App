package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun NativeUsersScreen(
    server: String,
    token: String,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val state = rememberNativeActions(server, token, onLogout)
    var users by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var me by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }

    suspend fun load() {
        me = state.api.get("/auth/me").optInt("id")
        users = state.api.list("/users")
    }

    fun edit(user: JSONObject?) {
        editing = user
        username = user?.text("username").orEmpty()
        password = ""
        admin = user?.optBoolean("is_admin") ?: false
        active = user?.optBoolean("is_active") ?: true
        editor = true
        state.error = ""
    }

    LaunchedEffect(Unit) { state.run { load() } }

    NativeScene(
        title = "用户管理",
        state = state,
        onBack = onBack,
        refresh = { state.run { load() } },
        subtitle = "USER ACCESS CONTROL"
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(HeColors.PanelGradient)
                        .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            tint = HeColors.Yellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "共 ${users.size} 个账户",
                            color = HeColors.OpWhite,
                            fontFamily = GeistMono,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = { edit(null) },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeColors.Yellow,
                        contentColor = HeColors.OnYellow
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            tint = HeColors.OnYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "添加用户",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                }
            }
        }

        items(users, key = { it.optInt("id") }) { u ->
            val isMe = u.optInt("id") == me
            val isAdmin = u.optBoolean("is_admin")
            val isActive = u.optBoolean("is_active", true)
            val uname = u.text("username")

            NativeSection(uname) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isAdmin) HeColors.Yellow.copy(alpha = 0.16f) else HeColors.OpSurface)
                            .border(1.dp, if (isAdmin) HeColors.Yellow.copy(alpha = 0.45f) else HeColors.HairlineMid, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAdmin) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = HeColors.Yellow,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = uname.take(1).uppercase(),
                                fontFamily = Oxanium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = HeColors.OpWhite
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAdmin) HeColors.Yellow.copy(alpha = 0.15f) else HeColors.Void.copy(alpha = 0.5f))
                                    .border(1.dp, if (isAdmin) HeColors.Yellow.copy(alpha = 0.4f) else HeColors.HairlineMid, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isAdmin) "管理员" else "普通用户",
                                    color = if (isAdmin) HeColors.Yellow else HeColors.OpWhiteSoft,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isActive) Color(0xFF10281B) else Color(0xFF281013))
                                    .border(1.dp, if (isActive) HeColors.Online.copy(alpha = 0.4f) else HeColors.OpDanger.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isActive) HeColors.Online else HeColors.OpDanger)
                                    )
                                    Text(
                                        text = if (isActive) "已启用" else "已停用",
                                        color = if (isActive) HeColors.Online else HeColors.OpDanger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (isMe) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(HeColors.Cyan.copy(alpha = 0.15f))
                                        .border(1.dp, HeColors.Cyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "当前账号",
                                        color = HeColors.Cyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = HeColors.OpWhiteMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "创建时间 ${u.text("created_at")}",
                                color = HeColors.OpWhiteSoft,
                                fontFamily = GeistMono,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { edit(u) },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.HairlineMid),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = HeColors.OpWhiteSoft
                            )
                            Text("编辑账号", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!isMe) {
                        OutlinedButton(
                            onClick = {
                                state.confirm("删除用户“${u.text("username")}”？此操作无法撤销。") {
                                    state.api.request("/users/${u.optInt("id")}", "DELETE")
                                    load()
                                }
                            },
                            enabled = !state.busy,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpDanger),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.OpDanger.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = HeColors.OpDanger
                                )
                                Text(
                                    text = "删除用户",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editor) {
        val dialogShape = RoundedCornerShape(20.dp)
        AlertDialog(
            onDismissRequest = {
                if (!state.busy) {
                    editor = false
                    password = ""
                }
            },
            shape = dialogShape,
            containerColor = HeColors.PanelLight,
            modifier = Modifier.border(1.dp, HeColors.HairlineRim, dialogShape),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(HeColors.Yellow))
                    Text(
                        text = if (editing == null) "添加用户" else "编辑账号",
                        color = HeColors.OpWhite,
                        fontFamily = NotoSansSC,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NativeField("用户名（3–40 字符）", username, { username = it }, enabled = !state.busy)
                    NativeField(
                        label = if (editing == null) "密码（10–128 字符）" else "新密码（留空保留）",
                        value = password,
                        change = { password = it },
                        secret = true,
                        enabled = !state.busy
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HeColors.Void.copy(alpha = 0.4f))
                            .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = admin,
                            onCheckedChange = { admin = it },
                            enabled = !state.busy && editing?.optInt("id") != me,
                            colors = CheckboxDefaults.colors(
                                checkedColor = HeColors.Yellow,
                                checkmarkColor = HeColors.OnYellow
                            )
                        )
                        Column {
                            Text("管理员", color = HeColors.OpWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("具有系统完全管理权限", color = HeColors.OpWhiteMuted, fontSize = 11.5.sp)
                        }
                    }

                    if (editing != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(HeColors.Void.copy(alpha = 0.4f))
                                .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = active,
                                onCheckedChange = { active = it },
                                enabled = !state.busy && editing?.optInt("id") != me,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = HeColors.Yellow,
                                    checkmarkColor = HeColors.OnYellow
                                )
                            )
                            Column {
                                Text("启用账号", color = HeColors.OpWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("允许此账号登录系统", color = HeColors.OpWhiteMuted, fontSize = 11.5.sp)
                            }
                        }
                    }

                    if (state.error.isNotBlank()) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = HeColors.Yellow, trackColor = HeColors.OpSurface)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        state.run {
                            require(username.trim().length in 3..40) { "用户名必须为 3–40 个字符" }
                            require((editing != null && password.isBlank()) || password.length in 10..128) { "密码必须为 10–128 个字符" }
                            val body = obj("username" to username.trim(), "is_admin" to admin, "is_active" to active, "password" to password.ifBlank { null })
                            state.api.send(if (editing == null) "/users" else "/users/${editing!!.optInt("id")}", body, if (editing == null) "POST" else "PUT")
                            password = ""
                            editor = false
                            load()
                            state.message = "账号已保存"
                        }
                    },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = HeColors.Yellow, contentColor = HeColors.OnYellow),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editor = false
                        password = ""
                    },
                    enabled = !state.busy
                ) {
                    Text("取消", color = HeColors.OpWhiteSoft)
                }
            }
        )
    }
}

@Composable
internal fun NativeServerScreen(
    server: String,
    token: String,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val state = rememberNativeActions(server, token, onLogout)
    var folders by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var proxy by rememberSaveable { mutableStateOf("") }
    var path by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("auto") }
    var thumbs by rememberSaveable { mutableStateOf(true) }
    var interval by rememberSaveable { mutableStateOf("1") }
    var matches by remember { mutableStateOf<List<String>>(emptyList()) }
    var add by rememberSaveable { mutableStateOf(false) }

    suspend fun load() {
        folders = state.api.list("/folders")
        proxy = state.api.get("/external/proxy").text("proxy")
    }

    LaunchedEffect(Unit) { state.run { load() } }

    NativePoll(Unit, folders.any { it.text("status") == "scanning" }) {
        try {
            folders = state.api.list("/folders")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.error = e.message ?: "扫描状态更新失败"
        }
    }

    NativeScene(
        title = "服务器设置",
        state = state,
        onBack = onBack,
        refresh = { state.run { load() } },
        subtitle = "SYSTEM STORAGE & REPOSITORY"
    ) {
        item {
            NativeSection("媒体目录") {
                Text(
                    text = "填写服务器上的目录路径。添加或更新配置后会开始扫描。",
                    color = HeColors.OpWhiteSoft,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                )

                OutlinedButton(
                    onClick = { add = !add },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (add) HeColors.OpWhiteSoft else HeColors.Yellow),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (add) HeColors.HairlineMid else HeColors.Yellow.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (add) Icons.Filled.Close else Icons.Filled.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(if (add) "收起目录配置" else "添加 / 更新目录", fontWeight = FontWeight.Bold)
                    }
                }

                if (add) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HeColors.Void.copy(alpha = 0.4f))
                            .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NativeField("服务器绝对路径", path, { path = it }, enabled = !state.busy)

                        OutlinedButton(
                            onClick = {
                                state.run {
                                    val a = state.api.get(query("/search-folder", "name" to path.substringAfterLast('/'))).optJSONArray("results")
                                    matches = (0 until (a?.length() ?: 0)).map { a!!.optString(it) }
                                    if (matches.isEmpty()) state.message = "未找到同名目录，请输入完整路径"
                                }
                            },
                            enabled = !state.busy && path.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.Cyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.Cyan.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(15.dp), tint = HeColors.Cyan)
                                Text("查找服务器目录", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (matches.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                matches.forEach { m ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(HeColors.PanelLight)
                                            .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(8.dp))
                                            .clickable { path = m; matches = emptyList() }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Filled.Folder, contentDescription = null, tint = HeColors.Yellow, modifier = Modifier.size(14.dp))
                                            Text(m, color = HeColors.OpWhite, fontFamily = GeistMono, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        NativeChoice(
                            label = "扫描类型",
                            value = mode,
                            options = listOf("auto" to "自动", "video" to "视频", "manga" to "漫画", "image" to "杂图", "audio" to "音频"),
                            enabled = !state.busy
                        ) { mode = it }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = thumbs,
                                onCheckedChange = { thumbs = it },
                                enabled = !state.busy,
                                colors = CheckboxDefaults.colors(checkedColor = HeColors.Yellow, checkmarkColor = HeColors.OnYellow)
                            )
                            Text("生成缩略图", color = HeColors.OpWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }

                        NativeField("缩略图间隔（1–60）", interval, { interval = it }, enabled = !state.busy)

                        Button(
                            onClick = {
                                state.confirm("保存目录“$path”的扫描设置并开始扫描？") {
                                    val n = interval.toIntOrNull()
                                    require(path.isNotBlank() && n != null && n in 1..60) { "请填写目录，并将间隔设为 1–60" }
                                    state.api.send(
                                        "/folders",
                                        obj("path" to path.trim(), "scan_mode" to mode, "thumbnail_enabled" to thumbs, "thumbnail_interval" to n)
                                    )
                                    load()
                                    add = false
                                    state.message = "目录已保存，扫描已提交"
                                }
                            },
                            enabled = !state.busy && path.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HeColors.Yellow, contentColor = HeColors.OnYellow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = HeColors.OnYellow, modifier = Modifier.size(17.dp))
                                Text("保存并扫描", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        state.confirm("扫描所有已配置目录？") {
                            state.api.send("/folders/scan-all")
                            load()
                            state.message = "已提交全部目录扫描"
                        }
                    },
                    enabled = !state.busy && folders.none { it.text("status") == "scanning" } && folders.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.Yellow),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.Yellow.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Sync, contentDescription = null, tint = HeColors.Yellow, modifier = Modifier.size(16.dp))
                        Text("扫描全部目录", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(folders, key = { it.optInt("id") }) { f ->
            val status = f.text("status")
            val isScanning = status == "scanning"
            val scanMode = f.text("scan_mode", "auto")

            NativeSection(f.text("path")) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HeColors.Void.copy(alpha = 0.6f))
                        .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = HeColors.Yellow, modifier = Modifier.size(18.dp))
                        Text(
                            text = f.text("path"),
                            color = HeColors.OpWhite,
                            fontFamily = GeistMono,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(HeColors.Cyan.copy(alpha = 0.12f))
                                .border(1.dp, HeColors.Cyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = scanMode,
                                color = HeColors.Cyan,
                                fontFamily = GeistMono,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val statusColor = if (isScanning) HeColors.Yellow else HeColors.Online
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(statusColor))
                                Text(
                                    text = statusName(status),
                                    color = statusColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "上次扫描 ${f.text("last_scanned_at", "尚未扫描")}",
                        color = HeColors.OpWhiteSoft,
                        fontFamily = GeistMono,
                        fontSize = 11.5.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            path = f.text("path")
                            mode = f.text("scan_mode", "auto")
                            thumbs = f.optBoolean("thumbnail_enabled", true)
                            interval = f.optInt("thumbnail_interval", 1).toString()
                            add = true
                        },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.HairlineMid),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("编辑目录配置", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            state.run {
                                state.api.send("/folders/${f.optInt("id")}/scan")
                                load()
                                state.message = "扫描已提交"
                            }
                        },
                        enabled = !state.busy && !isScanning,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HeColors.Yellow, contentColor = HeColors.OnYellow)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = HeColors.OnYellow, modifier = Modifier.size(14.dp))
                            Text("扫描此目录", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            state.confirm("从库中移除目录“${f.text("path")}”及其媒体记录？硬盘文件将保留。") {
                                state.api.request("/folders/${f.optInt("id")}", "DELETE")
                                load()
                            }
                        },
                        enabled = !state.busy && !isScanning,
                        colors = ButtonDefaults.textButtonColors(contentColor = HeColors.OpDanger),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("移除目录", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            NativeSection("外部源代理") {
                Text(
                    text = "配置抓取外网源（如 ASMR、X/Twitter、WNACG 等）时经过的网络代理节点。",
                    color = HeColors.OpWhiteSoft,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                NativeField("代理 URL（留空关闭）", proxy, { proxy = it }, enabled = !state.busy)
                Button(
                    onClick = {
                        state.run {
                            state.api.send("/external/proxy", obj("proxy" to proxy.trim()), "PATCH")
                            state.message = "代理设置已保存"
                        }
                    },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HeColors.Yellow, contentColor = HeColors.OnYellow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("保存代理设置", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            NativeSection("库维护") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            state.confirm("重新检查库中的缺失文件？") {
                                val result = state.api.send("/system/recheck-missing")
                                state.message = "缺失检查完成：${result.optInt("checked")} 个记录"
                            }
                        },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.HairlineMid),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.FindInPage, contentDescription = null, tint = HeColors.Yellow, modifier = Modifier.size(20.dp))
                            Text("重新检查缺失文件", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            state.confirm("检查并补全 ASMR 封面？") {
                                val result = state.api.send("/external/asmr/recheck-covers")
                                state.message = "检查 ${result.optInt("checked")}，修复 ${result.optInt("fixed")}，失败 ${result.optInt("failed")}"
                            }
                        },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.HairlineMid),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = HeColors.Cyan, modifier = Modifier.size(20.dp))
                            Text("补全 ASMR 封面", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NativeDedupScreen(
    server: String,
    token: String,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val state = rememberNativeActions(server, token, onLogout)
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var pairs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var status by rememberSaveable { mutableStateOf("pending") }
    var level by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("confidence") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var detail by rememberSaveable { mutableIntStateOf(0) }

    suspend fun load() {
        summary = state.api.get("/dedup/summary")
        val response = state.api.get(
            query(
                "/dedup/candidates-page",
                "status" to status,
                "level" to level,
                "media_type" to type,
                "sort" to sort,
                "limit" to 12,
                "offset" to page * 12
            )
        )
        pairs = response.optJSONArray("items").objects()
        total = response.optInt("total")
        selected = selected.intersect(pairs.filter { it.text("status") == "pending" }.map { it.optInt("id") }.toSet())
    }

    LaunchedEffect(status, level, type, sort, page) { state.run { load() } }

    NativePoll(Unit, (summary?.optInt("queue_size") ?: 0) > 0 || (summary?.optInt("checking") ?: 0) > 0) {
        try {
            summary = state.api.get("/dedup/summary")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.error = e.message ?: "队列状态更新失败"
        }
    }

    if (detail > 0) {
        NativeMediaDetail(server, token, detail, { detail = 0 }, onLogout)
        return
    }

    NativeScene(
        title = "重复管理",
        state = state,
        onBack = onBack,
        refresh = { state.run { load() } },
        subtitle = "DUPLICATE ASSET RESOLUTION"
    ) {
        summary?.let { s ->
            item {
                NativeSection("检测队列") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HeColors.Void.copy(alpha = 0.5f))
                                .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("待处理候选", color = HeColors.OpWhiteMuted, fontSize = 11.5.sp, fontFamily = NotoSansSC)
                                Text("${s.optInt("pending_pairs")}", color = HeColors.Yellow, fontSize = 22.sp, fontFamily = Oxanium, fontWeight = FontWeight.Bold)
                                Text("待人工确认", color = HeColors.YellowDim, fontSize = 10.5.sp, fontFamily = GeistMono)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HeColors.Void.copy(alpha = 0.5f))
                                .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("检查与队列", color = HeColors.OpWhiteMuted, fontSize = 11.5.sp, fontFamily = NotoSansSC)
                                Text("${s.optInt("checking")} / ${s.optInt("queue_size")}", color = HeColors.Cyan, fontSize = 22.sp, fontFamily = Oxanium, fontWeight = FontWeight.Bold)
                                Text("后台运行任务", color = HeColors.Cyan.copy(alpha = 0.7f), fontSize = 10.5.sp, fontFamily = GeistMono)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HeColors.Void.copy(alpha = 0.5f))
                                .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("高置信重复", color = HeColors.OpWhiteMuted, fontSize = 11.5.sp, fontFamily = NotoSansSC)
                                Text("${s.optInt("strong_duplicate")}", color = Color(0xFFFF5252), fontSize = 22.sp, fontFamily = Oxanium, fontWeight = FontWeight.Bold)
                                Text("哈希/文件名高相似", color = Color(0xFFFF8A80), fontSize = 10.5.sp, fontFamily = GeistMono)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HeColors.Void.copy(alpha = 0.5f))
                                .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("中 / 低置信", color = HeColors.OpWhiteMuted, fontSize = 11.5.sp, fontFamily = NotoSansSC)
                                Text("${s.optInt("suspected_duplicate")} / ${s.optInt("weak_suspected")}", color = Color(0xFFBA68C8), fontSize = 22.sp, fontFamily = Oxanium, fontWeight = FontWeight.Bold)
                                Text("潜在相似候选", color = Color(0xFFCE93D8), fontSize = 10.5.sp, fontFamily = GeistMono)
                            }
                        }
                    }
                }
            }
        }

        item {
            NativeSection("筛选") {
                NativeChoice("处理状态", status, listOf("pending" to "待处理", "all" to "全部", "merged" to "已保留", "replaced" to "已替换", "kept_both" to "非重复", "ignored" to "已忽略"), !state.busy) { status = it; page = 0 }
                NativeChoice("置信度", level, listOf("" to "全部", "strong_duplicate" to "高", "suspected_duplicate" to "中", "weak_suspected" to "低"), !state.busy) { level = it; page = 0 }
                NativeChoice("媒体类型", type, listOf("" to "全部", "video" to "视频", "manga" to "漫画", "image" to "图片", "audio" to "音频"), !state.busy) { type = it; page = 0 }
                NativeChoice("排序", sort, listOf("confidence" to "置信度", "newest" to "最新", "oldest" to "最早"), !state.busy) { sort = it; page = 0 }

                if (selected.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HeColors.Yellow.copy(alpha = 0.12f))
                            .border(1.dp, HeColors.Yellow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("已选择 ${selected.size} 组候选", color = HeColors.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf("keep_both" to "批量标记非重复", "ignore" to "批量忽略").forEach { (action, label) ->
                                    OutlinedButton(
                                        onClick = {
                                            state.confirm("$label ${selected.size} 组？") {
                                                state.api.send(
                                                    "/dedup/candidates-batch-resolve",
                                                    obj("pair_ids" to JSONArray(selected.toList()), "action" to action)
                                                )
                                                selected = emptySet()
                                                load()
                                            }
                                        },
                                        enabled = !state.busy,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpWhite),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.HairlineMid),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pairs.isEmpty() && !state.busy) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无重复候选", color = HeColors.OpWhiteMuted, fontSize = 14.sp)
                }
            }
        }

        items(pairs, key = { it.optInt("id") }) { pair ->
            val sim = pair.optInt("similarity")
            val simColor = when {
                sim >= 90 -> Color(0xFFFF5252)
                sim >= 70 -> HeColors.Yellow
                else -> HeColors.Cyan
            }

            NativeSection("${pair.optInt("similarity")}% 相似 · ${pair.text("level")} · ${pair.text("status")}") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HeColors.Void.copy(alpha = 0.45f))
                        .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(simColor.copy(alpha = 0.16f))
                                        .border(1.dp, simColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                                ) {
                                    Text("$sim% 相似", color = simColor, fontFamily = Oxanium, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(HeColors.PanelLight)
                                        .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                ) {
                                    Text(pair.text("level"), color = HeColors.OpWhiteSoft, fontFamily = GeistMono, fontSize = 11.5.sp)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (pair.text("status") == "pending") HeColors.Yellow.copy(alpha = 0.12f) else HeColors.Online.copy(alpha = 0.12f))
                                    .border(1.dp, if (pair.text("status") == "pending") HeColors.Yellow.copy(alpha = 0.35f) else HeColors.Online.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = statusName(pair.text("status")),
                                    color = if (pair.text("status") == "pending") HeColors.Yellow else HeColors.Online,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (pair.text("reason").isNotBlank()) {
                            Text(
                                text = pair.text("reason"),
                                color = HeColors.OpWhiteSoft,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(HeColors.Panel.copy(alpha = 0.4f))
                                    .border(1.dp, HeColors.HairlineMid.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (pair.text("status") == "pending") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = pair.optInt("id") in selected,
                            onCheckedChange = { yes ->
                                selected = if (yes) selected + pair.optInt("id") else selected - pair.optInt("id")
                            },
                            enabled = !state.busy,
                            colors = CheckboxDefaults.colors(
                                checkedColor = HeColors.Yellow,
                                checkmarkColor = HeColors.OnYellow
                            )
                        )
                        Text(
                            text = "选择批量处理",
                            color = HeColors.OpWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                listOf("existing" to "原记录", "candidate" to "候选记录").forEach { (key, label) ->
                    pair.optJSONObject(key)?.let { m ->
                        val isExisting = key == "existing"
                        val badgeColor = if (isExisting) HeColors.Cyan else HeColors.Yellow

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(HeColors.Void.copy(alpha = 0.35f))
                                .border(1.dp, badgeColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(label, color = badgeColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = m.text("title"),
                                    color = HeColors.OpWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            NativeMediaRow(m, state.api) { detail = m.optInt("id") }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HeColors.PanelLight.copy(alpha = 0.6f))
                                    .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = m.text("display_path"),
                                    fontFamily = GeistMono,
                                    fontSize = 11.5.sp,
                                    color = HeColors.OpWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${m.optLong("file_size")} 字节 · ${m.optInt("width")}×${m.optInt("height")} · ${m.optInt("duration")} 秒 · ${m.optInt("page_count")} 页",
                                    fontFamily = GeistMono,
                                    fontSize = 11.sp,
                                    color = HeColors.OpWhiteSoft
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        state.run {
                                            state.api.send("/dedup/media/${m.optInt("id")}/recheck")
                                            load()
                                            state.message = "已提交重新检测"
                                        }
                                    },
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpWhite),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.HairlineMid),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("重新检测此记录", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }

                                TextButton(
                                    onClick = {
                                        state.confirm("永久删除“${m.text("title")}”在磁盘上的文件？此操作无法撤销。") {
                                            state.api.request("/dedup/media/${m.optInt("id")}/file", "DELETE", obj("confirm" to true))
                                            load()
                                        }
                                    },
                                    enabled = !state.busy && !m.optBoolean("is_missing"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = HeColors.OpDanger)
                                ) {
                                    Text("删除磁盘文件", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                if (pair.text("status") == "pending") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    state.confirm("保留原记录？候选记录会被隐藏。") {
                                        state.api.send("/dedup/candidates/${pair.optInt("id")}/resolve", obj("action" to "keep_existing"))
                                        load()
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.Cyan),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.Cyan.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保留原记录", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    state.confirm("采用候选路径？原记录将采用候选路径，候选记录会被合并移除。") {
                                        state.api.send("/dedup/candidates/${pair.optInt("id")}/resolve", obj("action" to "replace_path"))
                                        load()
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.Yellow),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.Yellow.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("采用候选路径", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    state.confirm("两者不是重复？") {
                                        state.api.send("/dedup/candidates/${pair.optInt("id")}/resolve", obj("action" to "keep_both"))
                                        load()
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.Online),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.Online.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("两者不是重复", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    state.confirm("忽略此组？") {
                                        state.api.send("/dedup/candidates/${pair.optInt("id")}/resolve", obj("action" to "ignore"))
                                        load()
                                    }
                                },
                                enabled = !state.busy,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HeColors.OpWhiteSoft),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HeColors.HairlineMid),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("忽略此组", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            NativePagination(page, total, 12, !state.busy) { page = it }
        }
    }
}
