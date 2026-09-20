package com.hemanager.mobile

import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.runtime.CompositionLocalProvider
import com.hemanager.mobile.data.HePrefs
import com.hemanager.mobile.feature.creators.CreatorsScreen
import com.hemanager.mobile.feature.library.LibraryScreenV2
import com.hemanager.mobile.feature.login.LoginScreen
import com.hemanager.mobile.feature.settings.SettingsScreen
import com.hemanager.mobile.ui.components.LocalCoverImageLoader
import com.hemanager.mobile.ui.host.HostUiController
import com.hemanager.mobile.ui.host.LocalHostUiController
import com.hemanager.mobile.ui.theme.HeColorScheme
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), HostUiController {
    private val coverImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.24)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cover_cache"))
                    .maxSizeBytes(HePrefs(this).coverCacheSizeMb.toLong() * 1024L * 1024L)
                    .build()
            }
            .build()
    }

    // 颜色统一在 ui.theme.HeColors / HeColorScheme 中维护
    private val scheme = HeColorScheme

    /** 是否当前在「创作者」全屏页面上。CreatorsScreen 通过 HostUiController 控制。 */
    @Volatile
    private var creatorsScreenActive: Boolean = false

    /** 左边缘滑动手势触发时调用的回调；由 LibraryScreen 注册（指向 drawerState.open()）。 */
    @Volatile
    private var edgeDrawerOpenRequester: (() -> Unit)? = null

    /** 是否启用左边缘抽屉手势。LibraryScreen 挂载时设 true，图廊全屏时设 false。 */
    @Volatile
    private var edgeDrawerGestureEnabled: Boolean = false

    /** 图廊原生 RecyclerView 上 pinch 缩放正在进行中。 */
    @Volatile
    private var imageGalleryNativePinching: Boolean = false

    /** 图廊每个 RecyclerView 注册的 pinch OnItemTouchListener，WeakHashMap 防止
     *  RecyclerView 被销毁后我们还持有强引用造成内存泄漏。 */
    private val imageGalleryPinchTouchListeners =
        WeakHashMap<RecyclerView, RecyclerView.OnItemTouchListener>()

    // -- 边缘手势触摸跟踪（仅 dispatchTouchEvent 内部使用，保持 private）
    private var edgeDrawerTracking: Boolean = false
    private var edgeDrawerOpened: Boolean = false
    private var edgeDrawerStartX: Float = 0f
    private var edgeDrawerStartY: Float = 0f


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(8, 9, 12)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 12)

        // 刘海/挖孔屏：永远不让内容延伸进 cutout 区域。各 OEM 默认值不统一（状态栏隐藏后内容
        // 可能滑到挖孔下面），显式钉死成 NEVER，挖孔区会保留一条背景色窄边而不是覆盖 UI。
        // Android 15 (API 35+) 强制 edge-to-edge，使用 NEVER 会与 WindowInsetsAnimation 冲突
        // 导致软键盘 insets 计算异常，仅在 Android 15 以下系统设置。
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            android.os.Build.VERSION.SDK_INT < 35
        ) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalCoverImageLoader provides coverImageLoader,
                LocalHostUiController provides this,
            ) {
                MaterialTheme(colorScheme = scheme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        HeManagerApp()
                    }
                }
            }
        }

        // 状态栏只在库主页等场景显示；创作者页面进入时由 CreatorsScreen 自己切隐藏。
        // 这里按当前 creatorsScreenActive（默认 false）应用一次初始状态。
        setStatusBarHidden(creatorsScreenActive)
        // 在左边沿注册系统手势排除区，让 Android 10+ 的返回手势不会吃掉左边缘滑动。
        applyDrawerEdgeExclusion()
    }

    // ------------------------------------------------------------------------
    // 全局触摸分发：左边缘滑动开抽屉
    // ------------------------------------------------------------------------
    // 在 ComponentActivity 顶层拦截：从左边缘 56dp 内按下、向右滑超过 32dp 时，
    // 触发 edgeDrawerOpenRequester 回调（由 LibraryScreen 注入）。
    //
    // 谨慎让出：
    //   - 多指（pinch）：直接放过，让 RecyclerView 自己处理缩放
    //   - 图廊正在原生 pinch：让出
    //   - 垂直分量明显大于水平（用户在滚列表）：让出
    //   - 反向左滑：让出
    //
    // 这块逻辑必须在 ComponentActivity 层而不是 Compose 层，因为目标是「跨 composable 全局
    // 都能从左边沿开抽屉」，不限于 LibraryScreen 内部的可点击区域。
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!edgeDrawerGestureEnabled || imageGalleryNativePinching || event.pointerCount > 1) {
            edgeDrawerTracking = false
            edgeDrawerOpened = false
            return super.dispatchTouchEvent(event)
        }

        val density = resources.displayMetrics.density
        val edgeWidthPx = 56f * density
        val openDistancePx = 32f * density
        val touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        val action = event.actionMasked
        if (edgeDrawerOpened) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_DOWN) {
                edgeDrawerOpened = false
            }
            if (action != MotionEvent.ACTION_DOWN) {
                return true
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                edgeDrawerTracking = event.x <= edgeWidthPx
                edgeDrawerOpened = false
                edgeDrawerStartX = event.x
                edgeDrawerStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (edgeDrawerTracking) {
                    val dx = event.x - edgeDrawerStartX
                    val dy = event.y - edgeDrawerStartY
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    // 仅在 Y 明显主导时才放弃跟踪，保护垂直列表滚动。
                    if (absDy > absDx * 1.5f && absDy > touchSlopPx * 2) {
                        edgeDrawerTracking = false
                    } else if (dx < -touchSlopPx) {
                        edgeDrawerTracking = false
                    } else if (dx > openDistancePx && absDx > absDy * 1.1f) {
                        edgeDrawerTracking = false
                        edgeDrawerOpened = true
                        edgeDrawerOpenRequester?.invoke()

                        // Send ACTION_CANCEL to child views to cancel active gestures cleanly
                        val cancelEvent = MotionEvent.obtain(
                            event.downTime,
                            event.eventTime,
                            MotionEvent.ACTION_CANCEL,
                            event.x,
                            event.y,
                            event.metaState
                        )
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()

                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                edgeDrawerTracking = false
                edgeDrawerOpened = false
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun setCreatorsScreenActive(active: Boolean) {
        creatorsScreenActive = active
        setStatusBarHidden(active)
    }

    override fun setEdgeDrawerOpenRequester(requester: (() -> Unit)?) {
        edgeDrawerOpenRequester = requester
    }

    override fun setEdgeDrawerGestureEnabled(enabled: Boolean) {
        edgeDrawerGestureEnabled = enabled
    }

    /** 显示或隐藏顶部状态栏（仅本 Activity 的 window）。隐藏时支持顶部下滑短暂显示。 */
    private fun setStatusBarHidden(hidden: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 系统在 focus 变化时（旋转、前后台切换）有时会清掉 systemGestureExclusionRects，
        // 也可能重置状态栏。重新应用一次以保持一致。
        if (hasFocus) {
            setStatusBarHidden(creatorsScreenActive)
            applyDrawerEdgeExclusion()
        }
    }

    private fun applyDrawerEdgeExclusion() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val decor = window.decorView
        decor.post {
            val edge = (56f * resources.displayMetrics.density).toInt()
            val height = decor.height
            if (edge > 0 && height > 0) {
                decor.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, edge, height))
            }
        }
    }

    // ------------------------------------------------------------------------
    // 图廊 pinch 缩放：原生 RecyclerView OnItemTouchListener
    // ------------------------------------------------------------------------
    // 比 Compose 的 awaitEachGesture 修饰符更响应——不走 Compose 的事件管线，
    // 直接在 RecyclerView 层拦截，配合 requestDisallowInterceptTouchEvent 阻止
    // 父级（包括我们自己的 dispatchTouchEvent 边缘手势）抢走双指。
    //
    // 该方法被 Gallery 模块通过 (context as MainActivity).setImageGalleryPinchTouchListener(...)
    // 调用。enabled=false 时移除已注册的监听并复位 imageGalleryNativePinching 标志。
    //
    // 回调语义：
    //   onPinchStart(focal): 第二指落下，传入两指中点（RecyclerView 局部坐标）
    //   onPinch(scale, focal): 持续两指移动；scale = currentDistance / startDistance，已 clamp 到 [0.45, 2.40]
    //   onPinchEnd(): 任一指抬起或取消
    override fun setImageGalleryPinchTouchListener(
        recyclerView: RecyclerView,
        enabled: Boolean,
        onPinchStart: (Offset) -> Unit,
        onPinch: (Float, Offset) -> Unit,
        onPinchEnd: () -> Unit
    ) {
        imageGalleryPinchTouchListeners.remove(recyclerView)?.let {
            recyclerView.removeOnItemTouchListener(it)
        }
        if (!enabled) {
            imageGalleryNativePinching = false
            return
        }

        var pinching = false
        var startDistance = 0f

        fun distance(event: MotionEvent): Float {
            if (event.pointerCount < 2) return 0f
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return sqrt(dx * dx + dy * dy)
        }

        fun focalPoint(event: MotionEvent): Offset {
            if (event.pointerCount < 2) return Offset.Zero
            return Offset(
                (event.getX(0) + event.getX(1)) / 2f,
                (event.getY(0) + event.getY(1)) / 2f
            )
        }

        fun endPinch() {
            if (!pinching) return
            pinching = false
            imageGalleryNativePinching = false
            recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
            onPinchEnd()
        }

        val listener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                if (
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL ||
                    event.actionMasked == MotionEvent.ACTION_POINTER_UP
                ) {
                    val wasPinching = pinching
                    endPinch()
                    return wasPinching
                }
                if (event.pointerCount >= 2) {
                    val currentDistance = distance(event)
                    if (currentDistance > 1f && !pinching) {
                        pinching = true
                        imageGalleryNativePinching = true
                        startDistance = currentDistance
                        recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                        onPinchStart(focalPoint(event))
                    }
                    return true
                }
                return pinching
            }

            override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
                if (
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL ||
                    event.actionMasked == MotionEvent.ACTION_POINTER_UP
                ) {
                    endPinch()
                } else if (event.pointerCount >= 2) {
                    val currentDistance = distance(event)
                    if (currentDistance <= 1f) return
                    if (!pinching) {
                        pinching = true
                        imageGalleryNativePinching = true
                        startDistance = currentDistance
                        recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                        onPinchStart(focalPoint(event))
                    } else {
                        val scale = (currentDistance / startDistance).coerceIn(0.45f, 2.40f)
                        onPinch(scale, focalPoint(event))
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit
        }

        imageGalleryPinchTouchListeners[recyclerView] = listener
        recyclerView.addOnItemTouchListener(listener)
    }

    @Composable
    private fun HeManagerApp() {
        val prefs = remember { HePrefs(this) }
        var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
        var token by remember { mutableStateOf(prefs.token) }
        var serverHistory by remember { mutableStateOf(prefs.serverHistory) }

        if (serverUrl.isBlank() || token.isBlank()) {
            LoginScreen(
                initialServer = serverUrl,
                serverHistory = serverHistory,
                onRemoveServer = {
                    prefs.removeServerHistory(it)
                    serverHistory = prefs.serverHistory
                },
                onLoggedIn = { nextServer, nextToken ->
                    serverUrl = nextServer
                    token = nextToken
                    prefs.saveCredentials(nextServer, nextToken)
                    serverHistory = prefs.serverHistory
                }
            )
        } else {
            // rememberSaveable：creators 屏幕标志要跨 Activity 重建保留（config 改、
            // process death after 打开重型播放器 Activity）。普通 remember 在返回路径
            // 上 reset 为 false，会掉到 library 根，再一次返回直接退 App。
            var showCreators by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(false)
            }
            var showSettings by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(false)
            }
            if (showCreators) {
                CreatorsScreen(
                    serverUrl = serverUrl,
                    token = token,
                    onBack = { showCreators = false },
                    onLogout = {
                        showCreators = false
                        prefs.clearToken()
                        token = ""
                    }
                )
            } else if (showSettings) {
                SettingsScreen(
                    serverUrl = serverUrl,
                    serverHistory = serverHistory,
                    onBack = { showSettings = false },
                    onSwitchServer = { nextServer ->
                        prefs.serverUrl = nextServer
                        prefs.clearToken()
                        serverUrl = nextServer
                        token = ""
                        showSettings = false
                    },
                    onLogout = {
                        prefs.clearToken()
                        token = ""
                        showSettings = false
                    }
                )
            } else {
                LibraryScreenV2(
                    serverUrl = serverUrl,
                    token = token,
                    onOpenCreators = { showCreators = true },
                    onOpenSettings = { showSettings = true },
                    onLogout = {
                        prefs.clearToken()
                        token = ""
                    }
                )
            }
        }
    }
}
