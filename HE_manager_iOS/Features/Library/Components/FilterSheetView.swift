import SwiftUI

public struct FilterState: Equatable {
    public var mediaType: String = ""         // "" means all
    public var viewStatus: String = ""        // "" means all
    public var sourceSite: String = ""        // "" means all
    public var favoriteOnly: Bool = false
    public var selectedTags: Set<Int> = []
    public var sort: String = "date"          // date (added), date_asc, title, rating, progress

    public init() {}

    public var isFiltered: Bool {
        !mediaType.isEmpty || !viewStatus.isEmpty || !sourceSite.isEmpty || favoriteOnly || !selectedTags.isEmpty
    }

    public mutating func reset() {
        mediaType = ""
        viewStatus = ""
        sourceSite = ""
        favoriteOnly = false
        selectedTags = []
        sort = "date"
    }
}

public struct FilterSheetView: View {
    @Binding public var filter: FilterState
    public let availableTags: [TagItem]
    public let availableSources: [String]
    @Environment(\.dismiss) private var dismiss

    public var body: some View {
        NavigationStack {
            ZStack {
                OPTheme.void.ignoresSafeArea()

                VStack(spacing: 0) {
                    // 顶部黄色高光指示条
                    Rectangle()
                        .fill(OPTheme.yellow)
                        .frame(width: 140, height: 3)
                        .padding(.top, 8)

                    // 顶栏: // 筛选 / 重置
                    HStack {
                        HStack(spacing: 6) {
                            Text("//")
                                .font(.system(size: 15, weight: .bold, design: .monospaced))
                                .foregroundColor(OPTheme.yellow)
                            Text("筛选")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(OPTheme.opWhite)
                        }

                        Spacer()

                        Button {
                            filter.reset()
                        } label: {
                            Text("重置")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(OPTheme.yellow)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 12)

                    ScrollView {
                        VStack(alignment: .leading, spacing: 20) {
                            // Section 1: 媒体类型
                            sectionTitle("媒体类型")
                            FlowLayout(spacing: 8) {
                                FilterTab(label: "全部", active: filter.mediaType.isEmpty) {
                                    filter.mediaType = ""
                                }
                                FilterTab(label: "视频", active: filter.mediaType == "video") {
                                    filter.mediaType = "video"
                                }
                                FilterTab(label: "漫画", active: filter.mediaType == "manga") {
                                    filter.mediaType = "manga"
                                }
                                FilterTab(label: "图片", active: filter.mediaType == "image") {
                                    filter.mediaType = "image"
                                }
                                FilterTab(label: "音频", active: filter.mediaType == "audio") {
                                    filter.mediaType = "audio"
                                }
                            }

                            // Section 2: 观看状态
                            sectionTitle("观看状态")
                            FlowLayout(spacing: 8) {
                                FilterTab(label: "全部", active: filter.viewStatus.isEmpty && !filter.favoriteOnly) {
                                    filter.viewStatus = ""
                                    filter.favoriteOnly = false
                                }
                                FilterTab(label: "继续看", active: filter.viewStatus == "viewing") {
                                    filter.viewStatus = "viewing"
                                    filter.favoriteOnly = false
                                }
                                FilterTab(label: "收藏", active: filter.favoriteOnly) {
                                    filter.favoriteOnly = true
                                    filter.viewStatus = ""
                                }
                                FilterTab(label: "已看完", active: filter.viewStatus == "viewed") {
                                    filter.viewStatus = "viewed"
                                    filter.favoriteOnly = false
                                }
                            }

                            // Section 3: 来源
                            sectionTitle("来源")
                            FlowLayout(spacing: 8) {
                                FilterTab(label: "全部来源", active: filter.sourceSite.isEmpty) {
                                    filter.sourceSite = ""
                                }
                                FilterTab(label: "本地", active: filter.sourceSite == "local") {
                                    filter.sourceSite = "local"
                                }
                                FilterTab(label: "X", active: filter.sourceSite.lowercased() == "x") {
                                    filter.sourceSite = "x"
                                }
                                FilterTab(label: "wnacg", active: filter.sourceSite.lowercased() == "wnacg") {
                                    filter.sourceSite = "wnacg"
                                }
                                FilterTab(label: "ASMR", active: filter.sourceSite.lowercased() == "asmr") {
                                    filter.sourceSite = "asmr"
                                }
                                ForEach(availableSources.filter { !["x", "wnacg", "asmr", "local"].contains($0.lowercased()) }, id: \.self) { src in
                                    FilterTab(label: src.uppercased(), active: filter.sourceSite == src) {
                                        filter.sourceSite = src
                                    }
                                }
                            }

                            // Section 4: 排序
                            sectionTitle("排序")
                            FlowLayout(spacing: 8) {
                                FilterTab(label: "+ 最近添加", active: filter.sort == "date") {
                                    filter.sort = "date"
                                }
                                FilterTab(label: "↻ 最近打开", active: filter.sort == "opened") {
                                    filter.sort = "opened"
                                }
                                FilterTab(label: "★ 评分", active: filter.sort == "rating") {
                                    filter.sort = "rating"
                                }
                                FilterTab(label: "☰ 名称", active: filter.sort == "title") {
                                    filter.sort = "title"
                                }
                            }

                            // Section 5: 标签 (可选)
                            if !availableTags.isEmpty {
                                sectionTitle("标签")
                                FlowLayout(spacing: 6) {
                                    ForEach(availableTags.prefix(24)) { tag in
                                        let isSelected = filter.selectedTags.contains(tag.id)
                                        FilterTab(label: "#\(tag.name)", active: isSelected) {
                                            if isSelected {
                                                filter.selectedTags.remove(tag.id)
                                            } else {
                                                filter.selectedTags.insert(tag.id)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(minLength: 24)
                        }
                        .padding(.horizontal, 20)
                    }

                    // 底部大黄按钮: ➜ 应用 · APPLY
                    Button {
                        dismiss()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.right")
                                .font(.system(size: 14, weight: .bold))
                            Text("应用 · APPLY")
                                .font(.system(size: 14, weight: .heavy, design: .monospaced))
                                .tracking(1.2)
                        }
                        .foregroundColor(OPTheme.onYellow)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(
                            CutCornerShape(cut: 10)
                                .fill(OPTheme.yellow)
                        )
                        .clipShape(CutCornerShape(cut: 10))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }
            }
            .navigationBarHidden(true)
        }
    }

    private func sectionTitle(_ title: String) -> some View {
        HStack(spacing: 6) {
            Text("//")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(OPTheme.yellow)
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(OPTheme.opWhite)
        }
    }
}

// MARK: - FlowLayout Helper
public struct FlowLayout: Layout {
    public var spacing: CGFloat

    public init(spacing: CGFloat = 8) {
        self.spacing = spacing
    }

    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var height: CGFloat = 0
        var rowX: CGFloat = 0
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if rowX + size.width > width && rowX > 0 {
                height += rowHeight + spacing
                rowX = 0
                rowHeight = 0
            }
            rowX += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        height += rowHeight
        return CGSize(width: width, height: height)
    }

    public func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX && x > bounds.minX {
                y += rowHeight + spacing
                x = bounds.minX
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
