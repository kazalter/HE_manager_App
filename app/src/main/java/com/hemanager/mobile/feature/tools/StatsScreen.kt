package com.hemanager.mobile.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.saveable.rememberSaveable
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
    val state=rememberNativeActions(serverUrl,token,onLogout)
    var overview by remember {mutableStateOf<JSONObject?>(null)};var distribution by remember {mutableStateOf<JSONObject?>(null)}
    var activity by remember {mutableStateOf<JSONObject?>(null)};var attention by remember {mutableStateOf<JSONObject?>(null)};var highlights by remember {mutableStateOf<JSONObject?>(null)}
    var detail by rememberSaveable {mutableIntStateOf(0)};var creator by remember {mutableStateOf<JSONObject?>(null)}
    var activityType by rememberSaveable {mutableStateOf("")}
    suspend fun load() {
        val result=coroutineScope {listOf("/stats/overview","/stats/distribution","/stats/activity?days=365","/stats/attention","/stats/highlights?limit=10").map {async {state.api.get(it)}}.awaitAll()}
        overview=result[0];distribution=result[1];activity=result[2];attention=result[3];highlights=result[4]
    }
    LaunchedEffect(Unit) {state.run {load()}}
    if(detail>0) {NativeMediaDetail(serverUrl,token,detail,{detail=0;state.run {load()}},onLogout);return}
    if(creator!=null) {
        NativeScene(creator!!.optJSONObject("creator")?.text("display_name","创作者") ?: "创作者",state,{creator=null},{}) {
            items(creator!!.optJSONArray("media").objects()) {m->NativeMediaRow(m,state.api) {detail=m.optInt("id")}}
        };return
    }
    NativeScene("统计看板",state,onBack,{state.run {load()}}) {
        overview?.let {o->
            item {StatPair("媒体总数",o.optInt("total").toString(),"总容量",formatBytes(o.optLong("total_size_bytes")))}
            item {StatPair("收藏",o.optInt("favorites").toString(),"平均评分",String.format(Locale.getDefault(),"%.1f / 5",o.optDouble("average_rating",0.0)))}
            item {StatTile("音视频总时长","${o.optLong("total_duration_seconds")/3600} 小时",Modifier.fillMaxWidth())}
            item {StatBars("媒体分类",o.optJSONObject("by_type"),mapOf("video" to "视频","manga" to "漫画","image" to "杂图","audio" to "音频"))}
            item {StatBars("观看状态",o.optJSONObject("view_status"),mapOf("unviewed" to "未看","viewing" to "在看","viewed" to "已看"))}
            item {StatTile("缺失文件",o.optInt("missing").toString(),Modifier.fillMaxWidth())}
        }
        distribution?.let {d->
            item {StatBars("来源分布",d.optJSONObject("by_source"),mapOf("local" to "本地","x" to "X","wnacg" to "WNACG","asmr" to "ASMR","pawchive" to "Pawchive"))}
            item {StatBars("评分分布",d.optJSONObject("rating_histogram"),(0..5).associate {it.toString() to if(it==0) "未评分" else "$it 星"})}
            item {NativeSection("媒体增长（月度）") {
                val rows=d.optJSONArray("growth").objects();val max=rows.maxOfOrNull {it.optInt("added")}?.coerceAtLeast(1) ?: 1
                rows.forEach {r->Text("${r.text("month")} · 新增 ${r.optInt("added")} · 累计 ${r.optInt("cumulative")}");LinearProgressIndicator(progress={r.optInt("added").toFloat()/max},modifier=Modifier.fillMaxWidth(),color=HeColors.Yellow)}
                if(rows.isEmpty()) Text("暂无增长记录")
            } }
        }
        activity?.let {a->item {NativeSection("过去一年活跃记录") {
            NativeChoice("媒体类型",activityType,listOf("" to "全部","video" to "视频","manga" to "漫画","image" to "杂图","audio" to "音频")) {activityType=it}
            val rows=(if(activityType.isBlank()) a.optJSONArray("buckets") else a.optJSONObject("by_type")?.optJSONArray(activityType)).objects()
            Text("${rows.sumOf {it.optInt("count")}} 次最近打开记录 · 活跃 ${rows.size} 天",color=HeColors.OpWhiteSoft)
            NativeHeatmap(a.text("to_date"),rows)
            rows.takeLast(7).forEach {Text("${it.text("date")} · ${it.optInt("count")} 次")}
        } } }
        attention?.let {a->listOf("dusty" to "高分但久未打开","unrated" to "已看但未评分").forEach {(key,title)->
            item {Text(title,style=MaterialTheme.typography.titleMedium)}
            val rows=a.optJSONArray(key).objects();if(rows.isEmpty()) item {Text("暂无待整理作品",color=HeColors.OpWhiteSoft)}
            items(rows) {m->NativeMediaRow(m,state.api) {detail=m.optInt("id")}}
        } }
        highlights?.let {h->
            item {NativeSection("创作者排行") {h.optJSONArray("top_creators").objects().forEach {c->TextButton(onClick={state.run {creator=state.api.get(query("/mobile/creators/detail","key" to c.text("key")))}},enabled=!state.busy) {Text("${c.text("display_name")} · ${c.optInt("media_count")} 个作品")}}}}
            item {NativeSection("热门标签") {h.optJSONArray("top_tags").objects().forEach {t->Text("${t.text("namespace")}:${t.text("name")} · ${t.optInt("count")} 个作品")}}}
            item {Text("长视频排行",style=MaterialTheme.typography.titleMedium)}
            items(h.optJSONArray("top_videos").objects()) {m->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {NativeMediaRow(m,state.api) {detail=m.optInt("id")};Text("${m.optInt("duration")/60} 分钟 · ${formatBytes(m.optLong("file_size"))}",color=HeColors.OpWhiteSoft)}}
        }
    }
}

@Composable private fun NativeHeatmap(toDate: String,rows: List<JSONObject>) {
    val dates=remember(toDate) {
        val format=java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).apply {timeZone=java.util.TimeZone.getTimeZone("UTC")}
        val end=runCatching {format.parse(toDate)?.time}.getOrNull() ?: System.currentTimeMillis()
        (0 until 365).map {format.format(java.util.Date(end-(364-it)*86400000L))}
    }
    val counts=rows.associate {it.text("date") to it.optInt("count")};val max=counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    var selected by remember {mutableStateOf("")};val active=HeColors.Yellow;val inactive=HeColors.OpSurface
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics {contentDescription="过去一年活跃热图，${counts.size} 天有记录，颜色越深记录越多"}.pointerInput(dates,counts) {detectTapGestures {p->val index=(p.x/(size.width/53f)).toInt()*7+(p.y/(size.height/7f)).toInt();if(index in dates.indices) selected="${dates[index]} · ${counts[dates[index]] ?: 0} 次"}}) {
        val w=size.width/53;val h=size.height/7
        dates.forEachIndexed {i,date->val n=counts[date] ?: 0;drawRect(if(n==0) inactive else active.copy(alpha=0.3f+0.7f*n/max),Offset((i/7)*w,(i%7)*h),Size((w-2).coerceAtLeast(1f),(h-2).coerceAtLeast(1f)))}
    }
    Text(selected.ifBlank {"点击日期查看记录 · 浅色少，深色多"},style=MaterialTheme.typography.bodySmall,color=HeColors.OpWhiteSoft)
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
