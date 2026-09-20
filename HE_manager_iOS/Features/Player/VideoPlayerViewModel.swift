import Foundation
import AVFoundation
import SwiftUI

@Observable
public final class VideoPlayerViewModel {
    public var player: AVPlayer?
    public var isPlaying: Bool = false
    public var currentTime: Double = 0
    public var duration: Double = 0
    public var playbackRate: Float = 1.0
    public var isSeeking: Bool = false
    public var seekTime: Double = 0
    public var isBuffering: Bool = false
    public var errorMessage: String? = nil

    public let mediaId: Int
    public let title: String
    public let initialProgress: Int
    public let playlist: [Int]

    private var timeObserverToken: Any?
    private var lastSavedSeconds: Int = -1
    private let preferences: Preferences
    private var client: ApiClient {
        ApiClient(baseUrl: preferences.serverUrl, token: preferences.token)
    }

    public init(
        mediaId: Int,
        title: String,
        initialProgress: Int = 0,
        playlist: [Int] = [],
        preferences: Preferences = .shared
    ) {
        self.mediaId = mediaId
        self.title = title
        self.initialProgress = initialProgress
        self.playlist = playlist
        self.preferences = preferences
        self.playbackRate = preferences.defaultPlaybackSpeed
    }

    public func setupPlayer() {
        let streamUrlString = "\(preferences.serverUrl)/mobile/stream/\(mediaId)?token=\(preferences.token)"
        guard let url = URL(string: streamUrlString) else {
            errorMessage = "无效的视频播放流地址"
            return
        }

        let asset = AVURLAsset(url: url)
        let playerItem = AVPlayerItem(asset: asset)
        let avPlayer = AVPlayer(playerItem: playerItem)
        self.player = avPlayer

        // Audio session configuration for media playback
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)

        // Observe duration
        Task {
            if let dur = try? await asset.load(.duration) {
                let seconds = CMTimeGetSeconds(dur)
                if !seconds.isNaN && seconds > 0 {
                    await MainActor.run {
                        self.duration = seconds
                    }
                }
            }
        }

        // Seek to initial progress if needed
        if initialProgress > 0 && preferences.autoResume {
            let targetTime = CMTime(seconds: Double(initialProgress), preferredTimescale: 600)
            avPlayer.seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero)
        }

        // Periodic time observer (every 500ms)
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self, !self.isSeeking else { return }
            let secs = CMTimeGetSeconds(time)
            if !secs.isNaN {
                self.currentTime = secs
                self.checkAndSaveProgress(currentSecs: Int(secs))
            }
        }

        avPlayer.play()
        avPlayer.rate = playbackRate
        isPlaying = true
    }

    public func togglePlayPause() {
        guard let player = player else { return }
        if isPlaying {
            player.pause()
            isPlaying = false
            saveCurrentProgress()
        } else {
            player.play()
            player.rate = playbackRate
            isPlaying = true
        }
    }

    public func seek(to seconds: Double) {
        guard let player = player else { return }
        let target = CMTime(seconds: seconds, preferredTimescale: 600)
        player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            self?.currentTime = seconds
            self?.isSeeking = false
        }
    }

    public func skip(seconds: Double) {
        let target = max(0, min(duration, currentTime + seconds))
        seek(to: target)
    }

    public func setSpeed(_ speed: Float) {
        self.playbackRate = speed
        if isPlaying {
            player?.rate = speed
        }
    }

    public func cleanup() {
        saveCurrentProgress()
        if let token = timeObserverToken {
            player?.removeTimeObserver(token)
            timeObserverToken = nil
        }
        player?.pause()
        player = nil
    }

    private func checkAndSaveProgress(currentSecs: Int) {
        // Save progress every 10 seconds or when significantly changed
        if abs(currentSecs - lastSavedSeconds) >= 10 {
            lastSavedSeconds = currentSecs
            saveProgress(seconds: currentSecs)
        }
    }

    public func saveCurrentProgress() {
        let sec = Int(currentTime)
        if sec > 0 {
            saveProgress(seconds: sec)
        }
    }

    private func saveProgress(seconds: Int) {
        Task {
            try? await client.saveProgress(mediaId: mediaId, progress: seconds, duration: Int(duration))
        }
    }
}
