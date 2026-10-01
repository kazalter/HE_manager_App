package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.ui.theme.HeColors
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Locale

@Composable
internal fun StatsScreen(serverUrl: String, token: String, onBack: () -> Unit, onLogout: () -> Unit) {
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var distribution by remember { mutableStateOf<JSONObject?>(null) }
    var activity by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(serverUrl, token, revision) {
        loading = true; error = ""
        try {
            val data = withContext(Dispatchers.IO) { coroutineScope {
                val api = ApiClient(serverUrl, token)
                listOf("/stats/overview", "/stats/distribution", "/stats/activity?days=30").map { async { api.getJsonObject(it) } }.awaitAll()
            } }
            overview = data[0]; distribution = data[1]; activity = data[2]
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (e is ApiClient.UnauthorizedException) onLogout() else error = e.message ?: "统计加载失败" }
        finally { loading = false }
    }
    ToolPage("统计概览", onBack, actions = { IconButton(onClick = { revision++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "刷新统计") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error.isNotBlank()) item { ToolError(error) { revision++ } }
            overview?.let { o ->
                item { StatPair("媒体总数", o.optInt("total").toString(), "总容量", formatBytes(o.optLong("total_size_bytes"))) }
                item { StatPair("收藏", o.optInt("favorites").toString(), "平均评分", String.format(Locale.getDefault(), "%.1f / 5", o.optDouble("average_rating", 0.0))) }
                item { StatTile("音视频总时长", "${o.optLong("total_duration_seconds") / 3600} 小时", Modifier.fillMaxWidth()) }
                item { StatBars("媒体分类", o.optJSONObject("by_type"), mapOf("video" to "视频", "manga" to "漫画", "image" to "杂图", "audio" to "音频")) }
                item { StatBars("观看状态", o.optJSONObject("view_status"), mapOf("unviewed" to "未看", "viewing" to "在看", "viewed" to "已看")) }
                item { StatTile("文件缺失", o.optInt("missing").toString(), Modifier.fillMaxWidth()) }
            }
            distribution?.let { d ->
                item { StatBars("来源分布", d.optJSONObject("by_source"), mapOf("local" to "本地", "x" to "X", "wnacg" to "WNACG", "asmr" to "ASMR", "pawchive" to "Pawchive")) }
                item { StatBars("评分分布", d.optJSONObject("rating_histogram"), (0..5).associate { it.toString() to if (it == 0) "未评分" else "$it 星" }) }
            }
            activity?.let { a -> item {
                Card(colors = CardDefaults.cardColors(containerColor = HeColors.Panel)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("最近 30 天", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${a.optInt("total")} 次打开 · 最活跃一天 ${a.optInt("max")} 次", color = HeColors.OpWhiteSoft)
                        val buckets = a.optJSONArray("buckets")
                        val activeDays = (0 until (buckets?.length() ?: 0)).mapNotNull { buckets?.optJSONObject(it) }.filter { it.optInt("count") > 0 }.takeLast(7)
                        if (activeDays.isEmpty()) Text("暂无观看记录", color = HeColors.OpWhiteSoft)
                        activeDays.forEach { b -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.optString("date")); Text("${b.optInt("count")} 次", color = HeColors.Yellow)
                        } }
                    }
                }
            } }
        }
    }
}

@Composable
private fun StatPair(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints {
        if (largeText || maxWidth < 340.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(firstLabel, firstValue, Modifier.fillMaxWidth())
            StatTile(secondLabel, secondValue, Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(firstLabel, firstValue, Modifier.weight(1f))
            StatTile(secondLabel, secondValue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = HeColors.Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = HeColors.OpWhiteSoft, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatBars(title: String, values: JSONObject?, names: Map<String, String>) {
    val keys = values?.keys()?.asSequence()?.toList().orEmpty().sortedByDescending { values?.optLong(it) ?: 0 }
    val total = keys.sumOf { values?.optLong(it) ?: 0 }.coerceAtLeast(1)
    Card(colors = CardDefaults.cardColors(containerColor = HeColors.Panel)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (keys.isEmpty()) Text("暂无数据", color = HeColors.OpWhiteSoft)
            keys.forEach { key ->
                val n = values?.optLong(key) ?: 0
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(names[key] ?: key); Text(n.toString(), color = HeColors.OpWhiteSoft) }
                    LinearProgressIndicator(progress = { (n.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = HeColors.Yellow, trackColor = HeColors.OpSurface)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val unit = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble(); var i = 0
    while (value >= 1024 && i < unit.lastIndex) { value /= 1024; i++ }
    return String.format(Locale.getDefault(), "%.1f %s", value, unit[i])
}
