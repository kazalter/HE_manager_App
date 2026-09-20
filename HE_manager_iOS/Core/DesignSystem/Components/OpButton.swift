import SwiftUI

public enum OpButtonSize {
    case small, medium, large

    var verticalPadding: CGFloat {
        switch self {
        case .small: return 8
        case .medium: return 11
        case .large: return 14
        }
    }

    var horizontalPadding: CGFloat {
        switch self {
        case .small: return 16
        case .medium: return 20
        case .large: return 26
        }
    }

    var fontSize: CGFloat {
        switch self {
        case .small: return 12
        case .medium: return 13.5
        case .large: return 15
        }
    }

    var cut: CGFloat {
        switch self {
        case .small: return 8
        case .medium: return 10
        case .large: return 10
        }
    }
}

public struct YellowCTA: View {
    public let title: String
    public let icon: String?
    public let size: OpButtonSize
    public let fullWidth: Bool
    public let action: () -> Void

    public init(
        title: String,
        icon: String? = nil,
        size: OpButtonSize = .medium,
        fullWidth: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.icon = icon
        self.size = size
        self.fullWidth = fullWidth
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: size.fontSize, weight: .bold))
                }
                Text(title.uppercased())
                    .font(.system(size: size.fontSize, weight: .bold, design: .monospaced))
                    .tracking(1.0)
            }
            .foregroundColor(OPTheme.onYellow)
            .padding(.horizontal, size.horizontalPadding)
            .padding(.vertical, size.verticalPadding)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .background(
                CutCornerShape(cut: size.cut, tl: false, tr: true, br: false, bl: true)
                    .fill(OPTheme.yellow)
            )
            .clipShape(CutCornerShape(cut: size.cut, tl: false, tr: true, br: false, bl: true))
        }
        .buttonStyle(.plain)
    }
}

public struct GhostCTA: View {
    public let title: String
    public let icon: String?
    public let size: OpButtonSize
    public let fullWidth: Bool
    public let action: () -> Void

    public init(
        title: String,
        icon: String? = nil,
        size: OpButtonSize = .medium,
        fullWidth: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.icon = icon
        self.size = size
        self.fullWidth = fullWidth
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: size.fontSize, weight: .medium))
                }
                Text(title.uppercased())
                    .font(.system(size: size.fontSize, weight: .medium, design: .monospaced))
                    .tracking(0.8)
            }
            .foregroundColor(OPTheme.opWhite)
            .padding(.horizontal, size.horizontalPadding)
            .padding(.vertical, size.verticalPadding)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .background(
                CutCornerShape(cut: size.cut, tl: false, tr: true, br: false, bl: true)
                    .fill(OPTheme.panel)
            )
            .overlay(
                CutCornerShape(cut: size.cut, tl: false, tr: true, br: false, bl: true)
                    .stroke(OPTheme.hairlineHi, lineWidth: 1)
            )
            .clipShape(CutCornerShape(cut: size.cut, tl: false, tr: true, br: false, bl: true))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - FilterTab (Cut-corner pill with Diamond active state)
public struct FilterTab: View {
    public let label: String
    public let active: Bool
    public let onClick: () -> Void

    public init(label: String, active: Bool, onClick: @escaping () -> Void) {
        self.label = label
        self.active = active
        self.onClick = onClick
    }

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 6) {
                if active {
                    Diamond()
                        .fill(OPTheme.onYellow)
                        .frame(width: 6, height: 6)
                }
                Text(label)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(active ? OPTheme.onYellow : OPTheme.opWhiteSoft)
            }
            .padding(.horizontal, 13)
            .padding(.vertical, 7)
            .background(
                CutCornerShape(cut: 7)
                    .fill(active ? OPTheme.yellow : Color.clear)
            )
            .overlay(
                CutCornerShape(cut: 7)
                    .stroke(active ? Color.clear : OPTheme.hairlineMid, lineWidth: 1)
            )
            .clipShape(CutCornerShape(cut: 7))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - IconBtn4 (36pt circular HUD icon button)
public struct IconBtn4: View {
    public let icon: String
    public let action: () -> Void

    public init(icon: String, action: @escaping () -> Void) {
        self.icon = icon
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(OPTheme.ink)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Circle()
                            .stroke(OPTheme.hairlineMid, lineWidth: 1)
                    )

                Image(systemName: icon)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(OPTheme.opWhite)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - FloatingPlayButton (Cut-corner yellow PLAY button)
public struct FloatingPlayButton: View {
    public let label: String
    public let action: () -> Void

    public init(label: String = "PLAY", action: @escaping () -> Void) {
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: "play.fill")
                    .font(.system(size: 11, weight: .bold))
                Text(label)
                    .font(.system(size: 12, weight: .heavy, design: .monospaced))
                    .tracking(1.2)
            }
            .foregroundColor(OPTheme.onYellow)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(
                CutCornerShape(cut: 8)
                    .fill(OPTheme.yellow)
            )
            .clipShape(CutCornerShape(cut: 8))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - CodeChip (e.g. IMG-V189)
public struct CodeChip: View {
    public let text: String
    public var color: Color = OPTheme.yellow

    public init(text: String, color: Color = OPTheme.yellow) {
        self.text = text
        self.color = color
    }

    public var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .bold, design: .monospaced))
            .foregroundColor(color)
            .tracking(0.5)
    }
}

// MARK: - StatusPill (ONGOING / COMPLETED)
public struct StatusPill: View {
    public let label: String
    public let color: Color

    public init(label: String, color: Color) {
        self.label = label
        self.color = color
    }

    public var body: some View {
        Text(label)
            .font(.system(size: 9.5, weight: .bold, design: .monospaced))
            .foregroundColor(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.14))
            .clipShape(RoundedRectangle(cornerRadius: 3))
    }
}
