import SwiftUI

public struct MangaReaderView: View {
    public let mediaItem: MediaItem
    public let playlistItems: [MediaItem]

    @State private var readingMode: ViewerReadingMode = .scroll
    @State private var currentPage: Int = 0
    @State private var totalPages: Int = 1
    @State private var showControls: Bool = true
    @State private var controlsTimer: Task<Void, Never>? = nil
    @State private var pageJump: ReaderPageJump?
    @State private var readerReady: Bool
    @StateObject private var progressWriter = ReaderProgressWriter()

    @Bindable var preferences = Preferences.shared
    @Environment(\.dismiss) private var dismiss

    public init(mediaItem: MediaItem, playlistItems: [MediaItem] = []) {
        self.mediaItem = mediaItem
        self.playlistItems = playlistItems
        let image = mediaItem.mediaType.lowercased() == "image"
        let count = image ? max(1, playlistItems.count) : max(1, mediaItem.pageCount)
        let page = image ? (playlistItems.firstIndex(where: { $0.id == mediaItem.id }) ?? 0)
            : max(0, min(mediaItem.progress, count - 1))
        _totalPages = State(initialValue: count)
        _currentPage = State(initialValue: page)
        _readingMode = State(initialValue: Preferences.shared.viewerReadingMode)
        _readerReady = State(initialValue: image || mediaItem.pageCount > 0)
    }

    private var isImage: Bool {
        mediaItem.mediaType.lowercased() == "image"
    }

    private var effectiveItems: [MediaItem] {
        if isImage {
            return playlistItems.isEmpty ? [mediaItem] : playlistItems
        }
        return [mediaItem]
    }

    private var currentTitle: String {
        if isImage {
            if currentPage >= 0 && currentPage < effectiveItems.count {
                return effectiveItems[currentPage].title
            }
            return mediaItem.title
        }
        return mediaItem.title
    }

    public var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black.ignoresSafeArea()

                // Reader Content
                if !readerReady {
                    ProgressView().tint(OPTheme.yellow)
                } else if readingMode == .page {
                    PagedReaderView(
                        totalPages: totalPages,
                        currentPage: $currentPage,
                        pageUrlBuilder: pageUrl,
                        onSingleTap: toggleControls
                    )
                } else {
                    WebtoonReaderView(
                        totalPages: totalPages,
                        currentPage: $currentPage,
                        jump: pageJump,
                        pageUrlBuilder: pageUrl,
                        onSingleTap: toggleControls
                    )
                }

