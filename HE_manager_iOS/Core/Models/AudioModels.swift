import Foundation

public struct AudioTrack: Identifiable, Codable, Hashable, Sendable {
    public var id: Int { index }
    public let index: Int
    public let title: String
    public let rel: String
    public let durationSec: Double?
    public let hasLyrics: Bool

    enum CodingKeys: String, CodingKey {
        case index
        case title
        case rel
        case durationSec = "duration"
        case hasLyrics = "lyrics"
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.index = try container.decodeIfPresent(Int.self, forKey: .index) ?? 1
        self.title = try container.decodeIfPresent(String.self, forKey: .title) ?? "Track \(self.index)"
        self.rel = try container.decodeIfPresent(String.self, forKey: .rel) ?? ""
        if let d = try container.decodeIfPresent(Double.self, forKey: .durationSec), d > 0 {
            self.durationSec = d
        } else {
            self.durationSec = nil
        }
        if let lyricsStr = try container.decodeIfPresent(String.self, forKey: .hasLyrics) {
            self.hasLyrics = !lyricsStr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        } else if let lyricsBool = try container.decodeIfPresent(Bool.self, forKey: .hasLyrics) {
            self.hasLyrics = lyricsBool
        } else {
            self.hasLyrics = false
        }
    }

    public init(index: Int, title: String, rel: String = "", durationSec: Double? = nil, hasLyrics: Bool = false) {
        self.index = index
        self.title = title
        self.rel = rel
        self.durationSec = durationSec
        self.hasLyrics = hasLyrics
    }
}

public struct LyricLine: Identifiable, Codable, Hashable, Sendable {
    public var id: Double { timeSec }
    public let timeSec: Double
    public let text: String

    enum CodingKeys: String, CodingKey {
        case timeSec = "t"
        case text
    }

    public init(timeSec: Double, text: String) {
        self.timeSec = timeSec
        self.text = text
    }
}

public enum LoopMode: String, CaseIterable, Sendable {
    case off = "OFF"
    case all = "ALL"
    case one = "ONE"
    case shuffle = "SHUFFLE"
}

public struct AudioPlayerUiState: Sendable {
    public var workTitle: String = ""
    public var coverUrl: String? = nil
    public var tracks: [AudioTrack] = []
    public var currentIndex: Int = 0
    public var isPlaying: Bool = false
    public var positionMs: Int64 = 0
    public var durationMs: Int64 = 0
    public var loopMode: LoopMode = .off
    public var speed: Float = 1.0
    public var lyrics: [LyricLine] = []
    public var activeLyricIndex: Int = -1
    public var sleepTimerDeadline: Date? = nil
    public var sleepAfterCurrentTrack: Bool = false
    public var loading: Bool = true
    public var error: String? = nil

    public init() {}

    public var currentTrack: AudioTrack? {
        guard currentIndex >= 0 && currentIndex < tracks.count else { return nil }
        return tracks[currentIndex]
    }

    public var sleepTimerArmed: Bool {
        sleepTimerDeadline != nil || sleepAfterCurrentTrack
    }
}
