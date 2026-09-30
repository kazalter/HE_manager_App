package com.hemanager.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 全局色板 —— 整个 app 的颜色单一真相源。
 *
 * 同时被 MainActivity 的 colorScheme 和 player/ASMR 模块使用，
 * 避免 PlayerTheme.kt 早期版本的「两份重复定义、改一处忘一处」问题。
 *
 * 添加新颜色时优先放这里；语义色（状态、强调）见 [HeColors.Status]。
 */
object HeColors {
    // ===================================================================
    // HE OP 设计语言（黑底 + 单一高饱和黄 + hairline）—— 用于新版库/登录/创作者屏
    // 旧的蓝紫 token 在下方保留给 player / audio 模块，不要删。
    // ===================================================================

    // ---- HE OP 表面层（偏冷的近黑 + 微层次） ----
    val Void        = Color(0xFF07080B)  // 最深 — 顶级背景、状态条底色
    val Ink         = Color(0xFF0E1016)  // 主背景底色
    val Panel       = Color(0xFF141720)  // 卡片背景深处
    val PanelLight  = Color(0xFF1D212D)  // 卡片顶部微光高光表面
    val OpSurface   = Color(0xFF202534)  // 抬升卡片 / drawer item active
    val SurfaceAlt  = Color(0xFF262C3D)

    // ---- 表面高质感微光渐变 ----
    val PanelGradient = Brush.verticalGradient(
        listOf(PanelLight, Panel)
    )

    // ---- HE OP 描边（hairline 系统 + 边缘双色光泽） ----
    val Hairline    = Color(0x14FFFFFF) // alpha 0.08 — 标准 separator
    val HairlineMid = Color(0x24FFFFFF) // alpha 0.14 — 卡片描边
    val HairlineHi  = Color(0x40FFFFFF) // alpha 0.25 — 强调描边 / 顶边缘反光
    val HairlineRim = Brush.verticalGradient(
        listOf(Color(0x38FFFFFF), Color(0x14FFFFFF))
    )

    // ---- HE OP 文字 ----
    val OpWhite      = Color(0xFFF4F3EE)  // 主文字
    val OpWhiteSoft  = Color(0xFFB8B9C2)  // 次要文字
    val OpWhiteMuted = Color(0xFF6E6F78)  // muted / metadata
    val OpWhiteFaint = Color(0xFF3C3D45)  // 极淡 / disabled

    // ---- HE OP 签名色：高饱和黄 ----
    val Yellow     = Color(0xFFF5D800)
    val YellowDim  = Color(0xFFB8A100)
    val YellowSoft = Color(0x1AF5D800)  // alpha 0.10

    // ---- CTA 渐变（更丰满灵动的暖金光感） ----
    val CtaGradient = Brush.horizontalGradient(
        listOf(Color(0xFFFFE054), Color(0xFFFFB300))
    )

    // ---- 媒体分类视觉色彩（赋予各类别独立辨识度，彻底告别单调） ----
    val VideoAccent  = Color(0xFFFFB300) // 琥珀暖金 (Video)
    val MangaAccent  = Color(0xFF00E676) // 霓虹薄荷绿 (Manga)
    val ImageAccent  = Color(0xFF00E5FF) // 赛博青蓝 (Image)
    val AudioAccent  = Color(0xFFE040FB) // 电光紫 (Audio)
    val StarAccent   = Color(0xFFFF5252) // 收藏珊瑚红

    fun mediaAccent(type: String?): Color = when (type?.lowercase()) {
        "video" -> VideoAccent
        "manga" -> MangaAccent
        "image" -> ImageAccent
        "audio" -> AudioAccent
        else -> Yellow
    }

    fun mediaChipBg(type: String?): Color = when (type?.lowercase()) {
        "video" -> Color(0x28FFB300)
        "manga" -> Color(0x2800E676)
        "image" -> Color(0x2800E5FF)
        "audio" -> Color(0x28E040FB)
        else -> Color(0x28F5D800)
    }