                // Controls Overlay
                if showControls {
                    VStack(spacing: 0) {
                        topBar
                            .padding(.top, max(geo.safeAreaInsets.top, 20))
                            .background(
                                Color.black.opacity(0.88)
                                    .ignoresSafeArea(edges: .top)
                            )

                        Spacer()

                        if totalPages > 1 {
                            bottomBar
                                .padding(.bottom, max(geo.safeAreaInsets.bottom, 16))
                                .background(
                                    Color.black.opacity(0.88)
                                    .ignoresSafeArea(edges: .bottom)
                                )
                        }
                    }
                    .transition(.opacity)
                }
            }
            .ignoresSafeArea()
            .statusBarHidden(!showControls)
        }
        .task {
            if !isImage {
                let client = ApiClient(baseUrl: preferences.serverUrl, token: preferences.token)
                if let serverPages = try? await client.getMangaTotalPages(mediaId: mediaItem.id) {
                    guard !Task.isCancelled else { return }
                    totalPages = max(1, serverPages)
                    if !readerReady {
                        currentPage = max(0, min(mediaItem.progress, totalPages - 1))
                    } else if currentPage >= totalPages {
                        requestPage(totalPages - 1)
                    }
                }
            }
            guard !Task.isCancelled else { return }
            readerReady = true
            prefetchAhead()
            scheduleControlsTimer()
        }
        .onChange(of: currentPage) { _, newPage in
            if !isImage {
                saveProgress(page: newPage)
            }
            prefetchAhead()
        }
        .onChange(of: readingMode) { _, _ in prefetchAhead() }
        .onDisappear {
            controlsTimer?.cancel()
            if !isImage && readerReady { saveProgress(page: currentPage, flush: true) }
        }
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(OPTheme.opWhite)
                    .padding(9)
                    .background(Color.white.opacity(0.12))
                    .clipShape(Circle())
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(currentTitle)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(OPTheme.opWhite)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    Text(isImage ? "IMAGE" : "MANGA")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.yellow)

                    Text("//")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.yellowDim)

                    Text("\(currentPage + 1) / \(totalPages)")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteMuted)
                }
            }

            Spacer()

            // Mode Toggle (单页 PAGE vs 连续滚动 SCROLL)
            Menu {
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        readingMode = .page
                        preferences.viewerReadingMode = .page
                    }
                } label: {
                    Label("单页阅读 (PAGE)", systemImage: readingMode == .page ? "checkmark" : "")
                }
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        readingMode = .scroll
                        preferences.viewerReadingMode = .scroll
                    }
                } label: {
                    Label("连续滚动 (SCROLL)", systemImage: readingMode == .scroll ? "checkmark" : "")
                }
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: readingMode == .page ? "book.fill" : "scroll.fill")
                        .font(.system(size: 11))
                    Text(readingMode == .page ? "单页" : "滚动")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 8, weight: .bold))
                }
                .foregroundColor(OPTheme.onYellow)
                .padding(.horizontal, 9)
                .padding(.vertical, 6)
                .background(OPTheme.yellow)
                .clipShape(CutCornerShape(cut: 4))
            } primaryAction: {
                withAnimation(.easeInOut(duration: 0.18)) {
                    readingMode = readingMode == .page ? .scroll : .page
                    preferences.viewerReadingMode = readingMode
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private var bottomBar: some View {
        VStack(spacing: 8) {
            HStack {
                Text("\(currentPage + 1)")
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                    .foregroundColor(OPTheme.yellow)
                Text("/")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(OPTheme.opWhiteMuted)
                Text("\(totalPages)")
                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                    .foregroundColor(OPTheme.opWhiteSoft)
                Spacer()

                Text(isImage ? "连续图集 · \(readingMode == .page ? "单页翻页" : "垂直条漫")" : "连载漫画 · \(readingMode == .page ? "单页翻页" : "垂直条漫")")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(OPTheme.opWhiteMuted)
            }

            Slider(
                value: Binding(
                    get: { Double(currentPage) },
                    set: { requestPage(Int($0)) }
                ),
                in: 0...Double(max(0, totalPages - 1)),
                step: 1
            )
            .tint(OPTheme.yellow)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) {
            showControls.toggle()
        }
        if showControls {
            scheduleControlsTimer()
        } else {
            controlsTimer?.cancel()
        }
    }

    private func scheduleControlsTimer() {
        controlsTimer?.cancel()
        controlsTimer = Task {
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if !Task.isCancelled {
                withAnimation(.easeInOut(duration: 0.2)) {
                    showControls = false
                }
            }
        }
    }

    private func pageUrl(page: Int) -> String {
        if isImage {
            let target = (page >= 0 && page < effectiveItems.count) ? effectiveItems[page] : mediaItem
            return "\(preferences.serverUrl)/mobile/stream/\(target.id)?token=\(preferences.token)"
        }
        return "\(preferences.serverUrl)/mobile/manga/\(mediaItem.id)/page/\(page)?token=\(preferences.token)"
    }

    private func requestPage(_ page: Int) {
        currentPage = max(0, min(page, totalPages - 1))
        pageJump = ReaderPageJump(page: currentPage)
    }

    private func saveProgress(page: Int, flush: Bool = false) {
        guard readerReady else { return }
        progressWriter.submit(client: ApiClient(baseUrl: preferences.serverUrl, token: preferences.token),
                              mediaID: mediaItem.id, page: page, total: totalPages, flush: flush)
    }

    private func prefetchAhead() {
        // The continuous reader owns bounded, cancellable prefetch requests.
        guard readingMode == .page else { return }
        let aheadCount = 4
        let start = currentPage + 1
        let end = min(totalPages - 1, currentPage + aheadCount)
        guard start <= end else { return }

        for p in start...end {
            let url = pageUrl(page: p)
            Task {
                if await ImageCacheManager.shared.image(for: url) == nil {
                    guard let nsUrl = URL(string: url) else { return }
                    if let (data, _) = try? await URLSession.shared.data(from: nsUrl),
                       let img = UIImage(data: data) {
                        await ImageCacheManager.shared.store(image: img, data: data, for: url)
                    }
                }
            }
        }
    }
}

/// Coalesce rapid page changes and serialize writes so an older request cannot
/// finish after a newer one. The finite worker also drains the exit snapshot.
@MainActor
private final class ReaderProgressWriter: ObservableObject {
    private struct Snapshot {
        let client: ApiClient
        let mediaID: Int
        let page: Int
        let total: Int
    }
    private var pending: Snapshot?
    private var worker: Task<Void, Never>?
    private var flushRequested = false

    func submit(client: ApiClient, mediaID: Int, page: Int, total: Int, flush: Bool) {
        pending = Snapshot(client: client, mediaID: mediaID, page: page, total: total)
        flushRequested = flushRequested || flush
        guard worker == nil else { return }
        worker = Task {
            while pending != nil {
                if !flushRequested { try? await Task.sleep(nanoseconds: 400_000_000) }
                guard let snapshot = pending else { break }
                pending = nil
                flushRequested = false
                try? await snapshot.client.saveProgress(mediaId: snapshot.mediaID,
                                                         progress: snapshot.page, duration: snapshot.total)
            }
            worker = nil
        }
    }
}
