package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.ui.theme.HeColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class WebTool(val title: String, val description: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val webTools = listOf(
    WebTool("外部收藏与下载", "WNACG · X · ASMR，同步、导入、下载与自动同步", "/external", Icons.Default.CloudDownload),
    WebTool("Pawchive", "作者、帖子与附件浏览，收藏与下载入库", "/external/pawchive", Icons.Default.Explore),
    WebTool("漫画推荐", "根据个人收藏与阅读偏好发现作品", "/recommend", Icons.Default.AutoAwesome),
    WebTool("媒体管理", "分页浏览、多种布局、详情编辑与评分", "/", Icons.Default.EditNote),
    WebTool("BD2 动态", "角色动态浏览与播放", "/bd2-spine", Icons.Default.Animation),
    WebTool("完整统计看板", "年度活跃趋势、待整理项目和收藏排行", "/stats", Icons.Default.BarChart),
)
private val adminTools = listOf(
    WebTool("重复管理", "重复扫描、任务进度与合并处理", "/dedup", Icons.Default.ContentCopy),
    WebTool("服务器设置", "媒体路径、扫描、外部源与服务配置", "/settings", Icons.Default.Dns),
    WebTool("用户管理", "账户、权限与用户配置", "/users", Icons.Default.ManageAccounts),
)

@Composable
internal fun ToolsScreen(serverUrl: String, token: String, onBack: () -> Unit, onLogout: () -> Unit, onDetailChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val storage = remember { context.getSharedPreferences("he_tools", android.content.Context.MODE_PRIVATE) }
    var webUrl by remember(serverUrl) { mutableStateOf(storage.getString("web:$serverUrl", null) ?: WebEndpoint.defaultFor(serverUrl)) }
    var destination by rememberSaveable(serverUrl, token) { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var account by remember(serverUrl, token) { mutableStateOf<org.json.JSONObject?>(null) }
    var accountError by remember { mutableStateOf("") }
    var accountRefresh by remember { mutableIntStateOf(0) }
    var editEndpoint by remember { mutableStateOf(false) }
    var endpointDraft by remember { mutableStateOf(webUrl) }
    var endpointError by remember { mutableStateOf("") }
    LaunchedEffect(destination) { onDetailChanged(destination.isNotBlank()) }
    DisposableEffect(Unit) { onDispose { onDetailChanged(false) } }
    LaunchedEffect(serverUrl, token, accountRefresh) {
        accountError = ""
        try { account = withContext(Dispatchers.IO) { ApiClient(serverUrl, token).getJsonObject("/auth/me") } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (e is ApiClient.UnauthorizedException) onLogout() else accountError = e.message ?: "无法读取账号" }
    }
    BackHandler { if (destination.isNotBlank()) destination = "" else onBack() }
    when {
        destination == "native:stats" -> StatsScreen(serverUrl, token, { destination = "" }, onLogout)
        destination == "native:tags" -> TagsScreen(serverUrl, token, { destination = "" }, onLogout)
        destination.startsWith("web:") -> WebToolScreen(webUrl, token, destination.removePrefix("web:"), title, { destination = "" }, { editEndpoint = true })
        else -> ToolPage("发现与工具", onBack, actions = {
            IconButton(onClick = { endpointDraft = webUrl; endpointError = ""; editEndpoint = true }) { Icon(Icons.Default.Link, "设置 Web 地址") }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("让媒体库更完整", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${account?.optString("username") ?: "HE Manager"} · ${if (account?.optBoolean("is_admin") == true) "管理员" else "个人媒体库"}", color = HeColors.OpWhiteSoft)
                    }
                }
                if (accountError.isNotBlank()) item { ToolError(accountError) { accountRefresh++ } }
                item { Text("库内整理", style = MaterialTheme.typography.titleSmall, color = HeColors.Yellow, modifier = Modifier.padding(top = 8.dp)) }
                item { ToolCard("统计概览", "媒体数量、容量、收藏、观看状态与来源分布", Icons.Default.Insights, "原生") { destination = "native:stats" } }
                item { ToolCard("标签管理", "搜索、重命名、命名空间、合并与清理", Icons.Default.Label, "原生") { destination = "native:tags" } }
                item { Text("发现与导入", style = MaterialTheme.typography.titleSmall, color = HeColors.Yellow, modifier = Modifier.padding(top = 12.dp)) }
                webTools.forEach { tool -> item(key = tool.route) {
                    ToolCard(tool.title, tool.description, tool.icon, "App 内 Web") { title = tool.title; destination = "web:${tool.route}" }
                } }
                if (account?.optBoolean("is_admin") == true) {
                    item { Text("管理工具", style = MaterialTheme.typography.titleSmall, color = HeColors.Yellow, modifier = Modifier.padding(top = 12.dp)) }
                    adminTools.forEach { tool -> item(key = tool.route) {
                        ToolCard(tool.title, tool.description, tool.icon, "App 内 Web") { title = tool.title; destination = "web:${tool.route}" }
                    } }
                }
                item { Text("Web 服务：$webUrl\n沿用当前账号，返回媒体库可查看已入库的内容。", color = HeColors.OpWhiteSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
            }
        }
    }
    if (editEndpoint) AlertDialog(
        onDismissRequest = { editEndpoint = false }, title = { Text("Web 服务地址") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("填写同一个 HE Manager 服务的 Web 首页地址。后端 8010 通常对应 Web 8011。")
            OutlinedTextField(endpointDraft, { endpointDraft = it; endpointError = "" }, label = { Text("http://IP:端口") }, singleLine = true, isError = endpointError.isNotBlank())
            if (endpointError.isNotBlank()) Text(endpointError, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            try {
                webUrl = WebEndpoint.normalize(endpointDraft)
                storage.edit().putString("web:$serverUrl", webUrl).apply()
                editEndpoint = false
            } catch (e: IllegalArgumentException) { endpointError = e.message ?: "地址无效" }
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editEndpoint = false }) { Text("取消") } }
    )
}
