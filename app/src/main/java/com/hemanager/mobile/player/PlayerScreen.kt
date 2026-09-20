package com.hemanager.mobile.player

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hemanager.mobile.player.model.PlayerStatus
import com.hemanager.mobile.player.model.PlayerUiState
import com.hemanager.mobile.player.state.PlayerGestureHandler
import com.hemanager.mobile.player.state.SystemControls
import com.hemanager.mobile.player.state.playerGestures
import com.hemanager.mobile.player.ui.AspectModeSheet
import com.hemanager.mobile.player.ui.BarIndicator
import com.hemanager.mobile.player.ui.DoubleTapSeekFeedback
import com.hemanager.mobile.player.ui.DoubleTapSide
import com.hemanager.mobile.player.ui.ErrorOverlay
import com.hemanager.mobile.player.ui.GestureFeedback
import com.hemanager.mobile.player.ui.LockOverlay
import com.hemanager.mobile.player.ui.PlayerControlsOverlay
import com.hemanager.mobile.player.ui.PortraitInfoSection
import com.hemanager.mobile.player.ui.ResumeDialog
import com.hemanager.mobile.player.ui.SeekIndicator
import com.hemanager.mobile.player.ui.SpeedSheet
import com.hemanager.mobile.player.ui.TracksSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-level player screen.
 *
 * - Portrait: 16:9 video on top, scrolling info section below.
 * - Landscape: full-bleed video with overlay controls.
 *
 * Owns the gesture feedback transient state (seek bubble, brightness/volume bars,
 * 2x indicator). The VM owns the player state; the screen owns "what is currently
 * being shown to the user about the gesture they're making". This split is what
 * lets configChanges keep the player alive while the screen freely tears down
 * its overlay state on rebuild.
 */
