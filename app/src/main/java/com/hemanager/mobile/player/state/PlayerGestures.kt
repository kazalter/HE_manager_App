package com.hemanager.mobile.player.state

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/** Categories the gesture recogniser can resolve a drag into. */
enum class DragKind { NONE, SEEK, BRIGHTNESS, VOLUME }

/**
 * 播放区手势的可变载体。
 *
 * **为什么需要它**：`Modifier.pointerInput(Unit) { ... }` 的协程只在 key 变化或节点
 * 重新挂载时才重启（见 `SuspendingPointerInputModifierNodeImpl.update`：key 相同就
 * 只更新字段、不 reset 正在跑的协程）。所以手势块捕获的**按值传入的参数**会一直停在
 * 协程启动那一刻的快照上：
 *
 *  - `widthPx` 按值传进去 → 横竖屏切换后（Activity 不重建，Compose 树保留），左右
 *    半屏的分界线还停在旧宽度的一半，"左侧调亮度 / 右侧调音量"就错位了。
 *  - 读 `state.durationMs` 的回调 lambda 同理 → 播放列表切下一集后，横向拖动进度
 *    用的还是上一集的时长。
 *
 * 把这些值收进一个 `remember` 出来的稳定对象里，手势协程每次触发时现读字段，就不会
 * 有快照问题；同时 `pointerInput(handler)` 的 key 恒定，手势协程也不会被反复重启。
 */
class PlayerGestureHandler {
    /** 播放区宽度（px）。由 `onSizeChanged` 写入。 */
    var widthPx: Float = 1f
    /** 播放区高度（px）。 */
    var heightPx: Float = 1f

    var onSingleTap: () -> Unit = {}
    var onDoubleTap: (Offset) -> Unit = {}
    var onLongPressStart: () -> Unit = {}
    var onLongPressEnd: () -> Unit = {}
    var onSeekStart: () -> Unit = {}
    var onSeekDelta: (deltaPx: Float, totalDeltaPx: Float) -> Unit = { _, _ -> }
    var onSeekEnd: () -> Unit = {}
    var onBrightnessStart: () -> Unit = {}
    var onBrightnessDelta: (deltaPx: Float) -> Unit = {}
    var onBrightnessEnd: () -> Unit = {}
    var onVolumeStart: () -> Unit = {}
    var onVolumeDelta: (deltaPx: Float) -> Unit = {}
    var onVolumeEnd: () -> Unit = {}
}

/**
 * Combined player-region gesture handler. Routes the same touch stream into:
 *  - tap / double-tap / long-press (via [detectTapGestures])
 *  - drag with axis-and-side detection (via [awaitEachGesture])
 *
 * Two separate `pointerInput` blocks coexist: Compose's gesture system arbitrates between
 * them — a finger that drags will cancel the tap detector, and a finger that just taps
 * never clears the drag's slop. This is the canonical pattern.
 *
 * Conflict-handling rules:
 *  - Slop: ignore drags below ~touchSlop.
 *  - Axis lock: horizontal vs vertical based on which delta is larger AND ≥ 1.35× the
 *    other (matches the original Java impl). Below ratio → no kind yet.
 *  - Side lock for vertical: left half → brightness, right half → volume. Decided once
 *    based on where the finger went DOWN. Once committed, we ride that axis to release.
 *
 * When [enabled] is false (locked screen), this whole modifier is a no-op so taps/drags
 * fall through to whatever the locked overlay shows.
 */
fun Modifier.playerGestures(
    enabled: Boolean,
    handler: PlayerGestureHandler,
): Modifier {
    if (!enabled) return this
    return this
        .pointerInput(handler) {
            // Strict single-tap-only contract:
            //  - onTap fires ONLY for a confirmed clean tap-up after the double-tap
            //    arbitration window. detectTapGestures handles the cancellation
            //    semantics: if a 2nd tap arrives → onDoubleTap fires and onTap does
            //    NOT; if movement breaks slop → no tap; if held past long-press →
            //    onLongPress fires and onTap does NOT.
            //  - This means single-tap is the ONLY thing that toggles controls. No
            //    other gesture (double-tap, drag, long-press, scrub) changes the
            //    controls' visibility — exactly what the spec asks for.
            //
            // The long-press flag is shared with onPress so onLongPressEnd fires when
            // the finger eventually lifts after a long-press fired.
            val longPressFlag = booleanArrayOf(false)
            detectTapGestures(
                onPress = {
                    longPressFlag[0] = false
                    try {
                        tryAwaitRelease()
                    } finally {
                        if (longPressFlag[0]) handler.onLongPressEnd()
                    }
                },
                onTap = { handler.onSingleTap() },
                onDoubleTap = { offset -> handler.onDoubleTap(offset) },
                onLongPress = {
                    longPressFlag[0] = true
                    handler.onLongPressStart()
                },
            )
        }
        .pointerInput(handler) {
            awaitEachGesture {
                val down: PointerInputChange = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Main,
                )
                val startX = down.position.x
                val slop = viewConfiguration.touchSlop
                val ratio = 1.35f
                // 现读，而不是启动协程那一刻的快照。
                val width = handler.widthPx.takeIf { it > 1f } ?: size.width.toFloat().coerceAtLeast(1f)

                var kind = DragKind.NONE
                var totalDx = 0f
                var totalDy = 0f

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.first()
                    if (!change.pressed) break
                    // 多指（缩放等）出现时放弃本次单指拖动，避免和系统手势打架。
                    if (event.changes.count { it.pressed } > 1) {
                        if (kind != DragKind.NONE) break
                        continue
                    }

                    val deltaX = change.positionChange().x
                    val deltaY = change.positionChange().y
                    totalDx += deltaX
                    totalDy += deltaY

                    if (kind == DragKind.NONE) {
                        val ax = abs(totalDx)
                        val ay = abs(totalDy)
                        if (ax > slop && ax > ay * ratio) {
                            kind = DragKind.SEEK
                            handler.onSeekStart()
                        } else if (ay > slop && ay > ax * ratio) {
                            kind = if (startX < width / 2f) DragKind.BRIGHTNESS else DragKind.VOLUME
                            when (kind) {
                                DragKind.BRIGHTNESS -> handler.onBrightnessStart()
                                DragKind.VOLUME -> handler.onVolumeStart()
                                else -> Unit
                            }
                        }
                    }

                    when (kind) {
                        DragKind.SEEK -> {
                            change.consume()
                            handler.onSeekDelta(deltaX, totalDx)
                        }
                        DragKind.BRIGHTNESS -> {
                            change.consume()
                            handler.onBrightnessDelta(deltaY)
                        }
                        DragKind.VOLUME -> {
                            change.consume()
                            handler.onVolumeDelta(deltaY)
                        }
                        DragKind.NONE -> Unit
                    }
                }

                when (kind) {
                    DragKind.SEEK -> handler.onSeekEnd()
                    DragKind.BRIGHTNESS -> handler.onBrightnessEnd()
                    DragKind.VOLUME -> handler.onVolumeEnd()
                    DragKind.NONE -> Unit
                }
            }
        }
}
