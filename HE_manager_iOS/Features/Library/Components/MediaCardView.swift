import SwiftUI

// MARK: - Helper Functions (identical to Android Helpers.kt)

public func fakeCode(_ item: MediaItem) -> String {
    let prefix: String
    switch item.mediaType.lowercased() {
    case "video": prefix = "VID"
    case "manga": prefix = "MNG"
    case "image": prefix = "IMG"
    case "audio": prefix = "AUD"
    default: prefix = "MED"
    }
    let letters = ["A", "V", "X", "M"]
    let letter = letters[abs(item.id % 4)]
    let num = String(format: "%03d", abs(item.id) % 1000)
    return "\(prefix)-\(letter)\(num)"
}

public func cleanExtension(_ ext: String?) -> String {
    guard let raw = ext, !raw.isEmpty, raw != "null" else { return "" }
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    if trimmed == "dir" { return "" }
    return ".\(trimmed.replacingOccurrences(of: ".", with: "").uppercased())"
}

public func metaInline(_ item: MediaItem) -> String {
    var parts: [String] = []
    switch item.mediaType.lowercased() {
    case "video": parts.append("视频")
    case "manga": parts.append("漫画")
    case "image": parts.append("图片")
    case "audio": parts.append("音频")
    default: parts.append("媒体")
    }

    let ext = cleanExtension(item.extensionName)
    if !ext.isEmpty { parts.append(ext) }

    if item.duration > 0 {
        parts.append(formatDurationSec(item.duration))
    }
    if item.pageCount > 0 {
        parts.append("\(item.pageCount)P")
    }

    if item.viewStatus == "viewed" {
        parts.append("已看完")
    } else if item.viewStatus == "viewing" {
        if item.mediaType == "manga" && item.pageCount > 0 {
            let cur = min(item.progress + 1, item.pageCount)
            parts.append("第 \(cur) / \(item.pageCount) 页")
        } else if item.mediaType == "video" && item.progress > 0 {
            parts.append("看到 \(formatDurationSec(item.progress))")
        } else {
            parts.append("继续看")
        }
    } else {
        parts.append("未观看")
    }

    return parts.joined(separator: " · ")
}

public func formatDurationSec(_ seconds: Int) -> String {
    let s = max(0, seconds)
    let h = s / 3600
    let m = (s % 3600) / 60
    let sec = s % 60
    if h > 0 {
        return String(format: "%d:%02d:%02d", h, m, sec)
    } else {
        return String(format: "%d:%02d", m, sec)
    }
}

public func makeCoverUrl(serverUrl: String, token: String, coverPath: String) -> String? {
    guard !coverPath.isEmpty, coverPath != "null" else { return nil }
    guard let encoded = coverPath.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) else { return nil }
    return "\(serverUrl)/mobile/thumbnails/\(encoded)?token=\(token)"
}

// MARK: - 1. MediaCardLargeView (大图模式 - 对齐 Android MediaCardV2)

public struct MediaCardLargeView: View {
    public let item: MediaItem
    public let serverUrl: String
    public let token: String
    public let onToggleFavorite: () -> Void
    public let onSelect: () -> Void

    public init(
        item: MediaItem,
        serverUrl: String,
        token: String,
        onToggleFavorite: @escaping () -> Void,
        onSelect: @escaping () -> Void
    ) {
        self.item = item
        self.serverUrl = serverUrl
        self.token = token
        self.onToggleFavorite = onToggleFavorite
        self.onSelect = onSelect
    }

    private var coverUrl: String? {
        makeCoverUrl(serverUrl: serverUrl, token: token, coverPath: item.coverPath)
    }

