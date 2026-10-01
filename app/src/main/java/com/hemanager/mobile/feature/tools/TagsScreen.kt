package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.ui.theme.HeColors
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
            (0 until array.length()).map { i -> array.getJSONObject(i).let { ManagedTag(it.getInt("id"), it.optString("name"), it.optString("namespace", "general"), it.optInt("count")) } }
        }
    }
    LaunchedEffect(api, revision) {
        loading = true; error = ""
        try { fetchTags() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (e is ApiClient.UnauthorizedException) onLogout() else error = e.message ?: "标签加载失败" }
        finally { loading = false }
    }
    fun mutate(action: () -> Unit) {
        if (busy) return
        busy = true; dialogError = ""
        scope.launch {
            var succeeded = false
            try {
                withContext(Dispatchers.IO) { action() }
                succeeded = true
                editing = null; merging = null; deleting = null; cleanup = false; confirmMerge = false
                fetchTags()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is ApiClient.UnauthorizedException) onLogout()
                else if (succeeded) error = "已保存，但刷新失败：${e.message}"
                else dialogError = e.message ?: "操作失败，请重试"
            } finally { busy = false }
        }
    }
    val unused = tags.filter { it.count == 0 }
    val visible = tags.filter { it.name.contains(search.trim(), ignoreCase = true) || (namespaces[it.namespace] ?: it.namespace).contains(search.trim(), ignoreCase = true) }
        .sortedWith(compareBy<ManagedTag> { namespaces.keys.indexOf(it.namespace).let { n -> if (n < 0) Int.MAX_VALUE else n } }.thenBy { it.name })
    ToolPage("标签管理", { if (!busy) onBack() }, actions = { IconButton(onClick = { revision++ }, enabled = !loading && !busy) { Icon(Icons.Default.Refresh, "刷新标签") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("搜索标签或命名空间") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "清空搜索") } })
            Text("共 ${tags.size} 个标签 · 显示 ${visible.size} 个", color = HeColors.OpWhiteSoft, style = MaterialTheme.typography.bodySmall)
            if (unused.isNotEmpty()) OutlinedButton(onClick = { cleanup = true; dialogError = "" }, enabled = !busy && !loading) { Text("清理 ${unused.size} 个未使用标签") }
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) ToolError(error) { revision++ }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!loading && visible.isEmpty()) item { Text(if (search.isBlank()) "还没有标签" else "没有匹配的标签", modifier = Modifier.padding(24.dp), color = HeColors.OpWhiteSoft) }
                items(visible, key = { it.id }) { tag ->
                    Card(colors = CardDefaults.cardColors(containerColor = HeColors.Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(tag.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text("${namespaces[tag.namespace] ?: tag.namespace} · ${tag.count} 项媒体", style = MaterialTheme.typography.bodySmall, color = HeColors.OpWhiteSoft)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editing = tag; name = tag.name; namespace = tag.namespace; dialogError = "" }, enabled = !busy) { Text("编辑") }
                                TextButton(onClick = { merging = tag; mergeSearch = ""; mergeTarget = null; dialogError = "" }, enabled = !busy && tags.size > 1) { Text("合并") }
                                IconButton(onClick = { deleting = tag; dialogError = "" }, enabled = !busy) { Icon(Icons.Default.DeleteOutline, "删除标签 ${tag.name}", tint = HeColors.OpDanger) }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { tag -> AlertDialog(
        onDismissRequest = { if (!busy) editing = null }, title = { Text("编辑标签") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it; dialogError = "" }, label = { Text("标签名称") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Text("命名空间", color = HeColors.OpWhiteSoft)
            Column {
                (namespaces + if (namespace !in namespaces) mapOf(namespace to namespace) else emptyMap()).forEach { (key, label) ->
                    Row { RadioButton(namespace == key, { namespace = key }, enabled = !busy); TextButton(onClick = { namespace = key }, enabled = !busy) { Text(label) } }
                }
            }
            if (dialogError.isNotBlank()) Text(dialogError, color = HeColors.OpDanger)
        } },
        confirmButton = { TextButton(onClick = { mutate { api.patchJson("/tags/${tag.id}", JSONObject().put("name", name.trim()).put("namespace", namespace), true) } }, enabled = !busy && name.isNotBlank()) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(onClick = { editing = null }, enabled = !busy) { Text("取消") } }
    ) }
    merging?.let { source -> AlertDialog(
        onDismissRequest = { if (!busy) merging = null }, title = { Text("合并「${source.name}」") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择保留的目标标签。原标签的媒体关联会迁移至目标标签。")
            OutlinedTextField(mergeSearch, { mergeSearch = it }, label = { Text("搜索目标标签") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(tags.filter { it.id != source.id && it.name.contains(mergeSearch.trim(), true) }, key = { it.id }) { target ->
                    TextButton(onClick = { mergeTarget = target }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text("${if (mergeTarget?.id == target.id) "✓ " else ""}${namespaces[target.namespace] ?: target.namespace} · ${target.name} (${target.count})")
                    }
                }
            }
            if (dialogError.isNotBlank()) Text(dialogError, color = HeColors.OpDanger)
        } },
        confirmButton = { TextButton(onClick = { confirmMerge = true }, enabled = mergeTarget != null && !busy) { Text("下一步") } },
        dismissButton = { TextButton(onClick = { merging = null }, enabled = !busy) { Text("取消") } }
    ) }
    if (confirmMerge && merging != null && mergeTarget != null) ConfirmTagAction(
        "确认合并", "将「${merging!!.name}」合并到「${mergeTarget!!.name}」，原标签会删除。", busy, dialogError,
        { confirmMerge = false }, { val source = merging!!; val target = mergeTarget!!; mutate { api.postJson("/tags/${source.id}/merge", JSONObject().put("target_id", target.id), true) } }
    )
    deleting?.let { tag -> ConfirmTagAction("删除标签", "从 ${tag.count} 项媒体中移除「${tag.name}」。媒体文件会保留。", busy, dialogError, { deleting = null }) { mutate { api.deleteJson("/tags/${tag.id}", true) } } }
    if (cleanup) ConfirmTagAction("清理未使用标签", "删除 ${unused.size} 个引用为 0 的标签。", busy, dialogError, { cleanup = false }) {
        val targets = unused.toList()
        mutate {
            val current = api.getJsonArray("/tags")
            val unusedIds = (0 until current.length()).map { current.getJSONObject(it) }
                .filter { it.optInt("count") == 0 }.map { it.optInt("id") }.toSet()
            targets.filter { it.id in unusedIds }.forEach { api.deleteJson("/tags/${it.id}", true) }
        }
    }
}

@Composable
private fun ConfirmTagAction(title: String, message: String, busy: Boolean, error: String, cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) cancel() }, title = { Text(title) },
        text = { Column { Text(message); if (error.isNotBlank()) Text(error, color = HeColors.OpDanger) } },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy) { Text(if (busy) "处理中…" else "确认") } },
        dismissButton = { TextButton(onClick = cancel, enabled = !busy) { Text("取消") } })
}
