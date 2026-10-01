package com.hemanager.mobile.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.hemanager.mobile.ui.theme.GeistMono
import com.hemanager.mobile.ui.theme.HeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.min

/** The image viewport stays fixed while controls appear over it. */
@Composable
internal fun NativeAttachmentViewer(
    api: NativeApi,
    attachments: List<JSONObject>,
    index: Int,
    onIndex: (Int) -> Unit,
    onClose: () -> Unit
) {
    if (attachments.isEmpty()) return
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = index.coerceIn(attachments.indices)) { attachments.size }
    val page = pager.currentPage.coerceIn(attachments.indices)
    val attachment = attachments[page]
    var controls by rememberSaveable { mutableStateOf(true) }
    var autoplay by rememberSaveable { mutableStateOf(false) }
    var interval by rememberSaveable { mutableIntStateOf(5) }
    var intervalMenu by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var imageReady by remember(page) { mutableStateOf(false) }
    var resetZoom by remember { mutableIntStateOf(0) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val updateIndex by rememberUpdatedState(onIndex)

    LaunchedEffect(pager.settledPage) { updateIndex(pager.settledPage) }
    LaunchedEffect(index) {
        if (index in attachments.indices && index != pager.currentPage && !pager.isScrollInProgress) pager.scrollToPage(index)
    }
    LaunchedEffect(page) {
        zoomed = false
        if (attachment.text("media_type") == "video") controls = true
    }
    LaunchedEffect(dragged, zoomed) { if (dragged || zoomed) autoplay = false }

    fun navigate(target: Int) {
        autoplay = false
        if (target in attachments.indices && !pager.isScrollInProgress) {
            resetZoom++
            scope.launch { pager.animateScrollToPage(target) }
        }
    }

    // Keep prefetch bounded to adjacent images; a full-size batch wastes memory on phones.
    LaunchedEffect(page, api.origin, api.token) {
        val requests = listOf(page - 1, page + 1).filter { it in attachments.indices }
            .map { attachments[it] }.filter { it.text("media_type") == "image" }.map {
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context).data(attachmentUrl(api, it))
                        .addHeader("Authorization", "Bearer ${api.token}").size(1440).build()
                )
            }
        try { kotlinx.coroutines.awaitCancellation() } finally { requests.forEach { it.dispose() } }
    }
    LaunchedEffect(page, autoplay, interval, imageReady, zoomed, pager.isScrollInProgress) {
        if (autoplay && imageReady && !zoomed && !pager.isScrollInProgress && attachment.text("media_type") == "image") {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(interval * 1000L)
                // Paging changes this effect's keys. Keep the animation in the viewer
                // scope so the timer does not cancel its own scroll as it starts.
                if (page < attachments.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) }
                else autoplay = false
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                // Older Compose Dialogs can still fit the floating window to status-bar
                // insets on Android 15+, while measuring content to the entire display.
                // Clear window-level fitting so the bottom toolbar cannot fall offscreen.
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    it.attributes = it.attributes.apply {
                        setFitInsetsTypes(0)
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    }
                }
            }
            onDispose { }
        }
        DisposableEffect(view, controls) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val bars = window?.let { WindowCompat.getInsetsController(it, view) }
            bars?.isAppearanceLightStatusBars = false
            bars?.isAppearanceLightNavigationBars = false
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controls) bars?.show(WindowInsetsCompat.Type.systemBars()) else bars?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(Modifier.fillMaxSize().background(HeColors.Void)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
                key = { attachments[it].text("attachment_key", it.toString()) }
            ) { position ->
                val item = attachments[position]
                if (item.text("media_type") == "video") {
                    // Only the visible page owns a player; offscreen videos never play audio.
                    if (position == page) {
                        NativeVideo(api, "/external/pawchive/media/" + encoded(item.text("stream_ref")),
                            Modifier.fillMaxSize().safeDrawingPadding(), autoplay) {
                            if (autoplay && page < attachments.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) }
                            else autoplay = false
                        }
                    }
                } else {
                    AttachmentImage(api, item, position == page, resetZoom,
                        onTap = { controls = !controls },
                        onZoom = { if (position == page) zoomed = it },
                        onReady = { if (position == page) imageReady = it })
                }
            }

            if (controls) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().background(HeColors.ScrimTop)
                        .statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerIcon("关闭", Icons.Default.Close, onClose)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(attachment.text("filename", "图片阅读"), color = HeColors.OpWhite,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (zoomed) "已放大 · 拖动查看细节" else "左右滑动翻页 · 双击放大",
                            color = HeColors.OpWhiteSoft, fontSize = 12.sp)
                    }
                    Surface(color = HeColors.Panel, shape = RoundedCornerShape(24.dp)) {
                        Text("${page + 1} / ${attachments.size}", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontFamily = GeistMono, color = HeColors.Yellow, fontSize = 13.sp)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(HeColors.ScrimBottom)
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        ViewerIcon("上一张", Icons.Default.ChevronLeft, { navigate(page - 1) }, page > 0)
                        FilledTonalButton(onClick = { autoplay = !autoplay }, shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = HeColors.PanelLight, contentColor = HeColors.OpWhite)) {
                            Icon(if (autoplay) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (autoplay) "暂停连播" else "自动连播")
                        }
                        ViewerIcon("下一张", Icons.Default.ChevronRight, { navigate(page + 1) }, page < attachments.lastIndex)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            TextButton(onClick = { intervalMenu = true }) {
                                Text("间隔 ${interval} 秒", color = HeColors.OpWhiteSoft)
                                Icon(Icons.Default.ExpandMore, null, tint = HeColors.OpWhiteSoft, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                                listOf(2, 3, 5, 10, 15, 30, 60).forEach { seconds ->
                                    DropdownMenuItem(text = { Text("$seconds 秒") }, onClick = { interval = seconds; intervalMenu = false })
                                }
                            }
                        }
                        if (zoomed) TextButton(onClick = { resetZoom++ }) { Text("重置缩放", color = HeColors.Yellow) }
                        else Text("轻点图片隐藏工具栏", color = HeColors.OpWhiteSoft, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun attachmentUrl(api: NativeApi, attachment: JSONObject) =
    api.origin + "/external/pawchive/media/" + encoded(attachment.text("stream_ref"))

@Composable
private fun ViewerIcon(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                       onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, label, tint = if (enabled) HeColors.OpWhite else HeColors.OpWhiteFaint)
    }
}

