import SwiftUI

public struct AngularPanel<Content: View>: View {
    public let cut: CGFloat
    public let tl: Bool
    public let tr: Bool
    public let br: Bool
    public let bl: Bool
    public let background: Color
    public let yellowCorner: Bool
    public let hairline: Bool
    public let content: Content

    public init(
        cut: CGFloat = 12,
        tl: Bool = false,
        tr: Bool = true,
        br: Bool = false,
        bl: Bool = true,
        background: Color = OPTheme.panel,
        yellowCorner: Bool = false,
        hairline: Bool = true,
        @ViewBuilder content: () -> Content
    ) {
        self.cut = cut
        self.tl = tl
        self.tr = tr
        self.br = br
        self.bl = bl
        self.background = background
        self.yellowCorner = yellowCorner
        self.hairline = hairline
        self.content = content()
    }

    public var body: some View {
        let shape = CutCornerShape(cut: cut, tl: tl, tr: tr, br: br, bl: bl)

        content
            .background(
                shape
                    .fill(background)
            )
            .overlay(
                Group {
                    if hairline {
                        shape
                            .stroke(OPTheme.hairlineMid, lineWidth: 1)
                    }
                }
            )
            .overlay(alignment: .topTrailing) {
                if yellowCorner && tr {
                    CornerSealShape()
                        .fill(OPTheme.yellow)
                        .frame(width: cut, height: cut)
                }
            }
            .clipShape(shape)
    }
}
