import Foundation
import SwiftUI

public enum LibraryViewMode: String, CaseIterable, Codable {
    case large = "large"
    case grid = "grid"
    case detail = "detail"

    public var title: String {
        switch self {
        case .large: return "大图"
        case .grid: return "三列"
        case .detail: return "详细"
        }
    }

    public var icon: String {
        switch self {
        case .large: return "square.grid.2x2"
        case .grid: return "square.grid.3x3"
        case .detail: return "list.bullet"
        }
    }
}

public enum ViewerReadingMode: String, CaseIterable, Codable {
    case page = "PAGE"
    case scroll = "SCROLL"
}

@Observable
public final class Preferences {
    public static let shared = Preferences()

    private let defaults: UserDefaults

    private enum Keys {
        static let serverUrl = "he_server_url"
        static let token = "he_token"
        static let serverHistory = "he_server_history"
        static let galleryColumns = "he_gallery_columns"
        static let viewMode = "he_view_mode"
        static let viewerReadingMode = "he_viewer_reading_mode"
        static let coverCacheSizeMb = "he_cover_cache_size_mb"
        static let defaultPlaybackSpeed = "he_default_playback_speed"
        static let autoResume = "he_auto_resume"
    }

    public static let defaultGalleryColumns: Int = 3
    public static let minGalleryColumns: Int = 2
    public static let maxGalleryColumns: Int = 5
    public static let maxServerHistory: Int = 6

    public var viewMode: LibraryViewMode {
        didSet { defaults.set(viewMode.rawValue, forKey: Keys.viewMode) }
    }

    public var viewerReadingMode: ViewerReadingMode {
        didSet { defaults.set(viewerReadingMode.rawValue, forKey: Keys.viewerReadingMode) }
    }

    public var serverUrl: String {
        didSet { defaults.set(serverUrl, forKey: Keys.serverUrl) }
    }

    public var token: String {
        didSet { defaults.set(token, forKey: Keys.token) }
    }

    public var serverHistory: [String] {
        didSet { defaults.set(serverHistory, forKey: Keys.serverHistory) }
    }

    public var galleryColumns: Int {
        didSet { defaults.set(galleryColumns, forKey: Keys.galleryColumns) }
    }

    public var coverCacheSizeMb: Int {
        didSet { defaults.set(coverCacheSizeMb, forKey: Keys.coverCacheSizeMb) }
    }

    public var defaultPlaybackSpeed: Float {
        didSet { defaults.set(defaultPlaybackSpeed, forKey: Keys.defaultPlaybackSpeed) }
    }

    public var autoResume: Bool {
        didSet { defaults.set(autoResume, forKey: Keys.autoResume) }
    }

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.serverUrl = defaults.string(forKey: Keys.serverUrl) ?? ""
        self.token = defaults.string(forKey: Keys.token) ?? ""
        self.serverHistory = defaults.stringArray(forKey: Keys.serverHistory) ?? []
        let savedCols = defaults.integer(forKey: Keys.galleryColumns)
        self.galleryColumns = savedCols > 0 ? savedCols : Self.defaultGalleryColumns
        if let modeRaw = defaults.string(forKey: Keys.viewMode), let mode = LibraryViewMode(rawValue: modeRaw) {
            self.viewMode = mode
        } else {
            self.viewMode = .large
        }
        if let rRaw = defaults.string(forKey: Keys.viewerReadingMode), let rMode = ViewerReadingMode(rawValue: rRaw) {
            self.viewerReadingMode = rMode
        } else {
            self.viewerReadingMode = .scroll
        }
        let savedCache = defaults.integer(forKey: Keys.coverCacheSizeMb)
        self.coverCacheSizeMb = savedCache > 0 ? savedCache : 256
        let savedSpeed = defaults.float(forKey: Keys.defaultPlaybackSpeed)
        self.defaultPlaybackSpeed = savedSpeed > 0 ? savedSpeed : 1.0
        self.autoResume = defaults.object(forKey: Keys.autoResume) != nil ? defaults.bool(forKey: Keys.autoResume) : true
    }

    public var isLoggedIn: Bool {
        !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !serverUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    public func saveCredentials(serverUrl: String, token: String) {
        let trimmedServer = serverUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedToken = token.trimmingCharacters(in: .whitespacesAndNewlines)

        self.serverUrl = trimmedServer
        self.token = trimmedToken

        var history = self.serverHistory.filter { $0 != trimmedServer }
        history.insert(trimmedServer, at: 0)
        if history.count > Self.maxServerHistory {
            history = Array(history.prefix(Self.maxServerHistory))
        }
        self.serverHistory = history
    }

    public func removeServerHistory(_ server: String) {
        let target = server.trimmingCharacters(in: .whitespacesAndNewlines)
        self.serverHistory.removeAll { $0 == target }
    }

    public func clearToken() {
        self.token = ""
    }

    public func logout() {
        self.token = ""
    }
}