    public var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 0) {
                // 16:10 宽幅封面区
                ZStack(alignment: .bottomTrailing) {
                    Color.clear
                        .aspectRatio(1.60, contentMode: .fit)
                        .overlay(
                            AsyncCoverImage(urlString: coverUrl, contentMode: .fill)
                        )
                        .clipped()

                    // HUD 工业取景角标 (Top-Left & Top-Right)
                    HudBracketsView(inset: 8, length: 12, thickness: 1.5, color: OPTheme.yellow)

                    // Top-Start: TypeChip
                    VStack {
                        HStack {
                            TypeChip(mediaType: item.mediaType)
                                .padding(10)
                            Spacer()
                            if item.favorite {
                                Image(systemName: "star.fill")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(OPTheme.yellow)
                                    .padding(8)
                                    .background(Color.black.opacity(0.6))
                                    .clipShape(Circle())
                                    .padding(8)
                            }
                        }
                        Spacer()
                    }

                    // Bottom-Start: Page count chip for manga
                    if item.mediaType == "manga" && item.pageCount > 0 {
                        HStack {
                            Text("\(item.pageCount)P")
                                .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                                .foregroundColor(OPTheme.opWhite)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(Color.black.opacity(0.75))
                                .clipShape(RoundedRectangle(cornerRadius: 3))
                                .padding(10)
                            Spacer()
                        }
                    }

                    // Bottom-End: Floating PLAY Button
                    FloatingPlayButton(
                        label: item.viewStatus == "viewing" ? "RESUME" : "PLAY",
                        action: onSelect
                    )
                    .padding(10)
                }

                // 底部信息条
                VStack(alignment: .leading, spacing: 7) {
                    // Code + Status row
                    HStack {
                        CodeChip(text: fakeCode(item), color: OPTheme.yellow)
                        Spacer()
                        if item.viewStatus == "viewing" {
                            StatusPill(label: "ONGOING", color: OPTheme.yellow)
                        } else if item.viewStatus == "viewed" {
                            StatusPill(label: "COMPLETED", color: OPTheme.online)
                        }
                    }

                    // Title
                    Text(item.title)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(OPTheme.opWhite)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .lineSpacing(2)

                    // Subtitle metadata
                    Text(metaInline(item))
                        .font(.system(size: 11.5, weight: .medium, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteMuted)
                        .lineLimit(1)

                    // Progress bar
                    if item.progress > 0 {
                        let total = item.duration > 0 ? item.duration : item.pageCount
                        if total > 0 {
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Rectangle()
                                        .fill(Color.black.opacity(0.5))
                                        .frame(height: 2.5)
                                    Rectangle()
                                        .fill(item.viewStatus == "viewed" ? OPTheme.online : OPTheme.yellow)
                                        .frame(
                                            width: geo.size.width * CGFloat(min(1.0, Double(item.progress) / Double(total))),
                                            height: 2.5
                                        )
                                }
                            }
                            .frame(height: 2.5)
                            .padding(.top, 2)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(OPTheme.panel)
            }
            .clipShape(CutCornerShape(cut: 14))
            .overlay(
                CutCornerShape(cut: 14)
                    .stroke(OPTheme.hairlineMid, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                onToggleFavorite()
            } label: {
                Label(item.favorite ? "取消收藏" : "加入收藏", systemImage: item.favorite ? "star.slash" : "star")
            }
        }
    }
}

// MARK: - 2. MediaGridTileView (三列海报瓦片 - 对齐 Android MediaGridTileV2)

public struct MediaGridTileView: View {
    public let item: MediaItem
    public let serverUrl: String
    public let token: String
    public let onToggleFavorite: () -> Void
    public let onSelect: () -> Void

    public init(
        item: MediaItem,
        serverUrl: String,
        token: String,
        onToggleFavorite: @escaping () -> Void,
        onSelect: @escaping () -> Void
    ) {
        self.item = item
        self.serverUrl = serverUrl
        self.token = token
        self.onToggleFavorite = onToggleFavorite
        self.onSelect = onSelect
    }

    private var coverUrl: String? {
        makeCoverUrl(serverUrl: serverUrl, token: token, coverPath: item.coverPath)
    }

