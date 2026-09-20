import Foundation
import SwiftUI

@Observable
public final class LibraryViewModel {
    public var allItems: [MediaItem] = []
    public var allTags: [TagItem] = []
    public var filter = FilterState()
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

    public var availableSources: [String] {
        let sites = allItems.compactMap { $0.sourceSite }.filter { !$0.isEmpty }
        return Array(Set(sites)).sorted()
    }

    public var totalCount: Int { allItems.count }
    public var videoCount: Int { allItems.filter { $0.mediaType == "video" }.count }
    public var mangaCount: Int { allItems.filter { $0.mediaType == "manga" }.count }
    public var imageCount: Int { allItems.filter { $0.mediaType == "image" }.count }
    public var audioCount: Int { allItems.filter { $0.mediaType == "audio" }.count }

    /// Pure client-side instantaneous filtering & sorting
    public var filteredItems: [MediaItem] {
        var items = allItems

        // 1. Search Query
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !query.isEmpty {
            items = items.filter { item in
                item.title.lowercased().contains(query) ||
                item.tags.contains { $0.name.lowercased().contains(query) } ||
                (item.sourceSite?.lowercased().contains(query) ?? false)
            }
        }

        // 2. Media Type Filter
        if !filter.mediaType.isEmpty {
            items = items.filter { $0.mediaType == filter.mediaType }
        }

        // 3. View Status Filter
        if !filter.viewStatus.isEmpty {
            items = items.filter { $0.viewStatus == filter.viewStatus }
        }

        // 4. Source Site Filter
        if !filter.sourceSite.isEmpty {
            items = items.filter { $0.sourceSite?.lowercased() == filter.sourceSite.lowercased() }
        }

        // 5. Favorite Only
        if filter.favoriteOnly {
            items = items.filter { $0.favorite }
        }

        // 6. Tags Filter
        if !filter.selectedTags.isEmpty {
            items = items.filter { item in
                let itemTagIds = Set(item.tags.map { $0.id })
                return !filter.selectedTags.isDisjoint(with: itemTagIds)
            }
        }

        // 7. Sort
        switch filter.sort {
        case "date_asc":
            items.sort { $0.createdAt < $1.createdAt }
        case "title":
            items.sort { $0.title.localizedCompare($1.title) == .orderedAscending }
        case "rating":
            items.sort { $0.rating > $1.rating }
        case "progress":
            items.sort { $0.progress > $1.progress }
        default: // "date" (desc)
            items.sort { $0.createdAt > $1.createdAt }
        }

        return items
    }

    public func loadData() async {
        guard preferences.isLoggedIn else { return }
        isLoading = true
        errorMessage = nil

        do {
            async let mediaFetch = client.getMedia(sort: "date")
            async let tagsFetch = client.getTags()

            let (media, tags) = try await (mediaFetch, tagsFetch)
            self.allItems = media
            self.allTags = tags
            self.isLoading = false
        } catch {
            self.isLoading = false
            self.errorMessage = error.localizedDescription
        }
    }

    public func toggleFavorite(for item: MediaItem) {
        guard let index = allItems.firstIndex(where: { $0.id == item.id }) else { return }
        let targetFavorite = !item.favorite
        // Optimistic UI update
        allItems[index].favorite = targetFavorite

        Task {
            do {
                let updated = try await client.toggleFavorite(mediaId: item.id, favorite: targetFavorite)
                await MainActor.run {
                    if let idx = self.allItems.firstIndex(where: { $0.id == item.id }) {
                        self.allItems[idx] = updated
                    }
                }
            } catch {
                // Revert on failure
                await MainActor.run {
                    if let idx = self.allItems.firstIndex(where: { $0.id == item.id }) {
                        self.allItems[idx].favorite = !targetFavorite
                    }
                }
            }
        }
    }

    public func addTag(mediaId: Int, tagName: String) {
        Task {
            do {
                let updated = try await client.addTag(mediaId: mediaId, tagName: tagName)
                await MainActor.run {
                    if let idx = self.allItems.firstIndex(where: { $0.id == mediaId }) {
                        self.allItems[idx] = updated
                    }
                }
            } catch {
                // failed
            }
        }
    }

    public func deleteMedia(item: MediaItem) {
        let id = item.id
        allItems.removeAll { $0.id == id }
        Task {
            do {
                try await client.deleteMedia(mediaId: id)
            } catch {
                // revert or alert
            }
        }
    }
}
