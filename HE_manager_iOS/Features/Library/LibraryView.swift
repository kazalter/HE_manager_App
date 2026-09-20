import SwiftUI

public enum ActiveViewer: Identifiable {
    case reader(MediaItem, [MediaItem])
    case video(MediaItem, [Int])
    case audio(MediaItem)

    public var id: String {
        switch self {
        case .reader(let item, _): return "reader_\(item.id)"
        case .video(let item, _): return "video_\(item.id)"
        case .audio(let item): return "audio_\(item.id)"
        }
    }
}

public struct LibraryView: View {
    @State private var viewModel = LibraryViewModel()
    @Bindable var preferences = Preferences.shared

    @State private var isDrawerOpen: Bool = false
    @State private var isFilterSheetOpen: Bool = false
    @State private var isSettingsOpen: Bool = false
    @State private var isCreatorsOpen: Bool = false
    @State private var activeViewer: ActiveViewer? = nil

    // Pinch-to-zoom columns for grid mode
    @GestureState private var pinchScale: CGFloat = 1.0
    @State private var baseColumns: Int = 3

    public let onLogout: () -> Void

    public init(onLogout: @escaping () -> Void) {
        self.onLogout = onLogout
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                OPTheme.appBackgroundGradient

                VStack(spacing: 0) {
                    // 1. Top HUD Header (对齐 Android LibraryHeaderV2)
                    topHeader

                    // 2. Search & Filter Panel (对齐 Android SearchAndFilterPanelV2)
                    searchAndFilterPanel

                    // 3. Main Content Area
                    if viewModel.isLoading && viewModel.allItems.isEmpty {
                        loadingView
                    } else if let error = viewModel.errorMessage, viewModel.allItems.isEmpty {
                        errorView(error)
                    } else if viewModel.filteredItems.isEmpty {
                        emptyView
                    } else {
                        contentView
                    }
                }

                // Left Navigation Drawer Overlay
                if isDrawerOpen {
                    DrawerView(
                        serverUrl: preferences.serverUrl,
                        totalCount: viewModel.totalCount,
                        onSelectContinue: {
                            viewModel.filter.viewStatus = "viewing"
                            viewModel.filter.mediaType = ""
                            viewModel.filter.favoriteOnly = false
                        },
                        onSelectStarred: {
                            viewModel.filter.favoriteOnly = true
                            viewModel.filter.mediaType = ""
                            viewModel.filter.viewStatus = ""
                        },
                        onSelectCreators: {
                            isCreatorsOpen = true
                        },
                        onSelectSettings: {
                            isSettingsOpen = true
                        },
                        onRefresh: {
                            Task { await viewModel.loadData() }
                        },
                        onDisconnect: {
                            onLogout()
                        },
                        onClose: {
                            withAnimation(.easeOut(duration: 0.2)) {
                                isDrawerOpen = false
                            }
                        }
                    )
                    .transition(.move(edge: .leading))
                    .zIndex(100)
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $isFilterSheetOpen) {
                FilterSheetView(
                    filter: $viewModel.filter,
                    availableTags: viewModel.allTags,
                    availableSources: viewModel.availableSources
                )
            }
            .sheet(isPresented: $isSettingsOpen) {
                SettingsView(onDisconnect: onLogout)
            }
            .navigationDestination(isPresented: $isCreatorsOpen) {
                CreatorsView(onSelectMedia: handleMediaSelection)
            }
            .fullScreenCover(item: $activeViewer) { viewer in
                switch viewer {
                case .reader(let item, let playlistItems):
                    MangaReaderView(mediaItem: item, playlistItems: playlistItems)
                case .video(let item, let playlist):
                    VideoPlayerView(mediaItem: item, playlist: playlist)
                case .audio(let item):
                    AudioPlayerView(mediaItem: item)
                }
            }
            .task {
                baseColumns = preferences.galleryColumns
                await viewModel.loadData()
            }
        }
    }

    // MARK: - 1. Top HUD Header (对齐 Android LibraryHeaderV2)
    private var topHeader: some View {
        HStack(spacing: 12) {
            // 侧边栏按钮
            IconBtn4(icon: "line.3.horizontal") {
                withAnimation(.easeOut(duration: 0.2)) {
                    isDrawerOpen.toggle()
                }
            }

            // 工业双语标题
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 5) {
                    Text("//")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.yellow)
                    Text("媒体库")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(OPTheme.opWhite)
                    Text("Library")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteMuted)
                }

                Text("媒体库")
                    .font(.system(size: 21, weight: .heavy))
                    .foregroundColor(OPTheme.opWhite)

                Text("LIBRARY · OPERATOR ARCHIVE")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(OPTheme.opWhiteMuted)
                    .tracking(1.0)
            }

            Spacer()

            // 视图模式切换按钮
            Menu {
                Button {
                    cycleViewMode(to: .large)
                } label: {
                    Label("大图", systemImage: preferences.viewMode == .large ? "checkmark" : "")
                }
                Button {
                    cycleViewMode(to: .grid)
                } label: {
                    Label("三列", systemImage: preferences.viewMode == .grid ? "checkmark" : "")
                }
                Button {
                    cycleViewMode(to: .detail)
                } label: {
                    Label("详细", systemImage: preferences.viewMode == .detail ? "checkmark" : "")
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(OPTheme.ink)
                        .frame(width: 36, height: 36)
                        .overlay(
                            Circle()
                                .stroke(OPTheme.hairlineMid, lineWidth: 1)
                        )

                    Image(systemName: preferences.viewMode.icon)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(OPTheme.opWhite)
                }
            } primaryAction: {
                // 点击直接在三种视图间循环切换
                switch preferences.viewMode {
                case .large: cycleViewMode(to: .grid)
                case .grid: cycleViewMode(to: .detail)
                case .detail: cycleViewMode(to: .large)
                }
            }

            // 刷新按钮或加载指示器
            if viewModel.isLoading {
                ZStack {
                    Circle()
                        .fill(OPTheme.ink)
                        .frame(width: 36, height: 36)
                        .overlay(Circle().stroke(OPTheme.hairlineMid, lineWidth: 1))
                    ProgressView()
                        .tint(OPTheme.yellow)
                        .scaleEffect(0.8)
                }
            } else {
                IconBtn4(icon: "arrow.clockwise") {
                    Task { await viewModel.loadData() }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 8)
    }

    private func cycleViewMode(to mode: LibraryViewMode) {
        withAnimation(.easeInOut(duration: 0.18)) {
            preferences.viewMode = mode
        }
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()
    }

    // MARK: - 2. Search & Filter Panel (对齐 Android SearchAndFilterPanelV2)
    private var searchAndFilterPanel: some View {
        VStack(spacing: 10) {
            // 切角搜索框
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(OPTheme.opWhiteMuted)
                    .font(.system(size: 14))

                TextField("搜索标题、作者、文件夹", text: $viewModel.searchText)
                    .font(.system(size: 13.5))
                    .foregroundColor(OPTheme.opWhite)
                    .autocorrectionDisabled()

                if !viewModel.searchText.isEmpty {
                    Button {
                        viewModel.searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(OPTheme.opWhiteMuted)
                            .font(.system(size: 14))
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(OPTheme.panel)
            .clipShape(CutCornerShape(cut: 8))
            .overlay(
                CutCornerShape(cut: 8)
                    .stroke(OPTheme.hairlineMid, lineWidth: 1)
            )

            // 水平横向滑动分类药丸
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    // 全部
                    FilterTab(
                        label: "全部",
                        active: viewModel.filter.mediaType.isEmpty && viewModel.filter.viewStatus.isEmpty && !viewModel.filter.favoriteOnly
                    ) {
                        viewModel.filter.mediaType = ""
                        viewModel.filter.viewStatus = ""
                        viewModel.filter.favoriteOnly = false
                    }

                    // 视频
                    FilterTab(
                        label: "视频",
                        active: viewModel.filter.mediaType == "video"
                    ) {
                        toggleMediaType("video")
                    }

                    // 漫画
                    FilterTab(
                        label: "漫画",
                        active: viewModel.filter.mediaType == "manga"
                    ) {
                        toggleMediaType("manga")
                    }

                    // 图片
                    FilterTab(
                        label: "图片",
                        active: viewModel.filter.mediaType == "image"
                    ) {
                        toggleMediaType("image")
                    }

                    // 音频
                    FilterTab(
                        label: "音频",
                        active: viewModel.filter.mediaType == "audio"
                    ) {
                        toggleMediaType("audio")
                    }

                    // 继续看
                    FilterTab(
                        label: "继续看",
                        active: viewModel.filter.viewStatus == "viewing"
                    ) {
                        toggleViewStatus("viewing")
                    }

                    // 收藏
                    FilterTab(
                        label: "收藏",
                        active: viewModel.filter.favoriteOnly
                    ) {
                        viewModel.filter.favoriteOnly.toggle()
                    }
                }
            }

            // 过滤条展开按钮: // FILTER 全部 · 全部 ➜
            Button {
                isFilterSheetOpen = true
            } label: {
                HStack(spacing: 8) {
                    Text("//")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.yellow)

                    Text("FILTER")
                        .font(.system(size: 11, weight: .heavy, design: .monospaced))
                        .foregroundColor(OPTheme.opWhite)
                        .tracking(1.5)

                    Text(filterSummaryLabel)
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundColor(OPTheme.opWhiteSoft)
                        .lineLimit(1)

                    Spacer()

                    if activeFilterCount > 0 {
                        Text("\(activeFilterCount)")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(OPTheme.onYellow)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(OPTheme.yellow)
                            .clipShape(CutCornerShape(cut: 4))
                    }

                    Image(systemName: "arrow.right")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(OPTheme.yellow)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(OPTheme.panel)
                .clipShape(CutCornerShape(cut: 8))
                .overlay(
                    CutCornerShape(cut: 8)
                        .stroke(OPTheme.hairlineMid, lineWidth: 1)
                )
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }

    private func toggleMediaType(_ type: String) {
        if viewModel.filter.mediaType == type {
            viewModel.filter.mediaType = ""
        } else {
            viewModel.filter.mediaType = type
            viewModel.filter.viewStatus = ""
            viewModel.filter.favoriteOnly = false
        }
    }

    private func toggleViewStatus(_ status: String) {
        if viewModel.filter.viewStatus == status {
            viewModel.filter.viewStatus = ""
        } else {
            viewModel.filter.viewStatus = status
            viewModel.filter.mediaType = ""
            viewModel.filter.favoriteOnly = false
        }
    }

    private var filterSummaryLabel: String {
        let mediaLabel: String
        switch viewModel.filter.mediaType {
        case "video": mediaLabel = "视频"
        case "manga": mediaLabel = "漫画"
        case "image": mediaLabel = "图片"
        case "audio": mediaLabel = "音频"
        default: mediaLabel = "全部"
        }

        let statusLabel: String
        if viewModel.filter.favoriteOnly {
            statusLabel = "收藏"
        } else {
            switch viewModel.filter.viewStatus {
            case "viewing": statusLabel = "继续看"
            case "viewed": statusLabel = "已看完"
            case "unviewed": statusLabel = "未看"
            default: statusLabel = "全部"
            }
        }

        return "\(mediaLabel) · \(statusLabel)"
    }

    private var activeFilterCount: Int {
        var count = 0
        if !viewModel.filter.mediaType.isEmpty { count += 1 }
        if !viewModel.filter.viewStatus.isEmpty { count += 1 }
        if !viewModel.filter.sourceSite.isEmpty { count += 1 }
        if viewModel.filter.favoriteOnly { count += 1 }
        if !viewModel.filter.selectedTags.isEmpty { count += 1 }
        return count
    }

    // MARK: - 3. Content View (动态响应 3 种视图模式)
    private var contentView: some View {
        ScrollView {
            switch preferences.viewMode {
            case .large:
                // 大图模式 (单列 16:10 宽卡片)
                LazyVStack(spacing: 14) {
                    ForEach(viewModel.filteredItems) { item in
                        MediaCardLargeView(
                            item: item,
                            serverUrl: preferences.serverUrl,
                            token: preferences.token,
                            onToggleFavorite: {
                                viewModel.toggleFavorite(for: item)
                            },
                            onSelect: {
                                handleMediaSelection(item)
                            }
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

            case .grid:
                // 三列海报瓦片模式 (0.74 比例)
                let cols = Array(repeating: GridItem(.flexible(), spacing: 8), count: 3)
                LazyVGrid(columns: cols, spacing: 8) {
                    ForEach(viewModel.filteredItems) { item in
                        MediaGridTileView(
                            item: item,
                            serverUrl: preferences.serverUrl,
                            token: preferences.token,
                            onToggleFavorite: {
                                viewModel.toggleFavorite(for: item)
                            },
                            onSelect: {
                                handleMediaSelection(item)
                            }
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

            case .detail:
                // 详细列表模式
                LazyVStack(spacing: 10) {
                    ForEach(viewModel.filteredItems) { item in
                        MediaDetailRowView(
                            item: item,
                            serverUrl: preferences.serverUrl,
                            token: preferences.token,
                            onToggleFavorite: {
                                viewModel.toggleFavorite(for: item)
                            },
                            onSelect: {
                                handleMediaSelection(item)
                            }
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
        }
        .refreshable {
            await viewModel.loadData()
        }
    }

    private func handleMediaSelection(_ item: MediaItem) {
        let matchingItems = viewModel.filteredItems
            .filter { $0.mediaType.lowercased() == item.mediaType.lowercased() }
        let playlistItems = matchingItems.isEmpty ? [item] : matchingItems
        let playlistIds = playlistItems.map { $0.id }

        switch item.mediaType.lowercased() {
        case "manga", "image":
            activeViewer = .reader(item, playlistItems)
        case "video":
            activeViewer = .video(item, playlistIds)
        case "audio":
            activeViewer = .audio(item)
        default:
            activeViewer = .video(item, playlistIds)
        }
    }

    // MARK: - Loading, Error & Empty States
    private var loadingView: some View {
        VStack(spacing: 16) {
            Spacer()
            ProgressView()
                .tint(OPTheme.yellow)
                .scaleEffect(1.3)
            Text("// SYNCING MEDIA INDEX...")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(OPTheme.yellowDim)
            Spacer()
        }
    }

    private var emptyView: some View {
        VStack(spacing: 14) {
            Spacer()
            Image(systemName: "tray")
                .font(.system(size: 36))
                .foregroundColor(OPTheme.opWhiteMuted)
            Text("// NO MEDIA FOUND IN CURRENT FILTER")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(OPTheme.opWhiteMuted)

            Button {
                viewModel.filter.reset()
                viewModel.searchText = ""
            } label: {
                Text("RESET FILTER / 重置筛选")
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(OPTheme.yellow)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(OPTheme.panel)
                    .clipShape(CutCornerShape(cut: 6))
            }
            Spacer()
        }
    }

    private func errorView(_ error: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 32))
                .foregroundColor(OPTheme.danger)
            Text("// ERR: \(error)")
                .font(.system(size: 13, weight: .medium, design: .monospaced))
                .foregroundColor(OPTheme.opWhite)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            YellowCTA(title: "RETRY CONNECTION", size: .medium) {
                Task {
                    await viewModel.loadData()
                }
            }
            Spacer()
        }
    }
}
