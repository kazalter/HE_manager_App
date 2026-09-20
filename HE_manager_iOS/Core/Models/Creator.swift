import Foundation

public struct Creator: Identifiable, Codable, Hashable, Sendable {
    public var id: String { key }
    public let kind: String           // "x" | "artist"
    public let key: String            // unified id: "x:<screen_name>" | "a:<artist>"
    public let screenName: String     // X only; empty for manga artists
    public let displayName: String
    public let mediaCount: Int
    public let postsKnown: Int
    public let postsPending: Int
    public let coverPath: String

    enum CodingKeys: String, CodingKey {
        case kind
        case key
        case screenName = "screen_name"
        case displayName = "display_name"
        case mediaCount = "media_count"
        case postsKnown = "posts_known"
        case postsPending = "posts_pending"
        case coverPath = "cover_path"
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.kind = try container.decodeIfPresent(String.self, forKey: .kind) ?? ""
        self.key = try container.decodeIfPresent(String.self, forKey: .key) ?? ""
        self.screenName = try container.decodeIfPresent(String.self, forKey: .screenName) ?? ""
        self.displayName = try container.decodeIfPresent(String.self, forKey: .displayName) ?? ""
        self.mediaCount = try container.decodeIfPresent(Int.self, forKey: .mediaCount) ?? 0
        self.postsKnown = try container.decodeIfPresent(Int.self, forKey: .postsKnown) ?? 0
        self.postsPending = try container.decodeIfPresent(Int.self, forKey: .postsPending) ?? 0
        self.coverPath = try container.decodeIfPresent(String.self, forKey: .coverPath) ?? ""
    }

    public init(
        kind: String = "",
        key: String = "",
        screenName: String = "",
        displayName: String = "",
        mediaCount: Int = 0,
        postsKnown: Int = 0,
        postsPending: Int = 0,
        coverPath: String = ""
    ) {
        self.kind = kind
        self.key = key
        self.screenName = screenName
        self.displayName = displayName
        self.mediaCount = mediaCount
        self.postsKnown = postsKnown
        self.postsPending = postsPending
        self.coverPath = coverPath
    }

    public var label: String {
        if !displayName.isEmpty { return displayName }
        if !screenName.isEmpty { return "@\(screenName)" }
        return key
    }
}

public struct CreatorDetail: Codable, Sendable {
    public let creator: Creator
    public let media: [MediaItem]

    enum CodingKeys: String, CodingKey {
        case creator
        case media
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.creator = try container.decodeIfPresent(Creator.self, forKey: .creator) ?? Creator()
        self.media = try container.decodeIfPresent([MediaItem].self, forKey: .media) ?? []
    }

    public init(creator: Creator = Creator(), media: [MediaItem] = []) {
        self.creator = creator
        self.media = media
    }
}
