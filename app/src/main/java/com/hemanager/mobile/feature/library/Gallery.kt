package com.hemanager.mobile.feature.library

// Library 图片网格相关：RecyclerView 互操作 + Compose tile + 占位骨架。
// 包括 Modifier.imageGalleryPinchZoom（双指缩放手势）、NativeImageGalleryRecyclerV2、
// NativeImageGalleryAdapterV2（RecyclerView.Adapter）、ImageGalleryTileV2、RemoteGalleryThumbV2、
// ImageGallerySkeletonV2，外加两个工具扩展 dpView / Color.toArgbInt。
//
// 这是 library 模块最复杂的视觉部分：用原生 RecyclerView 实现大网格性能，
// 在 Compose 里通过 AndroidView 嵌入。

import com.hemanager.mobile.MediaItem
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.Disposable
import coil.size.Precision
import coil.size.Scale
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.ui.platform.LocalContext
import com.hemanager.mobile.data.image.coverCacheKey
import com.hemanager.mobile.data.image.imageGalleryDecodeBucketPx
import com.hemanager.mobile.data.image.imageGalleryPlaceholderColor
import com.hemanager.mobile.ui.components.LocalCoverImageLoader
import com.hemanager.mobile.ui.theme.HeColorScheme
import kotlin.math.roundToInt

internal fun Modifier.imageGalleryPinchZoom(
    enabled: Boolean,
    onPinchStart: (Offset) -> Unit,
    onPinch: (Float, Offset) -> Unit,
    onPinchEnd: () -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            var pinching = false
            var startDistance = 0f
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val first = pressed[0].position
                    val second = pressed[1].position
                    val distance = (first - second).getDistance()
                    val focalPoint = (first + second) / 2f
                    if (distance > 1f) {
                        if (!pinching) {
                            pinching = true
                            startDistance = distance
                            onPinchStart(focalPoint)
                        } else {
                            val scale = (distance / startDistance).coerceIn(0.45f, 2.40f)
                            onPinch(scale, focalPoint)
                        }
                    }
                    pressed.forEach { it.consume() }
                } else {
                    if (pinching) {
                        onPinchEnd()
                        break
                    }
                    if (pressed.isEmpty()) break
                }
            }
        }
    }
}

