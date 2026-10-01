package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.ui.op.IconBtn4
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import kotlinx.coroutines.*
import org.json.JSONObject

private data class ManagedTag(val id: Int, val name: String, val namespace: String, val count: Int)
private val namespaces = linkedMapOf("general" to "通用", "artist" to "作者", "character" to "角色", "parody" to "出处", "source" to "来源")

@Composable
internal fun TagsScreen(serverUrl: String, token: String, onBack: () -> Unit, onLogout: () -> Unit) {
    val api = remember(serverUrl, token) { ApiClient(serverUrl, token) }
    val scope = rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<ManagedTag>>(emptyList()) }
    var search by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<ManagedTag?>(null) }
    var name by remember { mutableStateOf("") }
    var namespace by remember { mutableStateOf("general") }
    var merging by remember { mutableStateOf<ManagedTag?>(null) }
    var mergeSearch by remember { mutableStateOf("") }
    var mergeTarget by remember { mutableStateOf<ManagedTag?>(null) }
    var deleting by remember { mutableStateOf<ManagedTag?>(null) }
    var cleanup by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf("") }
    var confirmMerge by remember { mutableStateOf(false) }

    BackHandler { if (!busy) onBack() }

    suspend fun fetchTags() {
        tags = withContext(Dispatchers.IO) {
            val array = api.getJsonArray("/tags")
            (0 until array.length()).map { i ->
                array.getJSONObject(i).let {
                    ManagedTag(it.getInt("id"), it.optString("name"), it.optString("namespace", "general"), it.optInt("count"))
                }
            }
        }
    }

    LaunchedEffect(api, revision) {
        loading = true
        error = ""
        try {
            fetchTags()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiClient.UnauthorizedException) onLogout() else error = e.message ?: "标签加载失败"
        } finally {
            loading = false
        }
    }

    fun mutate(action: () -> Unit) {
        if (busy) return
        busy = true
        dialogError = ""
        scope.launch {
            var succeeded = false
            try {
                withContext(Dispatchers.IO) { action() }
                succeeded = true
                editing = null
                merging = null
                deleting = null
                cleanup = false
                confirmMerge = false
                fetchTags()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiClient.UnauthorizedException) onLogout()
                else if (succeeded) error = "已保存，但刷新失败：${e.message}"
                else dialogError = e.message ?: "操作失败，请重试"
            } finally {
                busy = false
            }
        }
    }

    val unused = tags.filter { it.count == 0 }
    val visible = tags.filter {
        it.name.contains(search.trim(), ignoreCase = true) ||
                (namespaces[it.namespace] ?: it.namespace).contains(search.trim(), ignoreCase = true)
    }.sortedWith(
        compareBy<ManagedTag> {
            val n = namespaces.keys.indexOf(it.namespace)
            if (n < 0) Int.MAX_VALUE else n
        }.thenBy { it.name }
    )

    ToolPage(
        title = "标签管理",
        subtitle = "// TAGS & NAMESPACES",
        onBack = { if (!busy) onBack() },
        actions = {
            if (loading || busy) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = HeColors.Yellow)
                }
            } else {
                IconBtn4(
                    icon = Icons.Default.Refresh,
                    contentDescription = "刷新标签",
                    onClick = { revision++ }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search Input with OP style
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                label = { Text("搜索标签或命名空间") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = HeColors.OpWhiteSoft) },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, "清空搜索", tint = HeColors.OpWhiteSoft)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HeColors.Yellow,
                    unfocusedBorderColor = HeColors.HairlineHi,
                    focusedLabelColor = HeColors.Yellow,
                    unfocusedLabelColor = HeColors.OpWhiteSoft,
                    cursorColor = HeColors.Yellow,
                    focusedTextColor = HeColors.OpWhite,
                    unfocusedTextColor = HeColors.OpWhite,
                    focusedContainerColor = HeColors.Void.copy(alpha = 0.5f),
                    unfocusedContainerColor = HeColors.Void.copy(alpha = 0.35f)
                )
            )

            // Metrics bar & Clean unused action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(HeColors.AudioAccent))
                    Text(
                        text = "共 ${tags.size} 个标签 · 显示 ${visible.size} 个",
                        color = HeColors.OpWhiteSoft,
                        fontFamily = GeistMono,
                        fontSize = 12.5.sp
                    )
                }

                if (unused.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { cleanup = true; dialogError = "" },
                        enabled = !busy && !loading,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HeColors.OpDanger
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(HeColors.OpDanger.copy(alpha = 0.5f))
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("清理 ${unused.size} 个未使用标签", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (loading || busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = HeColors.Yellow,
                    trackColor = HeColors.OpSurface
                )
            }

            if (error.isNotBlank()) {
                ToolError(error) { revision++ }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!loading && visible.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(HeColors.PanelGradient)
                                .border(1.dp, HeColors.HairlineRim, RoundedCornerShape(16.dp))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    tint = HeColors.OpWhiteMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (search.isBlank()) "还没有标签" else "没有匹配的标签",
                                    color = HeColors.OpWhiteSoft,
                                    fontFamily = NotoSansSC,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                items(visible, key = { it.id }) { tag ->
                    val shape = RoundedCornerShape(14.dp)
                    val nsAccent = when (tag.namespace) {
                        "artist" -> HeColors.VideoAccent
                        "character" -> HeColors.ImageAccent
                        "parody" -> HeColors.AudioAccent
                        "source" -> HeColors.MangaAccent
                        else -> HeColors.OpWhiteSoft
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(HeColors.PanelGradient)
                            .border(1.dp, HeColors.HairlineRim, shape)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = tag.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HeColors.OpWhite,
                                        fontFamily = NotoSansSC,
                                        fontSize = 15.5.sp
                                    )
                                    // Namespace tag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(nsAccent.copy(alpha = 0.12f))
                                            .border(1.dp, nsAccent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = namespaces[tag.namespace] ?: tag.namespace,
                                            fontFamily = NotoSansSC,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            color = nsAccent
                                        )
                                    }
                                }

                                // Count badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(HeColors.OpSurface)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${tag.count} 项媒体",
                                        fontFamily = GeistMono,
                                        fontSize = 11.5.sp,
                                        color = if (tag.count > 0) HeColors.Yellow else HeColors.OpWhiteMuted
                                    )
                                }
                            }

                            // Card Action bar
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { editing = tag; name = tag.name; namespace = tag.namespace; dialogError = "" },
                                    enabled = !busy
                                ) {
                                    Text("编辑", color = HeColors.Yellow, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { merging = tag; mergeSearch = ""; mergeTarget = null; dialogError = "" },
                                    enabled = !busy && tags.size > 1
                                ) {
                                    Text("合并", color = HeColors.Cyan, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { deleting = tag; dialogError = "" },
                                    enabled = !busy
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除标签 ${tag.name}",
                                        tint = HeColors.OpDanger
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { tag ->
        val dialogShape = RoundedCornerShape(20.dp)
        AlertDialog(
            onDismissRequest = { if (!busy) editing = null },
            shape = dialogShape,
            containerColor = HeColors.PanelLight,
            modifier = Modifier.border(1.dp, HeColors.HairlineRim, dialogShape),
            title = {
                Text("编辑标签", fontFamily = NotoSansSC, fontWeight = FontWeight.Bold, color = HeColors.OpWhite)
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NativeField("标签名称", name, { name = it; dialogError = "" }, enabled = !busy)
                    Text("命名空间", color = HeColors.OpWhiteSoft, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (namespaces + if (namespace !in namespaces) mapOf(namespace to namespace) else emptyMap()).forEach { (key, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !busy) { namespace = key }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = namespace == key,
                                    onClick = { namespace = key },
                                    enabled = !busy,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = HeColors.Yellow,
                                        unselectedColor = HeColors.OpWhiteSoft
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    label,
                                    color = if (namespace == key) HeColors.OpWhite else HeColors.OpWhiteSoft,
                                    fontWeight = if (namespace == key) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (dialogError.isNotBlank()) Text(dialogError, color = HeColors.OpDanger, fontSize = 12.5.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { mutate { api.patchJson("/tags/${tag.id}", JSONObject().put("name", name.trim()).put("namespace", namespace), true) } },
                    enabled = !busy && name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeColors.Yellow,
                        contentColor = HeColors.OnYellow
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (busy) "保存中…" else "保存", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }, enabled = !busy) {
                    Text("取消", color = HeColors.OpWhiteSoft)
                }
            }
        )
    }

    merging?.let { source ->
        val dialogShape = RoundedCornerShape(20.dp)
        AlertDialog(
            onDismissRequest = { if (!busy) merging = null },
            shape = dialogShape,
            containerColor = HeColors.PanelLight,
            modifier = Modifier.border(1.dp, HeColors.HairlineRim, dialogShape),
            title = {
                Text("合并「${source.name}」", fontFamily = NotoSansSC, fontWeight = FontWeight.Bold, color = HeColors.OpWhite)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("选择保留的目标标签。原标签的媒体关联会迁移至目标标签。", color = HeColors.OpWhiteSoft, fontSize = 13.sp)
                    NativeField("搜索目标标签", mergeSearch, { mergeSearch = it })
                    LazyColumn(
                        Modifier
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HeColors.Void.copy(alpha = 0.4f))
                            .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
                    ) {
                        items(tags.filter { it.id != source.id && it.name.contains(mergeSearch.trim(), true) }, key = { it.id }) { target ->
                            val isSelected = mergeTarget?.id == target.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { mergeTarget = target }
                                    .background(if (isSelected) HeColors.YellowSoft else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isSelected) {
                                        Text("✓", color = HeColors.Yellow, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        target.name,
                                        color = if (isSelected) HeColors.Yellow else HeColors.OpWhite,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                Text(
                                    "${namespaces[target.namespace] ?: target.namespace} · (${target.count})",
                                    color = HeColors.OpWhiteSoft,
                                    fontSize = 11.5.sp,
                                    fontFamily = GeistMono
                                )
                            }
                        }
                    }
                    if (dialogError.isNotBlank()) Text(dialogError, color = HeColors.OpDanger, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmMerge = true },
                    enabled = mergeTarget != null && !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HeColors.Yellow,
                        contentColor = HeColors.OnYellow
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("下一步", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { merging = null }, enabled = !busy) {
                    Text("取消", color = HeColors.OpWhiteSoft)
                }
            }
        )
    }

    if (confirmMerge && merging != null && mergeTarget != null) {
        ConfirmTagAction(
            "确认合并",
            "将「${merging!!.name}」合并到「${mergeTarget!!.name}」，原标签会删除。",
            busy,
            dialogError,
            { confirmMerge = false },
            {
                val source = merging!!
                val target = mergeTarget!!
                mutate { api.postJson("/tags/${source.id}/merge", JSONObject().put("target_id", target.id), true) }
            }
        )
    }

    deleting?.let { tag ->
        ConfirmTagAction(
            "删除标签",
            "从 ${tag.count} 项媒体中移除「${tag.name}」。媒体文件会保留。",
            busy,
            dialogError,
            { deleting = null }
        ) {
            mutate { api.deleteJson("/tags/${tag.id}", true) }
        }
    }

    if (cleanup) {
        ConfirmTagAction(
            "清理未使用标签",
            "删除 ${unused.size} 个引用为 0 的标签。",
            busy,
            dialogError,
            { cleanup = false }
        ) {
            val targets = unused.toList()
            mutate {
                val current = api.getJsonArray("/tags")
                val unusedIds = (0 until current.length()).map { current.getJSONObject(it) }
                    .filter { it.optInt("count") == 0 }.map { it.optInt("id") }.toSet()
                targets.filter { it.id in unusedIds }.forEach { api.deleteJson("/tags/${it.id}", true) }
            }
        }
    }
}

@Composable
private fun ConfirmTagAction(title: String, message: String, busy: Boolean, error: String, cancel: () -> Unit, confirm: () -> Unit) {
    val dialogShape = RoundedCornerShape(20.dp)
    AlertDialog(
        onDismissRequest = { if (!busy) cancel() },
        shape = dialogShape,
        containerColor = HeColors.PanelLight,
        modifier = Modifier.border(1.dp, HeColors.HairlineRim, dialogShape),
        title = { Text(title, fontFamily = NotoSansSC, fontWeight = FontWeight.Bold, color = HeColors.OpWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, color = HeColors.OpWhiteSoft, lineHeight = 20.sp)
                if (error.isNotBlank()) Text(error, color = HeColors.OpDanger, fontSize = 12.5.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = confirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HeColors.Yellow,
                    contentColor = HeColors.OnYellow
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (busy) "处理中…" else "确认", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = cancel, enabled = !busy) {
                Text("取消", color = HeColors.OpWhiteSoft)
            }
        }
    )
}
