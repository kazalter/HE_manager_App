package com.hemanager.mobile.feature.library

// Library 模块复用的纯函数 / 半纯函数集合：颜色映射、文本格式化、URL 拼装等。
// 这些函数大多没有 state，可以独立测试。
//
// 命名约定：带 V2 后缀的函数是 HE OP UI 重写时新增的版本。
// 旧的无后缀版本中仅有语义重复的已清理（summary/meta/progressText/progressColor/typeLabel）。
// filterAccent/typeAccent/countForFilter 是按 mediaType 维度的分类颜色/计数，
// 与按 viewStatus 维度的 V2 系列不重叠，保留。后续有空可统一去掉 V2 后缀。

import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.MangaActivity
import com.hemanager.mobile.MediaItem
import android.content.Intent
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

@Composable
internal fun statusAccentV2(value: String): Color {
    return when (value) {
        "viewing" -> com.hemanager.mobile.ui.theme.HeColors.Yellow
        "favorite" -> com.hemanager.mobile.ui.theme.HeColors.StarAccent
        "viewed" -> com.hemanager.mobile.ui.theme.HeColors.Online
        else -> com.hemanager.mobile.ui.theme.HeColors.OpWhiteSoft
    }
}

@Composable
internal fun progressColorV2(item: MediaItem): Color {
    return when {
        item.missing -> Color(0xFFFF8CA3)
        item.viewStatus == "viewed" -> Color(0xFF58E6C2)
        item.viewStatus == "viewing" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

internal fun matchesStatusV2(item: MediaItem, value: String): Boolean {
    return when (value) {
        "viewing" -> item.viewStatus == "viewing"
        "favorite" -> item.favorite
        "viewed" -> item.viewStatus == "viewed"
        else -> true
    }
}

internal fun countForStatusV2(items: List<MediaItem>, value: String): Int {
    return items.count { matchesStatusV2(it, value) }
}

internal fun serverHostV2(serverUrl: String): String {
    return ApiClient.trimSlash(serverUrl)
        .removePrefix("http://")
        .removePrefix("https://")
}

internal fun mediaTypeLabelV2(type: String?): String {
    return when (type) {
        "video" -> "视频"
        "manga" -> "漫画"
        "image" -> "图片"
        "audio" -> "音频"
        else -> "媒体"
    }
}

internal fun mediaMetaChipsV2(item: MediaItem): List<String> {
    return listOf(
        cleanExtensionV2(item.extension),
        item.duration.takeIf { it > 0 }?.let { formatDuration(it) } ?: "",
        item.pageCount.takeIf { it > 0 }?.let { "${it} 页" } ?: "",
        item.rating.takeIf { it > 0 }?.let { "${it} 星" } ?: ""
    ).filter { it.isNotBlank() }
}

internal fun cleanExtensionV2(extension: String?): String {
    val raw = extension?.takeIf { it.isNotBlank() && it != "null" } ?: return ""
    val normalized = raw.lowercase(Locale.ROOT).removePrefix(".")
    if (normalized == "dir") return ""
    return raw.uppercase(Locale.ROOT)
}

internal fun metaInlineV2(item: MediaItem): String {
    val parts = mutableListOf<String>()
    parts += mediaTypeLabelV2(item.mediaType)
    cleanExtensionV2(item.extension).takeIf { it.isNotBlank() }?.let { parts += it }
    if (item.duration > 0) parts += formatDuration(item.duration)
    if (item.pageCount > 0) parts += "${item.pageCount}P"
    parts += progressTextV2(item)
    return parts.joinToString(" · ")
}

internal fun progressTextV2(item: MediaItem): String {
    if (item.viewStatus == "viewed") return "已看完"
    if (item.viewStatus == "viewing") {
        if (item.mediaType == "manga" && item.pageCount > 0) {
            return "第 ${minOf(item.progress + 1, item.pageCount)} / ${item.pageCount} 页"
        }
        if (item.mediaType == "video" && item.progress > 0) {
            return "看到 ${formatDuration(item.progress)}"
        }
        return "继续看"
    }
    return "未观看"
}

internal fun openItem(
    context: android.content.Context,
    item: MediaItem,
    serverUrl: String,
    token: String,
    restart: Boolean = false,
    playlist: List<MediaItem> = emptyList(),
) {
    val isVideo = item.mediaType == "video"
    val isAudio = item.mediaType == "audio"
    // 音频走独立的 ASMR 播放器（Media3 service-based 后台播放）；视频走 PlayerActivity；
    // 其他（漫画/图片）走 MangaActivity（图片借用漫画查看器的翻页能力）。
    val target = when {
        isVideo -> com.hemanager.mobile.player.PlayerActivity::class.java
        isAudio -> com.hemanager.mobile.audio.AudioPlayerActivity::class.java
        else -> MangaActivity::class.java
    }
    context.startActivity(Intent(context, target).apply {
        putExtra("server_url", serverUrl)
        putExtra("token", token)
        putExtra("id", item.id)
        putExtra("title", item.title)
        putExtra("media_type", item.mediaType)
        putExtra("progress", item.progress)
        putExtra("duration", item.duration)
        putExtra("page_count", item.pageCount)
        if (restart) putExtra("restart", true)
        if ((isVideo || item.mediaType == "image") && playlist.isNotEmpty()) {
            val ids = playlist.filter { it.mediaType == item.mediaType }.map { it.id }.toIntArray()
            if (ids.isNotEmpty()) putExtra("playlist_ids", ids)
        }
    })
}

internal fun coverUrl(serverUrl: String, token: String, item: MediaItem): String? {
    val cover = item.coverPath
    if (cover.isNullOrBlank() || cover == "null") return null
    // URL-encode the path so that thumbnails with non-ASCII characters (e.g. Japanese
    // dirname containing parens, brackets, CJK) reach the server intact.
    val encoded = android.net.Uri.encode(cover, "")
    return "$serverUrl/mobile/thumbnails/$encoded?${ApiClient.tokenQuery(token)}"
}

/** 创作者头像 URL（封面路径走 mobile/thumbnails，独立于 MediaItem.coverPath）。 */
internal fun creatorThumbUrl(serverUrl: String, token: String, coverPath: String?): String? {
    if (coverPath.isNullOrBlank() || coverPath == "null") return null
    val encoded = android.net.Uri.encode(coverPath, "")
    return "$serverUrl/mobile/thumbnails/$encoded?${ApiClient.tokenQuery(token)}"
}


/**
 * 图廊性能日志。
 *
 * 这些调用点全都在滚动 / pinch 的热路径上，而且参数是带字符串模板的，
 * 即使 Log.d 最终被丢弃，拼串也已经发生了。用一个编译期常量做开关，
 * 关掉时整段会被编译器消除，release 包里不留痕迹。
 */
internal fun logImageGalleryPerf(message: String) {
    if (!imageGalleryPerfLogEnabled) return
    Log.d(imageGalleryPerfLogTag, message)
}

internal fun greetingText(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..23 -> "晚上好"
        else -> "夜深了"
    }
}

internal fun countForFilter(items: List<MediaItem>, value: String): Int {
    return if (value.isBlank()) items.size else items.count { it.mediaType == value }
}



internal fun progressFraction(item: MediaItem): Float? {
    if (item.viewStatus == "viewed") return 1f
    if (item.viewStatus != "viewing") return null
    val fraction = when {
        item.mediaType == "manga" && item.pageCount > 0 -> (item.progress + 1).toFloat() / item.pageCount.toFloat()
        item.mediaType == "video" && item.duration > 0 -> item.progress.toFloat() / item.duration.toFloat()
        else -> return null
    }
    return fraction.coerceIn(0.02f, 1f)
}

@Composable
internal fun filterAccent(value: String): Color {
    return com.hemanager.mobile.ui.theme.HeColors.mediaAccent(value)
}

@Composable
internal fun typeAccent(type: String?): Color {
    return com.hemanager.mobile.ui.theme.HeColors.mediaAccent(type)
}



internal fun readableError(error: Throwable): String {
    if (error is ApiClient.UnauthorizedException) return "登录已过期，请重新登录"
    val message = error.message ?: return "读取失败"
    return when {
        message.contains("401", ignoreCase = true) -> "登录已过期，请重新登录"
        message.contains("Unauthorized", ignoreCase = true) -> "登录已过期，请重新登录"
        message.contains("Could not validate credentials", ignoreCase = true) -> "登录已过期，请重新登录"
        message.contains("登录已过期", ignoreCase = true) -> "登录已过期，请重新登录"
        message.contains("Failed to connect", ignoreCase = true) -> "无法连接服务器，请确认电脑端服务已启动"
        message.contains("timeout", ignoreCase = true) -> "连接超时，请检查网络或服务器地址"
        else -> message
    }
}



/**
 * 来源筛选匹配。
 * - "all"  → 全部通过
 * - "local" → 仅本地（sourceSite 为 null 或空）
 * - 其它   → 精确匹配（"x" / "wnacg" / "asmr"），大小写不敏感
 */
internal fun matchesSource(item: MediaItem, source: String): Boolean = when {
    source.isBlank() || source == "all" -> true
    source == "local" -> item.sourceSite.isNullOrBlank()
    else -> item.sourceSite?.equals(source, ignoreCase = true) == true
}

internal fun matchesSearch(item: MediaItem, query: String): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isBlank()) return true
    val fields = listOfNotNull(
        item.title,
        item.mediaType,
        item.extension,
        item.sourceSite,
        mediaTypeLabelV2(item.mediaType),
    )
    return fields.any { it.lowercase(Locale.ROOT).contains(needle) } ||
        item.tags?.any { tag -> tag.name.lowercase(Locale.ROOT).contains(needle) } == true
}