    public var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 0) {
                // 0.74 比例封面
                Color.clear
                    .aspectRatio(0.74, contentMode: .fit)
                    .overlay(
                        AsyncCoverImage(urlString: coverUrl, contentMode: .fill)
                    )
                    .clipShape(CutCornerShape(cut: 6, tl: false, tr: false, br: false, bl: false))
                    .clipped()
                    .overlay(alignment: .topLeading) {
                        TypeChip(mediaType: item.mediaType)
                            .padding(4)
                    }
                    .overlay(alignment: .topTrailing) {
                        CornerSealShape()
                            .fill(OPTheme.yellow)
                            .frame(width: 6, height: 6)
                    }
                    .overlay(alignment: .bottomLeading) {
                        if item.favorite {
                            Image(systemName: "star.fill")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(OPTheme.yellow)
                                .padding(4)
                                .background(Color.black.opacity(0.7))
                                .clipShape(Circle())
                                .padding(4)
                        }
                    }
                    .overlay(alignment: .bottom) {
                        if item.progress > 0 {
                            let total = item.duration > 0 ? item.duration : item.pageCount
                            if total > 0 {
                                GeometryReader { geo in
                                    ZStack(alignment: .leading) {
                                        Rectangle()
                                            .fill(Color.black.opacity(0.6))
                                            .frame(height: 2)
                                        Rectangle()
                                            .fill(item.viewStatus == "viewed" ? OPTheme.online : OPTheme.yellow)
                                            .frame(
                                                width: geo.size.width * CGFloat(min(1.0, Double(item.progress) / Double(total))),
                                                height: 2
                                            )
                                    }
                                }
                                .frame(height: 2)
                            }
                        }
                    }

                // 标题与编号
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(OPTheme.opWhite)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .frame(height: 32, alignment: .topLeading)

                    CodeChip(text: fakeCode(item), color: OPTheme.opWhiteMuted)
                        .font(.system(size: 9.5))
                }
                .padding(6)
            }
            .padding(4)
            .background(OPTheme.panel)
            .clipShape(CutCornerShape(cut: 10))
            .overlay(
                CutCornerShape(cut: 10)
                    .stroke(OPTheme.hairlineMid, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                onToggleFavorite()
            } label: {
                Label(item.favorite ? "取消收藏" : "加入收藏", systemImage: item.favorite ? "star.slash" : "star")
            }
        }
    }
}

// MARK: - 3. MediaDetailRowView (详细列表行 - 对齐 Android MediaDetailRowV2)

public struct MediaDetailRowView: View {
    public let item: MediaItem
    public let serverUrl: String
    public let token: String
    public let onToggleFavorite: () -> Void
    public let onSelect: () -> Void

    public init(
        item: MediaItem,
        serverUrl: String,
        token: String,
        onToggleFavorite: @escaping () -> Void,
        onSelect: @escaping () -> Void
    ) {
        self.item = item
        self.serverUrl = serverUrl
        self.token = token
        self.onToggleFavorite = onToggleFavorite
        self.onSelect = onSelect
    }

    private var coverUrl: String? {
        makeCoverUrl(serverUrl: serverUrl, token: token, coverPath: item.coverPath)
    }

    public var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 12) {
                // 左侧 0.74 缩略图
                Color.clear
                    .frame(width: 82, height: 110)
                    .overlay(
                        AsyncCoverImage(urlString: coverUrl, contentMode: .fill)
                    )
                    .clipShape(CutCornerShape(cut: 6))
                    .overlay(alignment: .topTrailing) {
                        CornerSealShape()
                            .fill(OPTheme.yellow)
                            .frame(width: 6, height: 6)
                    }

                // 右侧信息
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        CodeChip(text: fakeCode(item), color: OPTheme.yellow)
                        Spacer()
                        if item.favorite {
                            Image(systemName: "star.fill")
                                .font(.system(size: 11))
                                .foregroundColor(OPTheme.yellow)
                        }
                    }

                    Text(item.title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(OPTheme.opWhite)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    Text(metaInline(item))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteMuted)
                        .lineLimit(1)

                    if item.progress > 0 {
                        let total = item.duration > 0 ? item.duration : item.pageCount
                        if total > 0 {
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Rectangle()
                                        .fill(Color.black.opacity(0.5))
                                        .frame(height: 2)
                                    Rectangle()
                                        .fill(item.viewStatus == "viewed" ? OPTheme.online : OPTheme.yellow)
                                        .frame(
                                            width: geo.size.width * CGFloat(min(1.0, Double(item.progress) / Double(total))),
                                            height: 2
                                        )
                                }
                            }
                            .frame(height: 2)
                        }
                    }
                }
                .padding(.trailing, 8)
            }
            .padding(8)
            .background(OPTheme.panel)
            .clipShape(CutCornerShape(cut: 10))
            .overlay(
                CutCornerShape(cut: 10)
                    .stroke(OPTheme.hairlineMid, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                onToggleFavorite()
            } label: {
                Label(item.favorite ? "取消收藏" : "加入收藏", systemImage: item.favorite ? "star.slash" : "star")
            }
        }
    }
}

// MARK: - Compatibility Alias
public typealias MediaCardView = MediaGridTileView
