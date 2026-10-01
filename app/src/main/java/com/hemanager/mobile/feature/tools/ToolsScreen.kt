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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.ui.theme.HeColors
import org.json.JSONObject

@Composable
internal fun ToolsScreen(serverUrl: String,token: String,onBack: ()->Unit,onLogout: ()->Unit,onDetailChanged: (Boolean)->Unit) {
    val state=rememberNativeActions(serverUrl,token,onLogout)
    var destination by rememberSaveable(serverUrl,token) {mutableStateOf("")}
    var account by remember {mutableStateOf<JSONObject?>(null)}
    val back={destination=""}
    suspend fun loadAccount() {account=state.api.get("/auth/me")}
    LaunchedEffect(Unit) {state.run {loadAccount()}}
    LaunchedEffect(destination) {onDetailChanged(destination.isNotBlank())}
    DisposableEffect(Unit) {onDispose {onDetailChanged(false)}}
    BackHandler {if(destination.isNotBlank()) destination="" else onBack()}
    val admin=account?.optBoolean("is_admin")==true
    when(destination) {
        "stats" -> StatsScreen(serverUrl,token,back,onLogout)
        "tags" -> TagsScreen(serverUrl,token,back,onLogout)
        "media" -> NativeMediaScreen(serverUrl,token,back,onLogout)
        "external" -> NativeExternalScreen(serverUrl,token,back,onLogout)
        "pawchive" -> NativePawchiveScreen(serverUrl,token,back,onLogout)
        "recommend" -> NativeRecommendScreen(serverUrl,token,admin,back,onLogout)
        "bd2" -> NativeBd2Screen(serverUrl,token,admin,back,onLogout)
        "dedup" -> NativeDedupScreen(serverUrl,token,back,onLogout)
        "server" -> NativeServerScreen(serverUrl,token,back,onLogout)
        "users" -> NativeUsersScreen(serverUrl,token,back,onLogout)
        else -> NativeScene("发现与工具",state,onBack,{state.run {loadAccount()}}) {
            item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("让媒体库更完整",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("${account?.text("username") ?: "HE Manager"} · ${if(admin) "管理员" else "个人媒体库"}",color=HeColors.OpWhiteSoft)
            } }
            item {Text("库内整理",style=MaterialTheme.typography.titleSmall,color=HeColors.Yellow)}
            item {ToolCard("统计看板","数量、容量、年度活跃、增长与待整理作品",Icons.Default.Insights) {destination="stats"}}
            item {ToolCard("标签管理","搜索、重命名、命名空间、合并与清理",Icons.Default.Label) {destination="tags"}}
            item {ToolCard("媒体管理","分页、筛选、详情编辑、标签、收藏与评分",Icons.Default.EditNote) {destination="media"}}
            item {Text("发现",style=MaterialTheme.typography.titleSmall,color=HeColors.Yellow)}
            item {ToolCard("漫画推荐","偏好与标签推荐，AI 配置和分析任务",Icons.Default.AutoAwesome) {destination="recommend"}}
            item {ToolCard("BD2 动态","本地 Spine 动画、皮肤、缩放和播放控制",Icons.Default.Animation) {destination="bd2"}}
            if(admin) {
                item {Text("导入与管理",style=MaterialTheme.typography.titleSmall,color=HeColors.Yellow)}
                item {ToolCard("外部收藏与下载","WNACG、X、ASMR 同步、归档导入与下载任务",Icons.Default.CloudDownload) {destination="external"}}
                item {ToolCard("Pawchive","喜欢的作者、帖子、原生附件浏览与下载",Icons.Default.Explore) {destination="pawchive"}}
                item {ToolCard("重复管理","候选对比、批量处理、重检与文件维护",Icons.Default.ContentCopy) {destination="dedup"}}
                item {ToolCard("服务器设置","媒体目录、扫描、外部代理与库维护",Icons.Default.Dns) {destination="server"}}
                item {ToolCard("用户管理","创建、编辑、权限、停用与删除账号",Icons.Default.ManageAccounts) {destination="users"}}
            }
        }
    }
}