@Composable
internal fun NativeImageGalleryRecyclerV2(
    items: List<MediaItem>,
    serverUrl: String,
    token: String,
    loading: Boolean,
    error: String?,
    search: String,
    mediaFilters: List<FilterOption>,
    statusFilters: List<FilterOption>,
    selectedMediaType: String,
    selectedStatus: String,
    viewMode: LibraryViewMode,
    columns: Int,
    tileDp: Float,
    interactionsEnabled: Boolean,
    playlist: List<MediaItem>,
    onSearchChange: (String) -> Unit,
    onMediaSelected: (String) -> Unit,
    onStatusSelected: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onViewModeSelected: (LibraryViewMode) -> Unit,
    onMenu: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRecycler: (RecyclerView) -> Unit,
    onBackTopVisible: (Boolean) -> Unit,
    // inlineHeaders=false: 适配器跳过 4 个 header 行，让调用方在 Column 外部
    // 自己渲染。配合 graphicsLayer scale，pinch 视觉缩放只作用在瓦片上而不放大顶栏。
    inlineHeaders: Boolean = true,
    // 原生 pinch：调用 MainActivity.setImageGalleryPinchTouchListener 注册/反注册。
    // false 时不注册（保持旧 Compose 修饰符路径或纯无 pinch）；true 时 RecyclerView
    // 双指事件直接触发 callbacks，不经 Compose 事件管线，响应更快。
    pinchZoomEnabled: Boolean = false,
    onPinchStart: (Offset) -> Unit = {},
    onPinch: (Float, Offset) -> Unit = { _, _ -> },
    onPinchEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coverImageLoader = com.hemanager.mobile.ui.components.LocalCoverImageLoader.current
    val density = LocalDensity.current
    val tilePx = remember(tileDp, density) {
        with(density) { tileDp.dp.toPx().roundToInt() }
    }
    val mainActivity = (LocalContext.current as? com.hemanager.mobile.MainActivity)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = true
                clipChildren = true
                setPadding(0, 0, 0, dpView(this, 28))
                itemAnimator = null
                setHasFixedSize(true)
                setItemViewCacheSize(columns * 4)
                // 注册原生 pinch 监听（如果启用且能拿到 MainActivity 引用）。
                // MainActivity 内部维护 WeakHashMap<RecyclerView, Listener>，重复注册会先反注册旧的。
                mainActivity?.setImageGalleryPinchTouchListener(
                    recyclerView = this,
                    enabled = pinchZoomEnabled,
                    onPinchStart = onPinchStart,
                    onPinch = onPinch,
                    onPinchEnd = onPinchEnd
                )
                val galleryAdapter = NativeImageGalleryAdapterV2(
                    context = context,
                    coverImageLoader = coverImageLoader,
                    serverUrl = serverUrl,
                    token = token,
                    loading = loading,
                    error = error,
                    search = search,
                    mediaFilters = mediaFilters,
                    statusFilters = statusFilters,
                    selectedMediaType = selectedMediaType,
                    selectedStatus = selectedStatus,
                    viewMode = viewMode,
                    columns = columns,
                    tilePx = tilePx,
                    interactionsEnabled = interactionsEnabled,
                    playlist = playlist,
                    onSearchChange = onSearchChange,
                    onMediaSelected = onMediaSelected,
                    onStatusSelected = onStatusSelected,
                    onOpenFilters = onOpenFilters,
                    onViewModeSelected = onViewModeSelected,
                    onMenu = onMenu,
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                    inlineHeaders = inlineHeaders
                )
                adapter = galleryAdapter
                layoutManager = GridLayoutManager(context, columns).apply {
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return if (galleryAdapter.isFullSpan(position)) spanCount else 1
                        }
                    }
                }
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        val galleryAdapter = recyclerView.adapter as? NativeImageGalleryAdapterV2 ?: return
                        val idle = newState == RecyclerView.SCROLL_STATE_IDLE
                        galleryAdapter.networkAllowed = idle
                        if (idle) {
                            val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                            val first = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
                            val last = lm.findLastVisibleItemPosition().coerceAtLeast(first)
                            if (last >= first) {
                                galleryAdapter.notifyItemRangeChanged(first, last - first + 1, "network")
                            }
                        }
                    }

                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                        val first = lm.findFirstVisibleItemPosition()
                        val visible = first > 1 || recyclerView.computeVerticalScrollOffset() > 360
                        onBackTopVisible(visible)
                    }
                })
                onRecycler(this)
            }
        },
        update = { recyclerView ->
            val lm = recyclerView.layoutManager as? GridLayoutManager
            if (lm?.spanCount != columns) {
                lm?.spanCount = columns
                recyclerView.setItemViewCacheSize(columns * 4)
            }
            // Re-register pinch listener so 当 enabled 切换（如离开图片库进入视频库）
            // 或回调更新（state 闭包刷新）时，监听器永远指向最新闭包。MainActivity 内
            // 部对同一个 RecyclerView 会先反注册旧的再加新的，重复调用安全。
            mainActivity?.setImageGalleryPinchTouchListener(
                recyclerView = recyclerView,
                enabled = pinchZoomEnabled,
                onPinchStart = onPinchStart,
                onPinch = onPinch,
                onPinchEnd = onPinchEnd
            )
            val galleryAdapter = recyclerView.adapter as NativeImageGalleryAdapterV2
            galleryAdapter.updateConfig(
                serverUrl = serverUrl,
                token = token,
                loading = loading,
                error = error,
                search = search,
                mediaFilters = mediaFilters,
                statusFilters = statusFilters,
                selectedMediaType = selectedMediaType,
                selectedStatus = selectedStatus,
                viewMode = viewMode,
                columns = columns,
                tilePx = tilePx,
                interactionsEnabled = interactionsEnabled,
                playlist = playlist,
                onSearchChange = onSearchChange,
                onMediaSelected = onMediaSelected,
                onStatusSelected = onStatusSelected,
                onOpenFilters = onOpenFilters,
                onViewModeSelected = onViewModeSelected,
                onMenu = onMenu,
                onRefresh = onRefresh,
                onRetry = onRetry
            )
            galleryAdapter.submitItems(items)
            onRecycler(recyclerView)
        }
    )
}