@UnstableApi
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    systemControls: SystemControls,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()

    val resumeAsk = remember { ResumeAskTracker() }
    LaunchedEffect(state.media?.id, state.durationMs, state.inPictureInPicture) {
        val media = state.media ?: return@LaunchedEffect
        // 如果当前处于画中画小窗模式，直接标记已询问并跳过弹窗，
        // 避免小窗内被模态 Dialog 遮挡且无法点击；退出画中画回到全屏时也不会突兀弹窗打断播放。
        if (state.inPictureInPicture) {
            resumeAsk.markAsked(media.id)
            return@LaunchedEffect
        }
        val durationSec = (state.durationMs / 1000L).toInt().takeIf { it > 0 } ?: media.duration
        if (durationSec <= 0) return@LaunchedEffect
        val nearEnd = media.progress > 0 && media.progress.toFloat() / durationSec >= 0.85f
        if (nearEnd && !resumeAsk.askedFor(media.id)) {
            resumeAsk.markAsked(media.id)
            resumeAsk.show(media.progress)
        }
    }

    // 用户在弹窗显示或待处理时按下 Home 切入画中画：自动关闭弹窗，默认继续上次进度播放
    LaunchedEffect(state.inPictureInPicture) {
        if (state.inPictureInPicture && resumeAsk.pending != null) {
            resumeAsk.dismiss()
            viewModel.resumeFromSaved()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 画中画小窗：整窗只画视频，不要竖屏那套 16:9 + 信息区布局。
        if (isLandscape || state.inPictureInPicture) {
            VideoFrame(
                viewModel = viewModel,
                systemControls = systemControls,
                state = state,
                isFullscreen = true,
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                ) {
                    VideoFrame(
                        viewModel = viewModel,
                        systemControls = systemControls,
                        state = state,
                        isFullscreen = false,
                        onBack = onBack,
                        onToggleFullscreen = onToggleFullscreen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                PortraitInfoSection(
                    state = state,
                    onToggleFavorite = { scope.launch { viewModel.toggleFavorite() } },
                    onToggleWatched = { scope.launch { viewModel.toggleWatched() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070A12)),
                )
            }
        }

        if (!state.inPictureInPicture) {
            resumeAsk.pending?.let { resumeAt ->
                ResumeDialog(
                    resumeAtSeconds = resumeAt,
                    onContinue = {
                        resumeAsk.dismiss()
                        viewModel.resumeFromSaved()
                    },
                    onRestart = {
                        resumeAsk.dismiss()
                        viewModel.restartFromBeginning()
                    },
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun VideoFrame(
    viewModel: PlayerViewModel,
    systemControls: SystemControls,
    state: PlayerUiState,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }

    var seekIndicator by remember { mutableStateOf<SeekIndicator?>(null) }
    var barIndicator by remember { mutableStateOf<BarIndicator?>(null) }
    var longPress2xActive by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showAspectSheet by remember { mutableStateOf(false) }
    var showTracksSheet by remember { mutableStateOf(false) }

    // Position in millis when a horizontal scrub begins. Captured at onSeekStart so
    // ratio-based math during the drag is anchored to a stable reference, not to a
    // ticker that updates while the user's finger is down.
    var scrubStartPositionMs by remember { mutableLongStateOf(0L) }

    // --- Double-tap seek accumulator ---
    // Each double-tap on the same side adds ±10s to the accumulator; switching sides
    // commits the previous accumulator immediately and starts a fresh one. The seek
    // is NOT applied per-tap — only after 600 ms of no further taps (so the user sees
    // the visual climb to e.g. "30s" then commits all at once). The visual lingers
    // briefly during the fade-out so the final value stays readable.
    var doubleTapSide by remember { mutableStateOf<DoubleTapSide?>(null) }
    var doubleTapAccumSec by remember { mutableIntStateOf(0) }
    var doubleTapVisible by remember { mutableStateOf(false) }
    var commitJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(barIndicator) {
        if (barIndicator != null) {
            delay(800L)
            barIndicator = null
        }
    }

    LaunchedEffect(state.inPictureInPicture) {
        if (state.inPictureInPicture) {
            showSpeedSheet = false
            showAspectSheet = false
            showTracksSheet = false
            seekIndicator = null
            barIndicator = null
            doubleTapVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged {
                widthPx = it.width.toFloat()
                heightPx = it.height.toFloat()
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = viewModel.player
                }
            },
            update = { view ->
                view.resizeMode = state.aspectMode.resizeMode
                // 常亮只在真正播放时保留。之前写死 true，暂停后屏幕也永不熄灭。
                view.keepScreenOn = state.isPlaying
            },
        )

        // Gesture layer — beneath the controls so taps on visible buttons go to those,
        // but covers the rest of the video region for our custom gestures.
        //
        // 所有回调和尺寸都写进 remember 出来的 handler，手势协程现读（见
        // PlayerGestureHandler 的注释：pointerInput 的协程不会因重组而重启，
        // 按值捕获的参数会过期）。
        val gestures = remember { PlayerGestureHandler() }
        SideEffect {
            gestures.widthPx = widthPx.takeIf { it > 0f } ?: 1f
            gestures.heightPx = heightPx.takeIf { it > 0f } ?: 1f
            // Single-tap is the ONLY thing that toggles controls. Read latest
            // state at fire time so we don't toggle off a stale snapshot.
            gestures.onSingleTap = {
                val current = viewModel.uiState.value.controlsVisible
                viewModel.setControlsVisible(!current)
            }
            gestures.onDoubleTap = { offset ->
                val w = gestures.widthPx
                // Centre band toggles play/pause without participating in the
                // accumulator at all (so a play/pause double-tap doesn't leave
                // a stray "10s" indicator hanging).
                if (offset.x in (w * 0.42f)..(w * 0.58f)) {
                    viewModel.togglePlayPause()
                } else {
                    val newSide =
                        if (offset.x < w * 0.5f) DoubleTapSide.LEFT else DoubleTapSide.RIGHT

                    // If the user switched sides mid-accumulation, commit the old
                    // direction immediately before starting fresh on the new side.
                    if (doubleTapSide != null && doubleTapSide != newSide && doubleTapAccumSec != 0) {
                        viewModel.seekBy(doubleTapAccumSec * 1000L)
                        doubleTapAccumSec = 0
                    }

                    doubleTapSide = newSide
                    doubleTapAccumSec += if (newSide == DoubleTapSide.LEFT) -10 else 10
                    doubleTapVisible = true

                    // Restart the 600 ms commit timer.
                    commitJob?.cancel()
                    commitJob = coroutineScope.launch {
                        delay(600L)
                        val toSeek = doubleTapAccumSec
                        if (toSeek != 0) viewModel.seekBy(toSeek * 1000L)
                        doubleTapVisible = false
                        // Wait for the fade-out animation, then reset state. If the
                        // user tapped again during the fade, doubleTapVisible will
                        // have been re-flipped to true and we leave the value alone.
                        delay(320L)
                        if (!doubleTapVisible) {
                            doubleTapAccumSec = 0
                            doubleTapSide = null
                        }
                    }
                }
            }
            gestures.onLongPressStart = {
                viewModel.startLongPressBoost()
                longPress2xActive = true
            }
            gestures.onLongPressEnd = {
                viewModel.endLongPressBoost()
                longPress2xActive = false
            }
            gestures.onSeekStart = {
                scrubStartPositionMs = viewModel.uiState.value.positionMs
                viewModel.setScrubbing(true)
            }
            gestures.onSeekDelta = { _, totalDx ->
                val w = gestures.widthPx
                val duration = viewModel.uiState.value.durationMs
                if (duration > 0L) {
                    // 90% of the screen → 120s (or full duration, whichever smaller).
                    val window = (kotlin.math.min(duration, 120_000L)).toFloat()
                    val deltaMs = (totalDx / w * window).toLong()
                    val target = (scrubStartPositionMs + deltaMs).coerceIn(0L, duration)
                    viewModel.previewScrubPosition(target)
                    seekIndicator = SeekIndicator.Scrub(target, duration)
                }
            }
            gestures.onSeekEnd = {
                viewModel.commitScrub(viewModel.uiState.value.positionMs)
                seekIndicator = null
            }
            gestures.onBrightnessStart = { /* nothing — first delta will set the bar */ }
            gestures.onBrightnessDelta = { dy ->
                val h = gestures.heightPx
                // Drag full height ~= ±1.0 brightness change.
                val current = systemControls.getBrightness()
                val next = (current - dy / h).coerceIn(0.01f, 1f)
                systemControls.setBrightness(next)
                barIndicator = BarIndicator(BarIndicator.Kind.BRIGHTNESS, next)
            }
            gestures.onBrightnessEnd = { /* leave indicator to fade via LaunchedEffect */ }
            gestures.onVolumeStart = { }
            gestures.onVolumeDelta = { dy ->
                val h = gestures.heightPx
                val current = systemControls.getVolume()
                val next = (current - dy / h).coerceIn(0f, 1f)
                systemControls.setVolume(next)
                barIndicator = BarIndicator(BarIndicator.Kind.VOLUME, next)
            }
            gestures.onVolumeEnd = { }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(
                    enabled = !state.locked && state.status !is PlayerStatus.Error,
                    handler = gestures,
                ),
        )

        when (val status = state.status) {
            is PlayerStatus.Error -> ErrorOverlay(
                message = status.message,
                onRetry = { viewModel.retryCurrent() },
                onBack = onBack,
                // 出错时如果这个播放列表还有下一个，直接给一条出路，
                // 而不是把用户困在"重试 / 返回"两个选项里。
                onPlayNext = if (state.canSkipNext) {
                    { viewModel.playNext() }
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
            )
            else -> PlayerControlsOverlay(
                state = state,
                isFullscreen = isFullscreen,
                onBack = onBack,
                // 全屏顶栏的收藏按钮以前是空实现，点了毫无反应。接到 VM 上。
                onToggleFavorite = { coroutineScope.launch { viewModel.toggleFavorite() } },
                onTogglePlay = { viewModel.togglePlayPause() },
                onRewind = { viewModel.seekBy(-10_000L) },
                onForward = { viewModel.seekBy(10_000L) },
                onScrubStart = { viewModel.setScrubbing(true) },
                onScrubMove = { fraction ->
                    val target = (fraction * state.durationMs).toLong()
                    viewModel.previewScrubPosition(target)
                },
                onScrubEnd = { fraction ->
                    val target = (fraction * state.durationMs).toLong()
                    viewModel.commitScrub(target)
                },
                onSkipPrevious = { viewModel.playPrevious() },
                onSkipNext = { viewModel.playNext() },
                onCyclePlaybackMode = {
                    val newMode = viewModel.cyclePlaybackMode()
                    Toast.makeText(context, "已切换为：${newMode.label}", Toast.LENGTH_SHORT).show()
                },
                onSpeedClick = { showSpeedSheet = true },
                onAspectClick = { showAspectSheet = true },
                onTracksClick = { showTracksSheet = true },
                onLockClick = {
                    val nowLocked = !state.locked
                    viewModel.setLocked(nowLocked)
                    // When locking, also collapse the controls so the fade-out is in
                    // sync with the lock overlay coming up. When unlocking, leave
                    // controls hidden — the user can tap the empty area to reveal them.
                    if (nowLocked) viewModel.setControlsVisible(false)
                },
                onToggleFullscreen = onToggleFullscreen,
                // 任何面板（倍速 / 比例 / 音轨）打开时冻结 3s 自动隐藏，
                // 否则面板还开着，底下的控制条已经淡出，关掉面板就是一片空白。
                autoHideEnabled = !showSpeedSheet && !showAspectSheet && !showTracksSheet,
                onAutoHide = { viewModel.setControlsVisible(false) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Horizontal-scrub bubble + brightness/volume bars + 2x indicator.
        GestureFeedback(
            seekIndicator = seekIndicator,
            barIndicator = barIndicator,
            showLongPress2x = longPress2xActive,
            modifier = Modifier.fillMaxSize(),
        )

        // Double-tap accumulating ±10s feedback. Stays mounted across fade-out so the
        // final accumulated value stays readable while the alpha animates down.
        DoubleTapSeekFeedback(
            side = doubleTapSide,
            accumulatedSeconds = doubleTapAccumSec,
            visible = doubleTapVisible,
            modifier = Modifier.fillMaxSize(),
        )

        if (!state.inPictureInPicture) {
            if (showSpeedSheet) {
                SpeedSheet(
                    currentSpeed = state.playbackSpeed,
                    onPick = { viewModel.setSpeed(it) },
                    onDismiss = { showSpeedSheet = false },
                )
            }
            if (showAspectSheet) {
                AspectModeSheet(
                    current = state.aspectMode,
                    onPick = {
                        viewModel.setAspectMode(it)
                        Toast.makeText(context, it.label, Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showAspectSheet = false },
                )
            }
            if (showTracksSheet) {
                TracksSheet(
                    audioTracks = state.audioTracks,
                    subtitleTracks = state.subtitleTracks,
                    subtitlesEnabled = state.subtitlesEnabled,
                    onPickAudio = { viewModel.selectAudioTrack(it) },
                    onPickSubtitle = { viewModel.selectSubtitleTrack(it) },
                    onDismiss = { showTracksSheet = false },
                )
            }
        }

        // Lock overlay sits above everything else so when locked, no other gesture
        // layer (including the double-tap accumulator) sees taps.
        LockOverlay(
            locked = state.locked,
            onUnlock = { viewModel.setLocked(false) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Tracks whether the resume dialog has already been offered for a particular media id
 * in the current session. Backed by Compose state so dismissals trigger recomposition.
 */
private class ResumeAskTracker {
    private val asked = mutableSetOf<Int>()
    private var _pending = mutableStateOf<Int?>(null)
    val pending: Int? get() = _pending.value

    fun askedFor(id: Int): Boolean = id in asked
    fun markAsked(id: Int) { asked.add(id) }
    fun show(progress: Int) { _pending.value = progress }
    fun dismiss() { _pending.value = null }
}