internal fun sortMediaItems(items: List<MediaItem>, sortFilter: String): List<MediaItem> {
    return when (sortFilter) {
        "opened" -> items.sortedWith(
            compareByDescending<MediaItem> { it.lastOpenedAt.orEmpty() }
                .thenByDescending { it.id }
        )
        "rating" -> items.sortedWith(
            compareByDescending<MediaItem> { it.rating }
                .thenByDescending { it.id }
        )
        "name" -> items.sortedWith(
            compareBy<MediaItem> { it.title.orEmpty().lowercase(Locale.ROOT) }
                .thenByDescending { it.id }
        )
        else -> items.sortedByDescending { it.id }
    }
}

/** HE OP 装饰代码：VID-A001 / MNG-X042 / ... 仅用于卡片角标装饰，不参与任何逻辑。 */
internal fun fakeCode(item: MediaItem): String {
    val prefix = when (item.mediaType) {
        "video" -> "VID"
        "manga" -> "MNG"
        "image" -> "IMG"
        "audio" -> "AUD"
        else -> "MED"
    }
    val letter = "AVXM"[(item.id % 4 + 4) % 4]
    val num = item.id.toString().padStart(3, '0').takeLast(3)
    return "$prefix-$letter$num"
}

internal fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val secs = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}

// ---------------------------------------------------------------------------
// 图廊瓦片大小 / 列数互换工具
// ---------------------------------------------------------------------------
// Gallery 模块的网格在 pinch-to-zoom 期间需要在「瓦片 dp 大小」和「列数」之间
// 来回换算（列数才是用户偏好的稳定单位，但绘制阶段需要瓦片大小驱动 ItemDecoration
// 和 measure 阶段）。这些函数是纯数学，没有 Compose 或 Android 依赖，方便测试。
//
// 参数定义：
//   availableWidthDp：RecyclerView 可用宽度（已扣除 padding）的 dp 值
//   gapDp：相邻瓦片之间的间距
//   columns：列数；调用方负责把它 clamp 到 [imageGalleryMinColumns, imageGalleryMaxColumns]

