package com.hemanager.mobile.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import com.hemanager.mobile.ApiClient
import com.hemanager.mobile.player.state.SystemControls
import com.hemanager.mobile.player.ui.PlayerColorScheme

/**
 * Host Activity for the Compose player.
 *
 * Orientation behaviour (unchanged from Phase 1):
 *  - Activity does NOT recreate on rotation (configChanges in manifest).
 *  - "Fullscreen" = setRequestedOrientation(LANDSCAPE) + hide system bars.
 *
 * Phase 2 additions:
 *  - Reads `playlist_ids` int-array extra and feeds it to the ViewModel's PlaylistController.
 *  - Creates [SystemControls] tied to this Activity's window so brightness gestures can
 *    override window brightness without affecting the global system value.
 */
@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var orientationUnlocker: OrientationEventListener? = null

    private val playerViewModel by viewModels<PlayerViewModel>(
        factoryProducer = {
            val serverUrl = ApiClient.trimSlash(intent.getStringExtra("server_url"))
            val token = intent.getStringExtra("token") ?: ""
            PlayerViewModel.factory(serverUrl, token)
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.attributes = window.attributes.apply {
            rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
        }

        val mediaId = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title").orEmpty()
        val progress = intent.getIntExtra("progress", 0).coerceAtLeast(0)
        val duration = intent.getIntExtra("duration", 0).coerceAtLeast(0)
        val restart = intent.getBooleanExtra("restart", false)
        val playlist = intent.getIntArrayExtra("playlist_ids")?.toList().orEmpty()

        playerViewModel.bind(
            mediaId = mediaId,
            title = title,
            initialProgressSeconds = progress,
            durationSeconds = duration,
            restart = restart,
        )
        if (playlist.isNotEmpty()) {
            playerViewModel.setPlaylist(playlist)
        }

        val systemControls = SystemControls(this)
        setupOrientationUnlock()

        setContent {
            MaterialTheme(colorScheme = PlayerColorScheme) {
                PlayerScreen(
                    viewModel = playerViewModel,
                    systemControls = systemControls,
                    onBack = { onBackOrFinish() },
                    onToggleFullscreen = { toggleFullscreen() },
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsForOrientation(newConfig.orientation)
    }

    override fun onStart() {
        super.onStart()
        playerViewModel.onActivityStarted()
    }

    override fun onResume() {
        super.onResume()
        applySystemBarsForOrientation(resources.configuration.orientation)
    }

    override fun onStop() {
        // 画中画时窗口依然可见，不能暂停；其余情况（回桌面、切 App、锁屏）一律暂停，
        // 否则播放器没有通知栏控件，声音会在后台一直响而用户无从控制。
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        playerViewModel.onActivityStopped(keepPlaying = inPip)
        super.onStop()
    }

    override fun onDestroy() {
        orientationUnlocker?.disable()
        orientationUnlocker = null
        super.onDestroy()
    }

    /**
     * 用户按 Home / 切换任务时进入画中画。
     *
     * Manifest 早就声明了 `supportsPictureInPicture`，但一直没有任何代码调用
     * [enterPictureInPictureMode]，等于白声明。这里补上：只有正在播放时才进，
     * 画面比例取视频真实比例（越界时回落 16:9，系统对 PiP 比例有 0.42~2.39 的限制）。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (isFinishing || isInPictureInPictureMode) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (!playerViewModel.player.isPlaying) return

        val size = playerViewModel.player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            val raw = size.width.toFloat() / size.height.toFloat()
            if (raw in 0.45f..2.35f) Rational(size.width, size.height) else Rational(16, 9)
        } else {
            Rational(16, 9)
        }
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(ratio).build(),
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // PiP 小窗里没有地方放我们那一整套控件（和竖屏的信息区），
        // 进入时切成纯视频，退出时恢复。
        playerViewModel.setPictureInPicture(isInPictureInPictureMode)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun onBackOrFinish() {
        @Suppress("DEPRECATION")
        onBackPressed()
    }

    private fun toggleFullscreen() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    /**
     * 方向锁自动解除。
     *
     * [toggleFullscreen] / [onBackPressed] 会把 requestedOrientation 钉成
     * LANDSCAPE 或 PORTRAIT，而之前没有任何地方把它放回去——结果是：用户点过一次
     * 全屏按钮之后，"转动手机自动横竖屏"就永久失效了，只能靠按钮切。
     *
     * 这里监听物理方向：一旦手机实际朝向已经和锁定的方向一致，就把
     * requestedOrientation 放回 [ActivityInfo.SCREEN_ORIENTATION_USER]
     * （跟随系统的自动旋转开关），转屏能力恢复，且不会立刻被转回去。
     */
    private fun setupOrientationUnlock() {
        if (orientationUnlocker != null) return
        orientationUnlocker = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val locked = requestedOrientation
                if (
                    locked != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE &&
                    locked != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ) return

                val deviceIsLandscape = orientation in 60..120 || orientation in 240..300
                val deviceIsPortrait = orientation <= 30 || orientation >= 330 ||
                    orientation in 150..210
                val matched = when (locked) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> deviceIsLandscape
                    else -> deviceIsPortrait
                }
                if (matched) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                }
            }
        }
        if (orientationUnlocker?.canDetectOrientation() == true) {
            orientationUnlocker?.enable()
        } else {
            orientationUnlocker = null
        }
    }

    private fun applySystemBarsForOrientation(orientation: Int) {
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
