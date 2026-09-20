import SwiftUI

public struct CutCornerShape: Shape {
    public var cut: CGFloat
    public var tl: Bool
    public var tr: Bool
    public var br: Bool
    public var bl: Bool

    public init(cut: CGFloat = 12, tl: Bool = false, tr: Bool = true, br: Bool = false, bl: Bool = true) {
        self.cut = cut
        self.tl = tl
        self.tr = tr
        self.br = br
        self.bl = bl
    }

    /// Convenience initializer for uniform 4-corner cut
    public init(cut: CGFloat) {
        self.cut = cut
        self.tl = true
        self.tr = true
        self.br = true
        self.bl = true
    }

    public func path(in rect: CGRect) -> Path {
        var path = Path()
        let w = rect.width
        let h = rect.height
        let c = min(cut, min(w / 2, h / 2))

        // Start top edge
        path.move(to: CGPoint(x: tl ? c : 0, y: 0))

        // Top right
        if tr {
            path.addLine(to: CGPoint(x: w - c, y: 0))
            path.addLine(to: CGPoint(x: w, y: c))
        } else {
            path.addLine(to: CGPoint(x: w, y: 0))
        }

        // Bottom right
        if br {
            path.addLine(to: CGPoint(x: w, y: h - c))
            path.addLine(to: CGPoint(x: w - c, y: h))
        } else {
            path.addLine(to: CGPoint(x: w, y: h))
        }

        // Bottom left
        if bl {
            path.addLine(to: CGPoint(x: c, y: h))
            path.addLine(to: CGPoint(x: 0, y: h - c))
        } else {
            path.addLine(to: CGPoint(x: 0, y: h))
        }

        // Top left
        if tl {
            path.addLine(to: CGPoint(x: 0, y: c))
            path.addLine(to: CGPoint(x: c, y: 0))
        } else {
            path.addLine(to: CGPoint(x: 0, y: 0))
        }

        path.closeSubpath()
        return path
    }
}

public struct Diamond: Shape {
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        path.closeSubpath()
        return path
    }
}

public struct CornerSealShape: Shape {
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

public struct HudBracketsView: View {
    public var inset: CGFloat = 8
    public var length: CGFloat = 12
    public var thickness: CGFloat = 1.2
    public var color: Color = OPTheme.yellow
    public var showBottom: Bool = false

    public init(inset: CGFloat = 8, length: CGFloat = 12, thickness: CGFloat = 1.2, color: Color = OPTheme.yellow, showBottom: Bool = false) {
        self.inset = inset
        self.length = length
        self.thickness = thickness
        self.color = color
        self.showBottom = showBottom
    }

    public var body: some View {
        Canvas { context, size in
            let w = size.width
            let h = size.height
            let i = inset
            let l = length
            let t = thickness

            // Top-Left L bracket
            context.fill(Path(CGRect(x: i, y: i, width: l, height: t)), with: .color(color))
            context.fill(Path(CGRect(x: i, y: i, width: t, height: l)), with: .color(color))

            // Top-Right L bracket
            context.fill(Path(CGRect(x: w - i - l, y: i, width: l, height: t)), with: .color(color))
            context.fill(Path(CGRect(x: w - i - t, y: i, width: t, height: l)), with: .color(color))

            if showBottom {
                // Bottom-Left L bracket
                context.fill(Path(CGRect(x: i, y: h - i - t, width: l, height: t)), with: .color(color))
                context.fill(Path(CGRect(x: i, y: h - i - l, width: t, height: l)), with: .color(color))

                // Bottom-Right L bracket
                context.fill(Path(CGRect(x: w - i - l, y: h - i - t, width: l, height: t)), with: .color(color))
                context.fill(Path(CGRect(x: w - i - t, y: h - i - l, width: t, height: l)), with: .color(color))
            }
        }
        .allowsHitTesting(false)
    }
}
