@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hemanager.mobile.MediaItem
import com.hemanager.mobile.feature.library.openItem
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import com.hemanager.mobile.ui.theme.NotoSansSC
import com.hemanager.mobile.ui.theme.Oxanium
import org.json.JSONObject

@Composable
internal fun NativeMediaRow(item: JSONObject, api: NativeApi, onClick: () -> Unit) {
    val type = item.text("media_type")
    val typeAccent = HeColors.mediaAccent(type)
    val shape = RoundedCornerShape(14.dp)
    val isMissing = item.optBoolean("is_missing")
    val isFav = item.optBoolean("favorite")
    val rating = item.optInt("rating")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HeColors.PanelGradient)
            .border(1.dp, if (isMissing) HeColors.OpDanger.copy(alpha = 0.5f) else HeColors.HairlineMid, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with sleek border & category placeholder
            val cover = item.text("cover_path")
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HeColors.Void)
                    .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (cover.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(api.origin + "/mobile/thumbnails/" + encoded(cover))
                            .addHeader("Authorization", "Bearer ${api.token}")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = when (type) {
                            "video" -> Icons.Default.PlayCircle
                            "manga" -> Icons.Default.MenuBook
                            "audio" -> Icons.Default.Headphones
                            else -> Icons.Default.Image
                        },
                        contentDescription = null,
                        tint = typeAccent.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.text("title", "未命名"),
                    style = MaterialTheme.typography.titleSmall,
                    color = HeColors.OpWhite,
                    fontFamily = NotoSansSC,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Type pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HeColors.mediaChipBg(type))
                            .border(1.dp, HeColors.mediaChipBorder(type), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = mediaTypeName(type),
                            fontFamily = NotoSansSC,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = typeAccent
                        )
                    }

                    // Rating badge
                    if (rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HeColors.Yellow.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = HeColors.Yellow,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "$rating 星",
                                fontFamily = GeistMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = HeColors.Yellow
                            )
                        }
                    }

                    // Favorite pill
                    if (isFav) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HeColors.StarAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "★ 收藏",
                                fontFamily = NotoSansSC,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = HeColors.StarAccent
                            )
                        }
                    }

                    // Missing badge
                    if (isMissing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HeColors.OpDanger.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "文件缺失",
                                fontFamily = NotoSansSC,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = HeColors.OpDanger
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NativeMediaScreen(
    server: String,
    token: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    initialFilter: String = ""
) {
    val state = rememberNativeActions(server, token, onLogout)
    var records by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("date") }
    var tag by rememberSaveable { mutableStateOf("") }
    var tagDraft by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var viewStatus by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableIntStateOf(0) }

    suspend fun load() {
        val r = state.api.request(
            query(
                "/media",
                "limit" to 24,
                "offset" to page * 24,
                "search" to applied,
                "media_type" to type,
                "sort" to sort,
                "tag" to tag,
                "source_site" to source,
                "view_status" to viewStatus,
                "favorite" to if (filter == "favorite") true else null,
                "is_missing" to if (filter == "missing") true else null
            )
        )
        records = (r.value as org.json.JSONArray).objects()
        total = r.total ?: records.size
    }

    LaunchedEffect(page, applied, type, sort, tag, source, filter, viewStatus) { state.run { load() } }

    if (detail != 0) {
        NativeMediaDetail(server, token, detail, { detail = 0; state.run { load() } }, onLogout)
        return
    }

    NativeScene("媒体管理", state, onBack, { state.run { load() } }, subtitle = "// MEDIA DATABASE") {
        item {
            NativeSection("筛选与检索") {
                NativeField("搜索标题", search, { search = it }, enabled = !state.busy)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { applied = search.trim(); tag = tagDraft.trim(); page = 0 },
                        enabled = !state.busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HeColors.Yellow,
                            contentColor = HeColors.OnYellow
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("应用筛选", fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { advanced = !advanced }) {
                        Text(
                            if (advanced) "收起高级筛选" else "展开高级筛选",
                            color = HeColors.Yellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                NativeChoice(
                    "类型",
                    type,
                    listOf("" to "全部", "video" to "视频", "manga" to "漫画", "image" to "杂图", "audio" to "音频"),
                    !state.busy
                ) { type = it; page = 0 }

                if (advanced) {
                    NativeChoice(
                        "范围",
                        filter,
                        listOf("" to "全部", "favorite" to "收藏", "missing" to "缺失文件"),
                        !state.busy
                    ) { filter = it; page = 0 }

                    NativeChoice(
                        "观看状态",
                        viewStatus,
                        listOf("" to "全部", "unviewed" to "未看", "viewing" to "在看", "viewed" to "已看"),
                        !state.busy
                    ) { viewStatus = it; page = 0 }

                    NativeChoice(
                        "排序",
                        sort,
                        listOf("date" to "最新", "title" to "标题", "rating" to "评分", "opened" to "最近打开"),
                        !state.busy
                    ) { sort = it; page = 0 }

                    NativeField("标签名称（点击应用筛选生效）", tagDraft, { tagDraft = it }, enabled = !state.busy)

                    NativeChoice(
                        "来源",
                        source,
                        listOf("" to "全部", "local" to "本地", "x" to "X", "wnacg" to "WNACG", "asmr" to "ASMR", "pawchive" to "Pawchive"),
                        !state.busy
                    ) { source = it; page = 0 }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { grid = !grid }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = grid,
                        onCheckedChange = { grid = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = HeColors.Yellow,
                            checkmarkColor = HeColors.OnYellow
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("双列布局", color = HeColors.OpWhite, fontSize = 13.5.sp)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HeColors.Void.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(HeColors.Yellow))
                    Text(
                        text = "共 $total 个媒体",
                        color = HeColors.OpWhite,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
                Text(
                    text = "PAGE ${page + 1} / ${maxOf(1, (total + 23) / 24)}",
                    fontFamily = Oxanium,
                    color = HeColors.OpWhiteSoft,
                    fontSize = 11.5.sp
                )
            }
        }

        if (grid) {
            items(records.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m ->
                        Box(Modifier.weight(1f)) {
                            NativeMediaRow(m, state.api) { detail = m.optInt("id") }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(records, key = { it.optInt("id") }) { m ->
                NativeMediaRow(m, state.api) { detail = m.optInt("id") }
            }
        }

        if (records.isEmpty() && !state.busy) {
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
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = HeColors.OpWhiteMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text("暂无符合条件的媒体", color = HeColors.OpWhiteSoft, fontFamily = NotoSansSC)
                    }
                }
            }
        }

        item {
            NativePagination(page, total, 24, !state.busy) { page = it }
        }
    }
}

@Composable
internal fun NativePagination(page: Int, total: Int, size: Int, enabled: Boolean, onPage: (Int) -> Unit) {
    val maxPage = maxOf(1, (total + size - 1) / size)
    val hasPrev = enabled && page > 0
    val hasNext = enabled && (page + 1) * size < total

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HeColors.PanelGradient)
            .border(1.dp, HeColors.HairlineRim, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onPage(page - 1) },
            enabled = hasPrev,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = HeColors.OpWhite,
                disabledContentColor = HeColors.OpWhiteFaint
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(if (hasPrev) HeColors.HairlineHi else HeColors.Hairline)
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("上一页", fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(HeColors.OpSurface)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${page + 1} / $maxPage",
                fontFamily = Oxanium,
                fontWeight = FontWeight.Bold,
                color = HeColors.Yellow,
                fontSize = 13.sp
            )
        }

        OutlinedButton(
            onClick = { onPage(page + 1) },
            enabled = hasNext,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = HeColors.OpWhite,
                disabledContentColor = HeColors.OpWhiteFaint
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(if (hasNext) HeColors.HairlineHi else HeColors.Hairline)
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("下一页", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun NativeMediaDetail(server: String, token: String, id: Int, onBack: () -> Unit, onLogout: () -> Unit) {
    val state = rememberNativeActions(server, token, onLogout)
    val context = LocalContext.current
    var media by remember(id) { mutableStateOf<JSONObject?>(null) }
    var title by rememberSaveable(id) { mutableStateOf("") }
    var duration by rememberSaveable(id) { mutableStateOf("") }
    var progress by rememberSaveable(id) { mutableStateOf("") }
    var sourceUrl by rememberSaveable(id) { mutableStateOf("") }
    var sourceSite by rememberSaveable(id) { mutableStateOf("") }
    var viewStatus by rememberSaveable(id) { mutableStateOf("unviewed") }
    var tag by rememberSaveable(id) { mutableStateOf("") }

    suspend fun load() {
        media = state.api.get("/media/$id")
        media?.let {
            title = it.text("title")
            duration = it.optInt("duration").toString()
            progress = it.optInt("progress").toString()
            sourceUrl = it.text("source_url")
            sourceSite = it.text("source_site")
            viewStatus = it.text("view_status", "unviewed")
        }
    }

    LaunchedEffect(id) { state.run { load() } }

    NativeScene("媒体详情", state, onBack, { state.run { load() } }, subtitle = "// MEDIA ITEM #$id") {
        media?.let { m ->
            item {
                NativeMediaRow(m, state.api) {
                    if (!state.busy && !m.optBoolean("is_missing")) openItem(context, MediaItem.fromJson(m), server, token)
                }
            }

            item {
                NativeSection("观看与收藏") {
                    Button(
                        onClick = { openItem(context, MediaItem.fromJson(m), server, token) },
                        enabled = !state.busy && !m.optBoolean("is_missing"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HeColors.Yellow,
                            contentColor = HeColors.OnYellow
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("打开原生播放器 / 阅读器", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { state.run { media = state.api.send("/media/$id", obj("favorite" to !m.optBoolean("favorite")), "PATCH") } },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (m.optBoolean("favorite")) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (m.optBoolean("favorite")) HeColors.StarAccent else HeColors.OpWhiteSoft
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (m.optBoolean("favorite")) "取消收藏" else "收藏")
                    }

                    NativeChoice(
                        "评分",
                        m.optInt("rating").toString(),
                        (0..5).map { it.toString() to if (it == 0) "未评" else "$it 星" },
                        !state.busy
                    ) { r ->
                        state.run { media = state.api.send("/media/$id", obj("rating" to r.toInt()), "PATCH") }
                    }
                }
            }

            item {
                NativeSection("编辑信息") {
                    NativeField("标题", title, { title = it }, enabled = !state.busy)
                    NativeField("时长（秒）", duration, { duration = it }, enabled = !state.busy)
                    NativeField("进度（视频秒 / 漫画页）", progress, { progress = it }, enabled = !state.busy)
                    NativeChoice(
                        "观看状态",
                        viewStatus,
                        listOf("unviewed" to "未看", "viewing" to "在看", "viewed" to "已看"),
                        !state.busy
                    ) { viewStatus = it }
                    NativeField("来源网址", sourceUrl, { sourceUrl = it }, enabled = !state.busy)
                    NativeField("来源站点", sourceSite, { sourceSite = it }, enabled = !state.busy)
                    Button(
                        onClick = {
                            state.run {
                                require(title.isNotBlank()) { "标题不能为空" }
                                val d = duration.toIntOrNull()
                                val p = progress.toIntOrNull()
                                require(d != null && d >= 0 && p != null && p >= 0) { "时长和进度必须是非负整数" }
                                media = state.api.send(
                                    "/media/$id",
                                    obj(
                                        "title" to title.trim(),
                                        "duration" to d,
                                        "progress" to p,
                                        "view_status" to viewStatus,
                                        "source_url" to sourceUrl.trim(),
                                        "source_site" to sourceSite.trim()
                                    ),
                                    "PATCH"
                                )
                                state.message = "信息已保存"
                            }
                        },
                        enabled = !state.busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HeColors.Yellow,
                            contentColor = HeColors.OnYellow
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("保存信息", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                NativeSection("标签") {
                    val tagList = m.optJSONArray("tags").objects()
                    if (tagList.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tagList.forEach { t ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(HeColors.OpSurface)
                                        .border(1.dp, HeColors.HairlineMid, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(t.text("name"), color = HeColors.OpWhite, fontSize = 13.sp)
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "移除",
                                            tint = HeColors.OpWhiteSoft,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable(enabled = !state.busy) {
                                                    state.confirm("移除标签“${t.text("name")}”？") {
                                                        media = state.api.request("/media/$id/tags/${t.optInt("id")}", "DELETE").value as JSONObject
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    NativeField("新标签", tag, { tag = it }, enabled = !state.busy)
                    Button(
                        onClick = {
                            state.run {
                                media = state.api.send("/media/$id/tags", obj("name" to tag.trim()))
                                tag = ""
                            }
                        },
                        enabled = !state.busy && tag.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("添加标签", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                NativeSection("维护") {
                    Text(
                        text = m.text("absolute_path", m.text("relative_path")),
                        color = HeColors.OpWhiteSoft,
                        fontFamily = GeistMono,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedButton(
                        onClick = {
                            state.run {
                                state.api.send("/media/$id/recheck")
                                load()
                                state.message = "已重新检查文件"
                            }
                        },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("重新检查文件")
                    }
                    OutlinedButton(
                        onClick = {
                            state.run {
                                state.api.send("/media/$id/regenerate-thumbnail")
                                load()
                                state.message = "缩略图已更新"
                            }
                        },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("重新生成缩略图")
                    }
                    TextButton(
                        onClick = {
                            state.confirm("删除“${m.text("title")}”的媒体记录？此操作无法撤销。") {
                                state.api.request("/media/$id", "DELETE")
                                onBack()
                            }
                        },
                        enabled = !state.busy
                    ) {
                        Text("删除媒体记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
