package com.hemanager.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 现代圆滑形状（向后兼容原 CutCornerShape 签名）。
 *
 * 全面升级为现代高质感平滑圆角，默认对四角均生效，
 * 解决原切角设计在手机端视觉细碎、生硬割裂的问题。
 *
 * @param cut 圆角大小（dp），默认 14dp
 */
class CutCornerShape(
    private val cut: Dp,
    private val tr: Boolean = true,
    private val tl: Boolean = true,
    private val br: Boolean = true,
    private val bl: Boolean = true,
) : Shape {
    private val delegate = RoundedCornerShape(
        topStart = if (tl) cut else 0.dp,
        topEnd = if (tr) cut else 0.dp,
        bottomEnd = if (br) cut else 0.dp,
        bottomStart = if (bl) cut else 0.dp,
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = delegate.createOutline(size, layoutDirection, density)
}

/**
 * 常用圆角档位便利工厂与语义化设计 Token。
 */
object HeCut {
    fun standard(cut: Dp = 18.dp): CutCornerShape = CutCornerShape(cut)
    fun small(cut: Dp = 10.dp): CutCornerShape = CutCornerShape(cut)
    fun chip(cut: Dp = 10.dp): CutCornerShape = CutCornerShape(cut)
    fun sheet(cut: Dp = 24.dp): CutCornerShape =
        CutCornerShape(cut, tl = true, tr = true, bl = false, br = false)
}

object HeShapes {
    val Card = RoundedCornerShape(18.dp)
    val Tile = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(10.dp)
    val Input = RoundedCornerShape(14.dp)
    val Dialog = RoundedCornerShape(22.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** 角的语义枚举（用在 AngularPanel 的 corners 参数里）。 */
enum class Corner { TL, TR, BR, BL }