private class NativeImageGalleryAdapterV2(
    private val context: android.content.Context,
    private val coverImageLoader: coil.ImageLoader,
    private var serverUrl: String,
    private var token: String,
    private var loading: Boolean,
    private var error: String?,
    private var search: String,
    private var mediaFilters: List<FilterOption>,
    private var statusFilters: List<FilterOption>,
    private var selectedMediaType: String,
    private var selectedStatus: String,
    private var viewMode: LibraryViewMode,
    private var columns: Int,
    private var tilePx: Int,
    private var interactionsEnabled: Boolean,
    private var playlist: List<MediaItem>,
    private var onSearchChange: (String) -> Unit,
    private var onMediaSelected: (String) -> Unit,
    private var onStatusSelected: (String) -> Unit,
    private var onOpenFilters: () -> Unit,
    private var onViewModeSelected: (LibraryViewMode) -> Unit,
    private var onMenu: () -> Unit,
    private var onRefresh: () -> Unit,
    private var onRetry: () -> Unit,
    // inlineHeaders=false: 跳过 4 个固定 header 行（top bar / 搜索过滤 / error / loading）。
    // 用于 pinch-zoom 时只想缩放瓦片但保持顶部 UI 不变的场景——调用方在 RecyclerView 外
    // 的 Column 里渲染 header。这样 graphicsLayer 的 scale 不会放大搜索栏字体。
    private val inlineHeaders: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val viewTypeHeader = 1
    private val viewTypeSearch = 2
    private val viewTypeError = 3
    private val viewTypeLoading = 4
    private val viewTypeEmpty = 5
    private val viewTypeTile = 6
    private val fixedHeaderCount = if (inlineHeaders) 4 else 0
    private var items: List<MediaItem> = emptyList()
    private var itemIds: IntArray = IntArray(0)
    var networkAllowed: Boolean = true

    init {
        setHasStableIds(true)
    }

    inner class ComposeHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)

    inner class TileHolder(root: FrameLayout) : RecyclerView.ViewHolder(root) {
        val imageView: ImageView = ImageView(root.context)
        val favoriteBadge: TextView = TextView(root.context)
        val missingBadge: TextView = TextView(root.context)
        var disposable: Disposable? = null
        /** 当前已经加载成功的 Coil 缓存 key，用来跳过重复的 dispose + reload。 */
        var boundKey: String? = null

        init {
            // HE OP — Panel 底色（#16171D）
            root.setBackgroundColor(android.graphics.Color.rgb(22, 23, 29))
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            root.addView(
                imageView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            // HE OP 收藏角标：★ + 高饱和黄 (#F5D800)
            favoriteBadge.text = "★"
            favoriteBadge.setTextColor(android.graphics.Color.rgb(245, 216, 0))
            favoriteBadge.textSize = 11f
            favoriteBadge.gravity = Gravity.CENTER
            favoriteBadge.setBackgroundColor(android.graphics.Color.argb(160, 0, 0, 0))
            val favoriteParams = FrameLayout.LayoutParams(dpView(root, 18), dpView(root, 18), Gravity.TOP or Gravity.END)
            favoriteParams.setMargins(0, dpView(root, 4), dpView(root, 4), 0)
            root.addView(favoriteBadge, favoriteParams)

            // HE OP 缺失角标：HE 系红 (#FF5C5C)，更克制的字号
            missingBadge.text = "MISSING"
            missingBadge.setTextColor(android.graphics.Color.WHITE)
            missingBadge.textSize = 9f
            missingBadge.gravity = Gravity.CENTER
            missingBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            missingBadge.setBackgroundColor(android.graphics.Color.argb(204, 255, 92, 92))
            missingBadge.letterSpacing = 0.15f
            root.addView(
                missingBadge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dpView(root, 14),
                    Gravity.BOTTOM
                )
            )
        }
    }

    private fun hasEmptyRow(): Boolean = !loading && error == null && items.isEmpty()

    private fun tileStartPosition(): Int = fixedHeaderCount + if (hasEmptyRow()) 1 else 0

    fun isFullSpan(position: Int): Boolean = getItemViewType(position) != viewTypeTile

    override fun getItemId(position: Int): Long {
        return when (getItemViewType(position)) {
            viewTypeHeader -> -1L
            viewTypeSearch -> -2L
            viewTypeError -> -3L
            viewTypeLoading -> -4L
            viewTypeEmpty -> -5L
            else -> items.getOrNull(position - tileStartPosition())?.id?.toLong() ?: RecyclerView.NO_ID
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            inlineHeaders && position == 0 -> viewTypeHeader
            inlineHeaders && position == 1 -> viewTypeSearch
            inlineHeaders && position == 2 -> viewTypeError
            inlineHeaders && position == 3 -> viewTypeLoading
            hasEmptyRow() && position == fixedHeaderCount -> viewTypeEmpty
            else -> viewTypeTile
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == viewTypeTile) {
            val root = FrameLayout(parent.context)
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tilePx.coerceAtLeast(1)
            )
            TileHolder(root)
        } else {
            val composeView = ComposeView(parent.context)
            composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            composeView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            ComposeHolder(composeView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bind(holder, position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        bind(holder, position)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is TileHolder) {
            holder.disposable?.dispose()
            holder.disposable = null
            holder.boundKey = null
            holder.imageView.setImageDrawable(null)
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = tileStartPosition() + items.size

    fun updateConfig(
        serverUrl: String,
        token: String,
        loading: Boolean,
        error: String?,
        search: String,
        mediaFilters: List<FilterOption>,
        statusFilters: List<FilterOption>,
        selectedMediaType: String,
        selectedStatus: String,
        viewMode: LibraryViewMode,
        columns: Int,
        tilePx: Int,
        interactionsEnabled: Boolean,
        playlist: List<MediaItem>,
        onSearchChange: (String) -> Unit,
        onMediaSelected: (String) -> Unit,
        onStatusSelected: (String) -> Unit,
        onOpenFilters: () -> Unit,
        onViewModeSelected: (LibraryViewMode) -> Unit,
        onMenu: () -> Unit,
        onRefresh: () -> Unit,
        onRetry: () -> Unit
    ) {
        // 之前这里无条件 notifyDataSetChanged()，而 update{} 会在**任何一次重组**时
        // 被调用（搜索框输入、回到顶部按钮显隐、pinch 状态变化……），于是整屏瓦片
        // 每次都全量重绑、Coil 请求全部 dispose 再重发。这里改成只在真正影响渲染的
        // 字段变化时才 notify；回调 lambda 每次重组都是新对象，但它们只在点击时被
        // 读取，换掉字段就够了，不需要重绑。
        val hadEmptyRow = hasEmptyRow()
        val structureChanged =
            this.tilePx != tilePx ||
                this.columns != columns ||
                this.interactionsEnabled != interactionsEnabled ||
                this.serverUrl != serverUrl ||
                this.token != token ||
                (
                    inlineHeaders && (
                        this.loading != loading ||
                            this.error != error ||
                            this.search != search ||
                            this.selectedMediaType != selectedMediaType ||
                            this.selectedStatus != selectedStatus ||
                            this.viewMode != viewMode ||
                            this.mediaFilters != mediaFilters ||
                            this.statusFilters != statusFilters
                        )
                    )
        this.serverUrl = serverUrl
        this.token = token
        this.loading = loading
        this.error = error
        this.search = search
        this.mediaFilters = mediaFilters
        this.statusFilters = statusFilters
        this.selectedMediaType = selectedMediaType
        this.selectedStatus = selectedStatus
        this.viewMode = viewMode
        this.columns = columns
        this.tilePx = tilePx
        this.interactionsEnabled = interactionsEnabled
        this.playlist = playlist
        this.onSearchChange = onSearchChange
        this.onMediaSelected = onMediaSelected
        this.onStatusSelected = onStatusSelected
        this.onOpenFilters = onOpenFilters
        this.onViewModeSelected = onViewModeSelected
        this.onMenu = onMenu
        this.onRefresh = onRefresh
        this.onRetry = onRetry
        // loading / error 在 inlineHeaders=false（图廊真正走的分支）下不占行，
        // 只会通过"空状态行"影响行数；每次 onResume 刷新都 loading=true→false，
        // 无条件 notify 会让整屏白重绑两次。
        if (structureChanged || hadEmptyRow != hasEmptyRow()) notifyDataSetChanged()
    }

    fun submitItems(nextItems: List<MediaItem>) {
        // 签名带上 favorite / missing：只比 id 的话，别处（长按菜单、播放器里）
        // 改了收藏状态后网格上的角标不会更新。
        val nextIds = IntArray(nextItems.size) { index ->
            val item = nextItems[index]
            var signature = item.id * 4
            if (item.favorite) signature += 1
            if (item.missing) signature += 2
            signature
        }
        items = nextItems
        if (nextIds.contentEquals(itemIds)) return
        itemIds = nextIds
        notifyDataSetChanged()
    }

    private fun bind(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            viewTypeHeader -> bindHeader(holder as ComposeHolder)
            viewTypeSearch -> bindSearch(holder as ComposeHolder)
            viewTypeError -> bindError(holder as ComposeHolder)
            viewTypeLoading -> bindLoading(holder as ComposeHolder)
            viewTypeEmpty -> bindEmpty(holder as ComposeHolder)
            viewTypeTile -> bindTile(holder as TileHolder, position - tileStartPosition())
        }
    }

    private fun bindHeader(holder: ComposeHolder) {
        holder.composeView.setContent {
            MaterialTheme(colorScheme = com.hemanager.mobile.ui.theme.HeColorScheme) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    LibraryHeaderV2(
                        loading = loading,
                        viewMode = viewMode,
                        galleryMode = true,
                        onViewModeSelected = onViewModeSelected,
                        onMenu = onMenu,
                        onRefresh = onRefresh
                    )
                }
            }
        }
    }

    private fun bindSearch(holder: ComposeHolder) {
        holder.composeView.setContent {
            MaterialTheme(colorScheme = com.hemanager.mobile.ui.theme.HeColorScheme) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SearchAndFilterPanelV2(
                        search = search,
                        onSearchChange = onSearchChange,
                        filters = mediaFilters,
                        selectedValue = selectedMediaType,
                        onSelected = onMediaSelected,
                        statusFilters = statusFilters,
                        selectedStatusValue = selectedStatus,
                        onStatusSelected = onStatusSelected,
                        onOpenFilters = onOpenFilters
                    )
                }
            }
        }
    }

    private fun bindError(holder: ComposeHolder) {
        holder.composeView.setContent {
            MaterialTheme(colorScheme = com.hemanager.mobile.ui.theme.HeColorScheme) {
                if (error != null) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        ErrorPanelV2(error = error ?: "", loading = loading, onRetry = onRetry)
                    }
                }
            }
        }
    }

    private fun bindLoading(holder: ComposeHolder) {
        holder.composeView.setContent {
            MaterialTheme(colorScheme = com.hemanager.mobile.ui.theme.HeColorScheme) {
                if (loading) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        LoadingLineV2()
                    }
                }
            }
        }
    }

    private fun bindEmpty(holder: ComposeHolder) {
        holder.composeView.setContent {
            MaterialTheme(colorScheme = com.hemanager.mobile.ui.theme.HeColorScheme) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    EmptyStateV2()
                }
            }
        }
    }

    private fun bindTile(holder: TileHolder, itemPosition: Int) {
        val item = items.getOrNull(itemPosition) ?: return
        val root = holder.itemView as FrameLayout
        val lp = root.layoutParams as? RecyclerView.LayoutParams
        if (lp != null && lp.height != tilePx) {
            lp.height = tilePx.coerceAtLeast(1)
            root.layoutParams = lp
        }
        val placeholder = imageGalleryPlaceholderColor(coverUrl(serverUrl, token, item) ?: item.title)
        root.setBackgroundColor(placeholder.toArgbInt())
        holder.favoriteBadge.visibility = if (item.favorite) View.VISIBLE else View.GONE
        holder.missingBadge.visibility = if (item.missing) View.VISIBLE else View.GONE
        holder.itemView.isEnabled = interactionsEnabled
        holder.itemView.setOnClickListener {
            if (interactionsEnabled) openItem(context, item, serverUrl, token, false, playlist)
        }

        val url = coverUrl(serverUrl, token, item)
        if (url.isNullOrBlank()) {
            holder.disposable?.dispose()
            holder.disposable = null
            holder.boundKey = null
            holder.imageView.setImageDrawable(null)
            return
        }
        val decodePx = imageGalleryDecodeBucketPx(tilePx, columns)
        val key = coverCacheKey(url, decodePx, decodePx)
        // 同一张图、同样尺寸、而且已经画上去了 —— 直接复用。滚动停下来时
        // onScrollStateChanged 会对可见区间发一次 notifyItemRangeChanged，
        // 没有这个判断的话每次停手所有可见瓦片都要重新发一轮 Coil 请求。
        if (holder.boundKey == key && holder.imageView.drawable != null) return

        holder.disposable?.dispose()
        holder.imageView.setImageDrawable(null)
        holder.boundKey = key
        holder.disposable = holder.imageView.load(url, imageLoader = coverImageLoader) {
            size(decodePx, decodePx)
            scale(Scale.FILL)
            precision(Precision.INEXACT)
            memoryCacheKey(key)
            diskCacheKey(key)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
            networkCachePolicy(if (networkAllowed) CachePolicy.ENABLED else CachePolicy.DISABLED)
            crossfade(false)
        }
    }
}