    fun mediaChipBorder(type: String?): Color = when (type?.lowercase()) {
        "video" -> VideoAccent.copy(alpha = 0.45f)
        "manga" -> MangaAccent.copy(alpha = 0.45f)
        "image" -> ImageAccent.copy(alpha = 0.45f)
        "audio" -> AudioAccent.copy(alpha = 0.45f)
        else -> Yellow.copy(alpha = 0.45f)
    }

    // ---- HE OP 次要 accent ----
    val Cyan       = Color(0xFF5CE5D7)
    val CyanSoft   = Color(0x1A5CE5D7)

    // ---- HE OP 状态色 ----
    val Online   = Color(0xFF7BC494)
    val OpDanger = Color(0xFFFF5C5C)

    // ---- CTA 文字色（在 Yellow 上） ----
    val OnYellow = Color(0xFF0E0F00)

    // ===================================================================
    // Material / player bridge colors.
    //
    // Earlier builds used a blue-purple Material palette here. The main app
    // has since moved to the HE OP black + yellow language, so these bridge
    // tokens now map Material components and player controls back onto that
    // same visual system.
    // ===================================================================

    // ---- 主色 ----
    val Primary = Yellow
    val Secondary = Cyan
    val Tertiary = Color(0xFFF0B75A)

    // ---- 表面 / 背景 ----
    val Background = Ink
    val Surface = Panel
    val SurfaceVariant = SurfaceAlt
    val PrimaryContainer = Color(0xFF3A3300)
    val SecondaryContainer = Color(0xFF0F3A35)
    val TertiaryContainer = Color(0xFF463713)
    val ErrorContainer = Color(0xFF54212F)

    // ---- 前景 / 文字 ----
    val OnPrimary = OnYellow
    val OnSecondary = Color(0xFF061412)
    val OnTertiary = Color(0xFF15100A)
    val OnBackground = OpWhite
    val OnSurface = OpWhite
    val OnSurfaceVariant = OpWhiteSoft
    val OnSurfaceMuted = OpWhite.copy(alpha = 0.70f)
    val OnErrorContainer = Color(0xFFFFD9DD)

    // ---- 语义 / 状态 ----
    val Danger = OpDanger

    /**
     * 媒体观看状态相关的语义色。
     * 这些值此前散落在 MainActivity 的 `statusAccentV2` / `progressColorV2` 中，
     * 后续可逐步迁移到这里。
     */
    object Status {
        val Viewed = Online                 // 已看完（绿）
        val Viewing = Yellow                // 继续看（黄）
        val Favorite = Tertiary             // 收藏（金）
        val Missing = Danger                // 文件缺失
    }

    // ---- Player 模块用的半透明蒙层 ----
    val ScrimTop: Brush = Brush.verticalGradient(
        listOf(Color(0xCC000000), Color(0x00000000)),
    )
    val ScrimBottom: Brush = Brush.verticalGradient(
        listOf(Color(0x00000000), Color(0xDD000000)),
    )

    val ButtonGlass = Color(0x66000000)
    val ButtonGlassPressed = Color(0xAA000000)
}

/**
 * 整个 app 默认使用的 Material3 dark color scheme。
 *
 * MainActivity 和未来的 ASMR / Player 容器都引用这一份，
 * 避免重复声明 darkColorScheme(...)。
 */
val HeColorScheme: ColorScheme = darkColorScheme(
    primary = HeColors.Primary,
    secondary = HeColors.Secondary,
    tertiary = HeColors.Tertiary,
    error = HeColors.OpDanger,
    background = HeColors.Background,
    surface = HeColors.Surface,
    surfaceVariant = HeColors.SurfaceVariant,
    primaryContainer = HeColors.PrimaryContainer,
    secondaryContainer = HeColors.SecondaryContainer,
    tertiaryContainer = HeColors.TertiaryContainer,
    errorContainer = HeColors.ErrorContainer,
    onPrimary = HeColors.OnPrimary,
    onSecondary = HeColors.OnSecondary,
    onTertiary = HeColors.OnTertiary,
    onBackground = HeColors.OnBackground,
    onSurface = HeColors.OnSurface,
    onSurfaceVariant = HeColors.OnSurfaceVariant,
    onErrorContainer = HeColors.OnErrorContainer,
)
