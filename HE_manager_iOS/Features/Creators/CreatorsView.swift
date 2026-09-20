import SwiftUI

@Observable
public final class CreatorsViewModel {
    public var creators: [Creator] = []
    public var selectedKind: String = "all" // all, artist, x
    public var searchText: String = ""
    public var isLoading: Bool = false
    public var errorMessage: String? = nil

    private let preferences: Preferences
    private var client: ApiClient {
        ApiClient(baseUrl: preferences.serverUrl, token: preferences.token)
    }

    public init(preferences: Preferences = .shared) {
        self.preferences = preferences
    }

    public var filteredCreators: [Creator] {
        var list = creators
        if selectedKind != "all" {
            list = list.filter { $0.kind == selectedKind }
        }
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !q.isEmpty {
            list = list.filter {
                $0.displayName.lowercased().contains(q) ||
                $0.screenName.lowercased().contains(q) ||
                $0.key.lowercased().contains(q)
            }
        }
        return list
    }

    public func loadCreators() async {
        isLoading = true
        errorMessage = nil
        do {
            let list = try await client.getCreators(typeFilter: selectedKind)
            self.creators = list
            self.isLoading = false
        } catch {
            self.isLoading = false
            self.errorMessage = error.localizedDescription
        }
    }
}

public struct CreatorsView: View {
    @State private var viewModel = CreatorsViewModel()
    @Bindable var preferences = Preferences.shared
    public let onSelectMedia: (MediaItem) -> Void

    public var body: some View {
        ZStack {
            OPTheme.void.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Search & Filter Bar
                VStack(spacing: 10) {
                    // Search field
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(OPTheme.yellowDim)
                            .font(.system(size: 13))

                        TextField("Search creators...", text: $viewModel.searchText)
                            .font(.system(size: 13, design: .monospaced))
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
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(OPTheme.panel)
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                    // Kind Segmented Filters
                    HStack(spacing: 8) {
                        kindFilterButton(title: "ALL", kind: "all")
                        kindFilterButton(title: "ARTISTS // 画师", kind: "artist")
                        kindFilterButton(title: "X CREATORS // X推主", kind: "x")
                    }
                }
                .padding(12)
                .background(OPTheme.ink)

                if viewModel.isLoading && viewModel.creators.isEmpty {
                    Spacer()
                    ProgressView().tint(OPTheme.yellow)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                            ForEach(viewModel.filteredCreators) { creator in
                                NavigationLink {
                                    CreatorDetailView(creatorKey: creator.key, onSelectMedia: onSelectMedia)
                                } label: {
                                    creatorCard(creator)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(12)
                    }
                    .refreshable {
                        await viewModel.loadCreators()
                    }
                }
            }
        }
        .navigationTitle("CREATORS")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadCreators()
        }
    }

    private func kindFilterButton(title: String, kind: String) -> some View {
        let isSelected = viewModel.selectedKind == kind
        return Button {
            viewModel.selectedKind = kind
            Task {
                await viewModel.loadCreators()
            }
        } label: {
            Text(title)
                .font(.system(size: 11, weight: isSelected ? .bold : .medium, design: .monospaced))
                .foregroundColor(isSelected ? OPTheme.onYellow : OPTheme.opWhiteSoft)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity)
                .background(isSelected ? OPTheme.yellow : OPTheme.panel)
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
        .buttonStyle(.plain)
    }

    private func creatorCard(_ creator: Creator) -> some View {
        let coverUrl = creator.coverPath.isEmpty ? nil : "\(preferences.serverUrl)/mobile/thumbnails/\(creator.coverPath.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? "")?token=\(preferences.token)"

        return AngularPanel(cut: 8, hairline: true) {
            VStack(alignment: .leading, spacing: 8) {
                ZStack(alignment: .topTrailing) {
                    AsyncCoverImage(urlString: coverUrl, contentMode: .fill)
                        .frame(height: 100)
                        .clipped()

                    Text(creator.kind == "x" ? "X" : "ART")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(creator.kind == "x" ? OPTheme.cyan : OPTheme.yellow)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.7))
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                        .padding(6)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(creator.label)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(OPTheme.opWhite)
                        .lineLimit(1)

                    HStack {
                        Text("\(creator.mediaCount) WORKS")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(OPTheme.yellowDim)
                        Spacer()
                    }
                }
                .padding(.horizontal, 10)
                .padding(.bottom, 10)
            }
        }
    }
}

public struct CreatorDetailView: View {
    public let creatorKey: String
    public let onSelectMedia: (MediaItem) -> Void

    @State private var detail: CreatorDetail? = nil
    @State private var isLoading: Bool = true
    @State private var errorMessage: String? = nil
    @Bindable var preferences = Preferences.shared

    public var body: some View {
        ZStack {
            OPTheme.void.ignoresSafeArea()

            if isLoading {
                ProgressView().tint(OPTheme.yellow)
            } else if let error = errorMessage {
                Text(error).foregroundColor(OPTheme.danger)
            } else if let detail = detail {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Creator Hero Header
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(OPTheme.surfaceAlt)
                                    .frame(width: 50, height: 50)
                                    .overlay(
                                        Text(String(detail.creator.label.prefix(1)))
                                            .font(.system(size: 20, weight: .bold, design: .monospaced))
                                            .foregroundColor(OPTheme.yellow)
                                    )

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(detail.creator.label)
                                        .font(.system(size: 18, weight: .heavy))
                                        .foregroundColor(OPTheme.opWhite)

                                    if !detail.creator.screenName.isEmpty {
                                        Text("@\(detail.creator.screenName)")
                                            .font(.system(size: 12, design: .monospaced))
                                            .foregroundColor(OPTheme.cyan)
                                    }
                                }
                            }

                            HStack(spacing: 16) {
                                statItem(label: "TOTAL WORKS", value: "\(detail.media.count)")
                                if detail.creator.postsKnown > 0 {
                                    statItem(label: "POSTS KNOWN", value: "\(detail.creator.postsKnown)")
                                }
                            }
                            .padding(.top, 4)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(OPTheme.panel)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .padding(.horizontal, 12)

                        Text("WORKS COLLECTION // 关联作品")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(OPTheme.yellowDim)
                            .padding(.horizontal, 16)

                        // Media Grid
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
                            ForEach(detail.media) { item in
                                MediaCardView(
                                    item: item,
                                    serverUrl: preferences.serverUrl,
                                    token: preferences.token,
                                    onToggleFavorite: {},
                                    onSelect: {
                                        onSelectMedia(item)
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, 12)
                    }
                    .padding(.vertical, 12)
                }
            }
        }
        .navigationTitle(detail?.creator.label ?? "CREATOR")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadDetail()
        }
    }

    private func statItem(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundColor(OPTheme.opWhiteMuted)
            Text(value)
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundColor(OPTheme.yellow)
        }
    }

    private func loadDetail() async {
        isLoading = true
        errorMessage = nil
        do {
            let client = ApiClient(baseUrl: preferences.serverUrl, token: preferences.token)
            let res = try await client.getCreatorDetail(key: creatorKey)
            self.detail = res
            self.isLoading = false
        } catch {
            self.isLoading = false
            self.errorMessage = error.localizedDescription
        }
    }
}