@Composable
private fun AttachmentImage(api: NativeApi, attachment: JSONObject, active: Boolean, reset: Int,
                            onTap: () -> Unit, onZoom: (Boolean) -> Unit, onReady: (Boolean) -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var source by remember { mutableStateOf(Size.Zero) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val tap by rememberUpdatedState(onTap)

    fun bounded(value: Offset, zoom: Float): Offset {
        val fit = if (source.width > 0 && source.height > 0) min(viewport.width / source.width, viewport.height / source.height) else 1f
        val width = if (source.width > 0) source.width * fit else viewport.width
        val height = if (source.height > 0) source.height * fit else viewport.height
        val maxX = ((width * zoom - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((height * zoom - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
    LaunchedEffect(active, reset) { scale = 1f; offset = Offset.Zero }
    LaunchedEffect(active, scale, loaded) { if (active) { onZoom(scale > 1.01f); onReady(loaded) } }
    val model = remember(api.origin, api.token, attachment.text("stream_ref"), retry) {
        ImageRequest.Builder(context).data(attachmentUrl(api, attachment))
            .addHeader("Authorization", "Bearer ${api.token}").build()
    }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
        .pointerInput(active) {
            detectTapGestures(onTap = { tap() }, onDoubleTap = { point ->
                if (loaded) {
                    val next = if (scale > 1.01f) 1f else 2.5f
                    offset = if (next == 1f) Offset.Zero else bounded((Offset(viewport.width / 2, viewport.height / 2) - point) * (next - 1), next)
                    scale = next
                }
            })
        }
        .pointerInput(active) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var transformed = false
                var motion = Offset.Zero
                do {
                    val event = awaitPointerEvent()
                    val multiple = event.changes.count { it.pressed } > 1
                    val pan = event.calculatePan()
                    motion += pan
                    // At fit size leave one-finger drags to HorizontalPager.
                    // Once a pinch owns the gesture, never turn its final finger into a page swipe.
                    if (loaded && (multiple || transformed || (scale > 1.01f && motion.getDistance() > viewConfiguration.touchSlop))) {
                        transformed = true
                        val next = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                        val center = event.calculateCentroid(useCurrent = false)
                        val relative = if (center == Offset.Unspecified) Offset.Zero else center - Offset(viewport.width / 2, viewport.height / 2)
                        offset = bounded((offset - relative) * (next / scale) + relative + pan, next)
                        scale = next
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }, contentAlignment = Alignment.Center) {
        // Coil treats equal URL requests as the same request. Recreate the painter for
        // retries so clearing the error actually starts another network request.
        key(retry) {
        AsyncImage(model = model, contentDescription = attachment.text("filename"), contentScale = ContentScale.Fit,
            onLoading = { loaded = false; failed = false },
            onSuccess = { result ->
                source = Size(result.result.drawable.intrinsicWidth.toFloat(), result.result.drawable.intrinsicHeight.toFloat())
                loaded = true; failed = false
            },
            onError = { loaded = false; failed = true },
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y))
        }
        if (!loaded && !failed) CircularProgressIndicator(color = HeColors.Yellow,
            modifier = Modifier.size(32.dp).semantics { contentDescription = "正在加载图片" }, strokeWidth = 2.dp)
        if (failed) Surface(color = HeColors.Panel, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.BrokenImage, null, tint = HeColors.OpWhiteSoft, modifier = Modifier.size(32.dp))
                Text("图片加载失败", color = HeColors.OpWhite, fontWeight = FontWeight.SemiBold)
                Text("请检查网络后重试，或滑动查看下一张", color = HeColors.OpWhiteSoft, fontSize = 12.sp)
                Button(onClick = { failed = false; retry++ }) { Text("重试加载") }
            }
        }
    }
}
