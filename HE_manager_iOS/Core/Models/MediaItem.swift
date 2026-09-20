import Foundation

public struct MediaItem: Identifiable, Codable, Hashable, Sendable {
    public let id: Int
    public var title: String
    public let mediaType: String
    public let extensionName: String
    public let coverPath: String
    public let duration: Int
    public let pageCount: Int
    public var progress: Int
    public var rating: Int
    public var favorite: Bool
    public var viewStatus: String
    public let isMissing: Bool
    public let sourceSite: String?
    public let createdAt: String
    public let lastOpenedAt: String
    public var tags: [TagItem]

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case mediaType = "media_type"
        case extensionName = "extension"
        case coverPath = "cover_path"
        case duration
        case pageCount = "page_count"
        case progress
        case rating
        case favorite
        case viewStatus = "view_status"
        case isMissing = "is_missing"
        case sourceSite = "source_site"
        case createdAt = "created_at"
        case lastOpenedAt = "last_opened_at"
        case tags
    }

    public init(
        id: Int = 0,
        title: String = "Untitled",
        mediaType: String = "",
        extensionName: String = "",
        coverPath: String = "",
        duration: Int = 0,
        pageCount: Int = 0,
        progress: Int = 0,
        rating: Int = 0,
        favorite: Bool = false,
        viewStatus: String = "unviewed",
        isMissing: Bool = false,
        sourceSite: String? = nil,
        createdAt: String = "",
        lastOpenedAt: String = "",
        tags: [TagItem] = []
    ) {
        self.id = id
        self.title = title
        self.mediaType = mediaType
        self.extensionName = extensionName
        self.coverPath = coverPath
        self.duration = duration
        self.pageCount = pageCount
        self.progress = progress
        self.rating = rating
        self.favorite = favorite
        self.viewStatus = viewStatus
        self.isMissing = isMissing
        self.sourceSite = sourceSite
        self.createdAt = createdAt
        self.lastOpenedAt = lastOpenedAt
        self.tags = tags
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decodeIfPresent(Int.self, forKey: .id) ?? 0
        self.title = try container.decodeIfPresent(String.self, forKey: .title) ?? "Untitled"
        self.mediaType = try container.decodeIfPresent(String.self, forKey: .mediaType) ?? ""
        self.extensionName = try container.decodeIfPresent(String.self, forKey: .extensionName) ?? ""
        self.coverPath = try container.decodeIfPresent(String.self, forKey: .coverPath) ?? ""
        self.duration = try container.decodeIfPresent(Int.self, forKey: .duration) ?? 0
        self.pageCount = try container.decodeIfPresent(Int.self, forKey: .pageCount) ?? 0
        self.progress = try container.decodeIfPresent(Int.self, forKey: .progress) ?? 0
        self.rating = try container.decodeIfPresent(Int.self, forKey: .rating) ?? 0
        self.favorite = try container.decodeIfPresent(Bool.self, forKey: .favorite) ?? false
        self.viewStatus = try container.decodeIfPresent(String.self, forKey: .viewStatus) ?? "unviewed"
        self.isMissing = try container.decodeIfPresent(Bool.self, forKey: .isMissing) ?? false
        let site = try container.decodeIfPresent(String.self, forKey: .sourceSite)?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.sourceSite = (site == nil || site?.isEmpty == true) ? nil : site
        self.createdAt = try container.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
        self.lastOpenedAt = try container.decodeIfPresent(String.self, forKey: .lastOpenedAt) ?? ""
        self.tags = try container.decodeIfPresent([TagItem].self, forKey: .tags) ?? []
    }
}
