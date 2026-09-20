import SwiftUI

public enum OPTheme {
    // MARK: - Surfaces
    public static let void = Color(hex: 0x08090C)       // 顶级极深背景 / 状态栏
    public static let ink = Color(hex: 0x0F1014)        // 主背景
    public static let panel = Color(hex: 0x16171D)      // 卡片面板背景
    public static let opSurface = Color(hex: 0x1B1D25)  // 抬升表面 / active item
    public static let surfaceAlt = Color(hex: 0x23252F) // 变种表面

    // MARK: - Hairline Borders
    public static let hairline = Color.white.opacity(0.07)    // 标准分隔线
    public static let hairlineMid = Color.white.opacity(0.14) // 卡片描边
    public static let hairlineHi = Color.white.opacity(0.22)  // 强调描边

    // MARK: - Typography Colors
    public static let opWhite = Color(hex: 0xF2F1ED)      // 主文字
    public static let opWhiteSoft = Color(hex: 0xB8B9C2)  // 次级文字
    public static let opWhiteMuted = Color(hex: 0x6E6F78) // 弱化 / 元数据
    public static let opWhiteFaint = Color(hex: 0x3C3D45) // 极淡 / disabled

    // MARK: - Signature Brand Accents
    public static let yellow = Color(hex: 0xF5D800)       // HE OP 标志性高饱和黄
    public static let yellowDim = Color(hex: 0xB8A100)
    public static let yellowSoft = Color(hex: 0xF5D800).opacity(0.12)
    public static let onYellow = Color(hex: 0x0E0F00)     // 黄色上的深色前景

    // MARK: - Accents & Status
    public static let cyan = Color(hex: 0x5CE5D7)         // 次要高光 / 标签
    public static let cyanSoft = Color(hex: 0x5CE5D7).opacity(0.12)
    public static let online = Color(hex: 0x7BC494)       // 已看完 / 在线（绿）
    public static let danger = Color(hex: 0xFF5C5C)       // 错误 / 危险（红）
    public static let favorite = Color(hex: 0xF0B75A)     // 收藏（金）

    // MARK: - Ambient Background
    public static var appBackgroundGradient: some View {
        RadialGradient(
            gradient: Gradient(stops: [
                .init(color: yellow.opacity(0.024), location: 0.0),
                .init(color: ink, location: 0.4),
                .init(color: void, location: 1.0)
            ]),
            center: .top,
            startRadius: 0,
            endRadius: 700
        )
        .ignoresSafeArea()
    }
}

extension Color {
    public init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 08) & 0xff) / 255,
            blue: Double((hex >> 00) & 0xff) / 255,
            opacity: alpha
        )
    }
}
