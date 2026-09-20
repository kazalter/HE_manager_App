import SwiftUI

public struct TypeChip: View {
    public let mediaType: String
    public let onYellow: Bool

    public init(mediaType: String, onYellow: Bool = false) {
        self.mediaType = mediaType
        self.onYellow = onYellow
    }

    public var code: String {
        switch mediaType.lowercased() {
        case "video": return "VID"
        case "manga": return "MNG"
        case "image": return "IMG"
        case "audio": return "AUD"
        default: return "MED"
        }
    }

    public var body: some View {
        let bg = onYellow ? OPTheme.yellow : Color.black.opacity(0.78)
        let fg = onYellow ? OPTheme.onYellow : OPTheme.yellow

        Text(code)
            .font(.system(size: 9.5, weight: .bold, design: .monospaced))
            .tracking(0.5)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: true)
            .foregroundColor(fg)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(
                CutCornerShape(cut: 3, tl: false, tr: true, br: false, bl: true)
                    .fill(bg)
            )
            .clipShape(CutCornerShape(cut: 3, tl: false, tr: true, br: false, bl: true))
    }
}

public struct StatusStripe: View {
    public let status: String
    public let progress: Int
    public let total: Int

    public init(status: String, progress: Int = 0, total: Int = 0) {
        self.status = status
        self.progress = progress
        self.total = total
    }

    public var stripeColor: Color {
        switch status.lowercased() {
        case "viewed":
            return OPTheme.online
        case "viewing":
            return OPTheme.yellow
        default:
            return OPTheme.opWhiteFaint
        }
    }

    public var body: some View {
        Rectangle()
            .fill(stripeColor)
            .frame(height: 3)
    }
}
