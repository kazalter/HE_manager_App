@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemanager.mobile.ui.op.AngularPanel
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.Locale

@Composable
internal fun StatsScreen(serverUrl: String, token: String, onBack: () -> Unit, onLogout: () -> Unit) {
    val state = rememberNativeActions(serverUrl, token, onLogout)
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var distribution by remember { mutableStateOf<JSONObject?>(null) }
    var activity by remember { mutableStateOf<JSONObject?>(null) }
    var attention by remember { mutableStateOf<JSONObject?>(null) }
    var highlights by remember { mutableStateOf<JSONObject?>(null) }
    var detail by rememberSaveable { mutableIntStateOf(0) }
    var creator by remember { mutableStateOf<JSONObject?>(null) }
    var activityType by rememberSaveable { mutableStateOf("") }

    suspend fun load() {
        val result = coroutineScope {
            listOf(
                "/stats/overview",
                "/stats/distribution",
                "/stats/activity?days=365",
                "/stats/attention",
                "/stats/highlights?limit=10"
            ).map { async { state.api.get(it) } }.awaitAll()
        }
        overview = result[0]
        distribution = result[1]
        activity = result[2]
        attention = result[3]
        highlights = result[4]
    }

    LaunchedEffect(Unit) { state.run { load() } }

    if (detail > 0) {
        NativeMediaDetail(serverUrl, token, detail, { detail = 0; state.run { load() } }, onLogout)
        return
    }

    if (creator != null) {
        NativeScene(
            creator!!.optJSONObject("creator")?.text("display_name", "创作者") ?: "创作者",
            state,
            { creator = null },
            {},
            subtitle = "// CREATOR ARCHIVE"
        ) {
            items(creator!!.optJSONArray("media").objects()) { m ->
                NativeMediaRow(m, state.api) { detail = m.optInt("id") }
            }
        }
        return
    }

    NativeScene("统计看板", state, onBack, { state.run { load() } }, subtitle = "// ANALYTICS & INSIGHTS") {
        overview?.let { o ->
            item {
                StatPair(
                    "媒体总数", o.optInt("total").toString(), Icons.Default.Storage, HeColors.ImageAccent,
                    "总容量", formatBytes(o.optLong("total_size_bytes")), Icons.Default.Dns, HeColors.VideoAccent
                )
            }
            item {
                StatPair(
                    "收藏", o.optInt("favorites").toString(), Icons.Default.Star, HeColors.StarAccent,
                    "平均评分", String.format(Locale.getDefault(), "%.1f / 5", o.optDouble("average_rating", 0.0)), Icons.Default.AutoAwesome, HeColors.Yellow
                )
            }
            item {
                StatTile(
                    label = "音视频总时长",
                    value = "${o.optLong("total_duration_seconds") / 3600} 小时",
                    icon = Icons.Default.Schedule,
                    accentColor = HeColors.Cyan,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                StatBars(
                    "媒体分类",
                    o.optJSONObject("by_type"),
                    mapOf("video" to "视频", "manga" to "漫画", "image" to "杂图", "audio" to "音频"),
                    isMediaType = true
                )
            }
            item {
                StatBars(
                    "观看状态",
                    o.optJSONObject("view_status"),
                    mapOf("unviewed" to "未看", "viewing" to "在看", "viewed" to "已看"),
                    isViewStatus = true
                )
            }
            if (o.optInt("missing") > 0) {
                item {
                    StatTile(
                        label = "缺失文件",
                        value = "${o.optInt("missing")} 项",
                        icon = Icons.Default.Warning,
                        accentColor = HeColors.OpDanger,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        distribution?.let { d ->
            item {
                StatBars(
                    "来源分布",
                    d.optJSONObject("by_source"),
                    mapOf("local" to "本地", "x" to "X", "wnacg" to "WNACG", "asmr" to "ASMR", "pawchive" to "Pawchive")
                )
            }
            item {
                StatBars(
                    "评分分布",
                    d.optJSONObject("rating_histogram"),
                    (0..5).associate { it.toString() to if (it == 0) "未评分" else "$it 星" }
                )
            }
            item {
                NativeSection("媒体增长（月度）") {
                    val rows = d.optJSONArray("growth").objects()
                    val max = rows.maxOfOrNull { it.optInt("added") }?.coerceAtLeast(1) ?: 1
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { r ->
                            val added = r.optInt("added")
                            val cumulative = r.optInt("cumulative")
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = r.text("month"),
                                        fontFamily = Oxanium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = HeColors.OpWhite
                                    )
                                    Text(
                                        text = "新增 $added · 累计 $cumulative",
                                        fontFamily = GeistMono,
                                        fontSize = 12.sp,
                                        color = HeColors.OpWhiteSoft
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { added.toFloat() / max },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(2.5.dp)),
                                    color = HeColors.Yellow,
                                    trackColor = HeColors.OpSurface
                                )
                            }
                        }
                    }
                    if (rows.isEmpty()) Text("暂无增长记录", color = HeColors.OpWhiteSoft)
                }
            }
        }

        activity?.let { a ->
            item {
                NativeSection("过去一年活跃记录") {
                    NativeChoice(
                        "媒体类型",
                        activityType,
                        listOf("" to "全部", "video" to "视频", "manga" to "漫画", "image" to "杂图", "audio" to "音频")
                    ) { activityType = it }

                    val rows = (if (activityType.isBlank()) a.optJSONArray("buckets") else a.optJSONObject("by_type")?.optJSONArray(activityType)).objects()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(HeColors.Void.copy(alpha = 0.4f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(HeColors.Yellow))
                        Text(
                            text = "${rows.sumOf { it.optInt("count") }} 次最近打开记录 · 活跃 ${rows.size} 天",
                            fontFamily = GeistMono,
                            fontSize = 12.5.sp,
                            color = HeColors.OpWhiteSoft
                        )
                    }

                    NativeHeatmap(a.text("to_date"), rows)

                    if (rows.isNotEmpty()) {
                        Text(
                            text = "近期活跃概览",
                            style = MaterialTheme.typography.labelMedium,
                            color = HeColors.Yellow,
                            fontFamily = NotoSansSC,
                            fontWeight = FontWeight.Bold
                        )
                        rows.takeLast(7).forEach {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(it.text("date"), fontFamily = GeistMono, color = HeColors.OpWhiteSoft, fontSize = 12.5.sp)
                                Text("${it.optInt("count")} 次", fontFamily = Oxanium, fontWeight = FontWeight.Bold, color = HeColors.Yellow, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }

        attention?.let { a ->
            listOf("dusty" to "高分但久未打开", "unrated" to "已看但未评分").forEach { (key, title) ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(1.5.dp)).background(HeColors.Cyan))
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HeColors.OpWhite, fontFamily = NotoSansSC)
                    }
                }
                val rows = a.optJSONArray(key).objects()
                if (rows.isEmpty()) item {
                    Text("暂无待整理作品", color = HeColors.OpWhiteSoft, modifier = Modifier.padding(start = 9.dp))
                }
                items(rows) { m -> NativeMediaRow(m, state.api) { detail = m.optInt("id") } }
            }
        }

        highlights?.let { h ->
            item {
                NativeSection("创作者排行") {
                    h.optJSONArray("top_creators").objects().forEachIndexed { index, c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(HeColors.Void.copy(alpha = 0.35f))
                                .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d", index + 1),
                                    fontFamily = Oxanium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (index < 3) HeColors.Yellow else HeColors.OpWhiteMuted
                                )
                                TextButton(
                                    onClick = { state.run { creator = state.api.get(query("/mobile/creators/detail", "key" to c.text("key"))) } },
                                    enabled = !state.busy
                                ) {
                                    Text(
                                        c.text("display_name"),
                                        fontFamily = NotoSansSC,
                                        fontWeight = FontWeight.Bold,
                                        color = HeColors.OpWhite,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Text(
                                text = "${c.optInt("media_count")} 作品",
                                fontFamily = GeistMono,
                                fontSize = 12.sp,
                                color = HeColors.Yellow
                            )
                        }
                    }
                }
            }

            item {
                NativeSection("热门标签") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        h.optJSONArray("top_tags").objects().forEach { t ->
                            val ns = t.text("namespace")
                            val accent = when (ns) {
                                "artist" -> HeColors.VideoAccent
                                "character" -> HeColors.ImageAccent
                                "parody" -> HeColors.AudioAccent
                                "source" -> HeColors.MangaAccent
                                else -> HeColors.OpWhiteSoft
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.12f))
                                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${t.text("namespace")}:${t.text("name")}",
                                        color = HeColors.OpWhite,
                                        fontSize = 12.5.sp,
                                        fontFamily = NotoSansSC
                                    )
                                    Text(
                                        text = t.optInt("count").toString(),
                                        fontFamily = Oxanium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = accent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Box(Modifier.width(3.dp).height(13.dp).clip(RoundedCornerShape(1.5.dp)).background(HeColors.VideoAccent))
                    Text("长视频排行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HeColors.OpWhite, fontFamily = NotoSansSC)
                }
            }
            items(h.optJSONArray("top_videos").objects()) { m ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NativeMediaRow(m, state.api) { detail = m.optInt("id") }
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${m.optInt("duration") / 60} 分钟",
                            fontFamily = Oxanium,
                            fontWeight = FontWeight.Bold,
                            color = HeColors.VideoAccent,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "· ${formatBytes(m.optLong("file_size"))}",
                            fontFamily = GeistMono,
                            color = HeColors.OpWhiteSoft,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeHeatmap(toDate: String, rows: List<JSONObject>) {
    val dates = remember(toDate) {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val end = runCatching { format.parse(toDate)?.time }.getOrNull() ?: System.currentTimeMillis()
        (0 until 365).map { format.format(java.util.Date(end - (364 - it) * 86400000L)) }
    }
    val counts = rows.associate { it.text("date") to it.optInt("count") }
    val max = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    var selected by remember { mutableStateOf("") }
    val active = HeColors.Yellow
    val inactive = HeColors.OpSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HeColors.Void.copy(alpha = 0.5f))
            .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = "过去一年活跃热图，${counts.size} 天有记录，颜色越深记录越多" }
                .pointerInput(dates, counts) {
                    detectTapGestures { p ->
                        val index = (p.x / (size.width / 53f)).toInt() * 7 + (p.y / (size.height / 7f)).toInt()
                        if (index in dates.indices) selected = "${dates[index]} · ${counts[dates[index]] ?: 0} 次打开"
                    }
                }
        ) {
            val w = size.width / 53
            val h = size.height / 7
            dates.forEachIndexed { i, date ->
                val n = counts[date] ?: 0
                drawRect(
                    if (n == 0) inactive else active.copy(alpha = (0.25f + 0.75f * n / max).coerceIn(0.2f, 1f)),
                    Offset((i / 7) * w, (i % 7) * h),
                    Size((w - 2).coerceAtLeast(1f), (h - 2).coerceAtLeast(1f))
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selected.ifBlank { "点击方格查看日期 · 浅色少，深色多" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = GeistMono,
            color = if (selected.isNotBlank()) HeColors.Yellow else HeColors.OpWhiteSoft
        )
    }
}

@Composable
private fun StatPair(
    firstLabel: String, firstValue: String, firstIcon: ImageVector, firstAccent: Color,
    secondLabel: String, secondValue: String, secondIcon: ImageVector, secondAccent: Color
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints {
        if (largeText || maxWidth < 340.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(firstLabel, firstValue, firstIcon, firstAccent, Modifier.fillMaxWidth())
                StatTile(secondLabel, secondValue, secondIcon, secondAccent, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(firstLabel, firstValue, firstIcon, firstAccent, Modifier.weight(1f))
                StatTile(secondLabel, secondValue, secondIcon, secondAccent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector = Icons.Default.Insights,
    accentColor: Color = HeColors.Yellow,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
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
                Text(
                    text = label,
                    color = HeColors.OpWhiteSoft,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = NotoSansSC,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = value,
                fontFamily = Oxanium,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = HeColors.OpWhite
            )
        }
    }
}

@Composable
private fun StatBars(
    title: String,
    values: JSONObject?,
    names: Map<String, String>,
    isMediaType: Boolean = false,
    isViewStatus: Boolean = false
) {
    val keys = values?.keys()?.asSequence()?.toList().orEmpty().sortedByDescending { values?.optLong(it) ?: 0 }
    val total = keys.sumOf { values?.optLong(it) ?: 0 }.coerceAtLeast(1)

    NativeSection(title) {
        if (keys.isEmpty()) Text("暂无数据", color = HeColors.OpWhiteSoft)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.forEach { key ->
                val n = values?.optLong(key) ?: 0
                val pct = (n.toFloat() / total * 100).toInt()
                val accent = when {
                    isMediaType -> when (key.lowercase()) {
                        "video" -> HeColors.VideoAccent
                        "manga" -> HeColors.MangaAccent
                        "image" -> HeColors.ImageAccent
                        "audio" -> HeColors.AudioAccent
                        else -> HeColors.Yellow
                    }
                    isViewStatus -> when (key.lowercase()) {
                        "viewed" -> HeColors.Online
                        "viewing" -> HeColors.Yellow
                        else -> HeColors.OpWhiteMuted
                    }
                    else -> HeColors.Yellow
                }

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = names[key] ?: key,
                                color = HeColors.OpWhite,
                                fontFamily = NotoSansSC,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = n.toString(),
                                fontFamily = GeistMono,
                                color = HeColors.OpWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "($pct%)",
                                fontFamily = Oxanium,
                                color = HeColors.OpWhiteSoft,
                                fontSize = 12.sp
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (n.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp)),
                        color = accent,
                        trackColor = HeColors.OpSurface
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val unit = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < unit.lastIndex) {
        value /= 1024
        i++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, unit[i])
}