/** 给定列数反推每个瓦片的边长 dp。 */
internal fun imageGalleryTileForColumns(availableWidthDp: Float, gapDp: Float, columns: Int): Float {
    return ((availableWidthDp - gapDp * (columns - 1)) / columns).coerceAtLeast(1f)
}

/**
 * 给定一个自由瓦片 dp，吸附到「最近的列数对应的瓦片 dp」。
 * pinch 松手后用来 snap 到稳定状态。
 */
internal fun nearestImageGalleryPreset(sizeDp: Float, availableWidthDp: Float, gapDp: Float): Float {
    return (imageGalleryMinColumns..imageGalleryMaxColumns)
        .map { columns -> imageGalleryTileForColumns(availableWidthDp, gapDp, columns) }
        .minByOrNull { abs(it - sizeDp) }
        ?: imageGalleryTileForColumns(availableWidthDp, gapDp, 5)
}

/**
 * 给定瓦片 dp 推断目标列数。可选 hysteresis（迟滞）防止用户在阈值附近抖动时列数反复跳。
 *
 * 迟滞规则：仅当瓦片大小越过「两个相邻列数的瓦片中点 ± switchMargin」才切换，避免边界抖。
 *
 * @param withHysteresis 默认按"最近列数"硬切；为 true 时启用迟滞，需要传 [currentColumns]。
 */
internal fun imageGalleryColumnsForTile(
    sizeDp: Float,
    availableWidthDp: Float,
    gapDp: Float,
    currentColumns: Int,
    withHysteresis: Boolean
): Int {
    val rawColumns = floor((availableWidthDp + gapDp) / (sizeDp + gapDp))
        .toInt()
        .coerceIn(imageGalleryMinColumns, imageGalleryMaxColumns)
    if (!withHysteresis) return rawColumns

    val current = currentColumns.coerceIn(imageGalleryMinColumns, imageGalleryMaxColumns)
    if (rawColumns == current) return current

    val currentTile = imageGalleryTileForColumns(availableWidthDp, gapDp, current)
    val targetTile = imageGalleryTileForColumns(availableWidthDp, gapDp, rawColumns)
    val midpoint = (currentTile + targetTile) / 2f
    val switchMarginDp = 3.5f
    return if (rawColumns < current) {
        if (sizeDp >= midpoint + switchMarginDp) rawColumns else current
    } else {
        if (sizeDp <= midpoint - switchMarginDp) rawColumns else current
    }
}