internal fun dpView(view: View, value: Int): Int {
    return (value * view.resources.displayMetrics.density).roundToInt()
}

internal fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        (red * 255).roundToInt().coerceIn(0, 255),
        (green * 255).roundToInt().coerceIn(0, 255),
        (blue * 255).roundToInt().coerceIn(0, 255)
    )
}

@Composable
internal fun ImageGalleryTileV2(
    coverUrl: String?,
    label: String,
    favorite: Boolean,
    missing: Boolean,
    tileDp: Float,
    columns: Int,
    interactionsEnabled: Boolean,
    networkAllowedProvider: () -> Boolean,
    requestRestartKey: Int,
    onOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .clickable(
                enabled = interactionsEnabled,
                onClick = onOpen
            )
    ) {
        RemoteGalleryThumbV2(
            url = coverUrl,
            label = label,
            tileDp = tileDp,
            columns = columns,
            networkAllowedProvider = networkAllowedProvider,
            requestRestartKey = requestRestartKey,
            modifier = Modifier.fillMaxSize()
        )
        if (favorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(if (tileDp < 70f) 16.dp else 20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "favorite",
                    tint = Color(0xFFF6C46B),
                    modifier = Modifier.size(if (tileDp < 70f) 10.dp else 13.dp)
                )
            }
        }
        if (missing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCCFF4E6A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "缺失",
                    modifier = Modifier.padding(vertical = if (tileDp < 70f) 1.dp else 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun RemoteGalleryThumbV2(
    url: String?,
    label: String,
    tileDp: Float,
    columns: Int,
    networkAllowedProvider: () -> Boolean,
    requestRestartKey: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val targetPx = remember(tileDp, density) {
        with(density) { tileDp.dp.toPx().roundToInt() }
    }
    val decodePx = remember(targetPx, columns) { imageGalleryDecodeBucketPx(targetPx, columns) }
    val placeholderColor = remember(url, label) {
        imageGalleryPlaceholderColor(url ?: label)
    }

    Box(
        modifier = modifier.background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        CoilCoverImage(
            url = url,
            label = label,
            decodeWidthPx = decodePx,
            decodeHeightPx = decodePx,
            crossfadeMillis = 0,
            networkAllowedProvider = networkAllowedProvider,
            requestRestartKey = requestRestartKey,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun ImageGallerySkeletonV2(index: Int) {
    val alpha = remember(index) { 0.16f + (index % 5) * 0.025f }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = alpha))
    )
}
