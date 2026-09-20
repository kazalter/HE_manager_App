import Foundation
import SwiftUI

@Observable
public final class AudioPlayerViewModel {
    public var state = AudioPlayerUiState()
    public var showLyrics: Bool = false
    public var showTracklist: Bool = false

    public let mediaId: Int
    private let preferences: Preferences
    private var client: ApiClient {
        ApiClient(baseUrl: preferences.serverUrl, token: preferences.token)
    }

    private var sleepTimerTask: Task<Void, Never>?
    private var lastSavedSec: Int = -1

    public init(mediaId: Int, preferences: Preferences = .shared) {
        self.mediaId = mediaId
        self.preferences = preferences
    }

    public func loadWork(initialProgressSeconds: Int = 0) async {
        state.loading = true
        state.error = nil

        do {
            async let metaFetch = client.getMediaDetail(mediaId: mediaId)
            async let tracksFetch = client.getAudioTracks(mediaId: mediaId)

            let (meta, tracks) = try await (metaFetch, tracksFetch)
            state.workTitle = meta.title
            state.coverUrl = client.thumbnailStreamUrl(coverPath: meta.coverPath)
            state.tracks = tracks
            state.durationMs = Int64(meta.duration * 1000)

            // Calculate starting track and offset from initial progress
            var targetIndex = 0
            var offsetSec: Double = Double(initialProgressSeconds)
            for (idx, track) in tracks.enumerated() {
                let trackDur = track.durationSec ?? 0
                if trackDur > 0 && offsetSec >= trackDur && idx < tracks.count - 1 {
                    offsetSec -= trackDur
                    targetIndex = idx + 1
                } else {
                    targetIndex = idx
                    break
                }
            }

            state.currentIndex = targetIndex
            state.loading = false

            // Bind background service callbacks
            setupServiceCallbacks()

            // Play track
            playTrack(at: targetIndex, seekToSeconds: offsetSec)
        } catch {
            state.loading = false
            state.error = error.localizedDescription
        }
    }

    private func setupServiceCallbacks() {
        let service = BackgroundAudioService.shared

        service.onProgressUpdate = { [weak self] current, dur in
            guard let self = self else { return }
            self.state.positionMs = Int64(current * 1000)
            if dur > 0 {
                self.state.durationMs = Int64(dur * 1000)
            }
            self.updateLockScreenInfo(current: current, duration: dur)
            self.checkAndSaveCumulativeProgress()
        }

        service.onTrackDidFinish = { [weak self] in
            guard let self = self else { return }
            if self.state.sleepAfterCurrentTrack {
                self.pause()
                self.state.sleepAfterCurrentTrack = false
                return
            }
            self.nextTrack()
        }

        service.onRemoteCommand = { [weak self] cmd in
            guard let self = self else { return }
            switch cmd {
            case .play: self.resume()
            case .pause: self.pause()
            case .togglePlayPause: self.togglePlayPause()
            case .next: self.nextTrack()
            case .previous: self.previousTrack()
            case .seek(let pos): self.seek(to: pos)
            }
        }
    }

    public func playTrack(at index: Int, seekToSeconds: Double = 0) {
        guard index >= 0 && index < state.tracks.count else { return }
        state.currentIndex = index
        let track = state.tracks[index]

        let streamUrlStr = "\(preferences.serverUrl)/audio/\(mediaId)/track/\(track.index)?token=\(preferences.token)"
        guard let url = URL(string: streamUrlStr) else { return }

        BackgroundAudioService.shared.play(url: url)
        if seekToSeconds > 0 {
            BackgroundAudioService.shared.seek(to: seekToSeconds)
        }
        state.isPlaying = true

        // Load lyrics for this track
        Task {
            let lines = try? await client.getAudioLyrics(mediaId: mediaId, trackIndex: track.index)
            await MainActor.run {
                self.state.lyrics = lines ?? []
            }
        }
    }

    public func togglePlayPause() {
        if state.isPlaying {
            pause()
        } else {
            resume()
        }
    }

    public func resume() {
        BackgroundAudioService.shared.resume()
        state.isPlaying = true
    }

    public func pause() {
        BackgroundAudioService.shared.pause()
        state.isPlaying = false
        saveCurrentProgress()
    }

    public func nextTrack() {
        if state.currentIndex + 1 < state.tracks.count {
            playTrack(at: state.currentIndex + 1)
        } else if state.loopMode == .all {
            playTrack(at: 0)
        } else {
            pause()
        }
    }

    public func previousTrack() {
        if state.positionMs > 3000 {
            seek(to: 0)
        } else if state.currentIndex > 0 {
            playTrack(at: state.currentIndex - 1)
        } else {
            seek(to: 0)
        }
    }

    public func seek(to seconds: Double) {
        BackgroundAudioService.shared.seek(to: seconds)
        state.positionMs = Int64(seconds * 1000)
    }

    public func setSleepTimer(minutes: Int) {
        sleepTimerTask?.cancel()
        state.sleepAfterCurrentTrack = false

        if minutes <= 0 {
            state.sleepTimerDeadline = nil
            return
        }

        let deadline = Date().addingTimeInterval(TimeInterval(minutes * 60))
        state.sleepTimerDeadline = deadline

        sleepTimerTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(minutes * 60) * 1_000_000_000)
            if !Task.isCancelled {
                await MainActor.run {
                    self.pause()
                    self.state.sleepTimerDeadline = nil
                }
            }
        }
    }

    public func setSleepAfterCurrentTrack() {
        sleepTimerTask?.cancel()
        state.sleepTimerDeadline = nil
        state.sleepAfterCurrentTrack.toggle()
    }

    private func updateLockScreenInfo(current: Double, duration: Double) {
        guard let track = state.currentTrack else { return }
        BackgroundAudioService.shared.updateNowPlayingInfo(
            title: track.title,
            workTitle: state.workTitle,
            currentTime: current,
            duration: duration,
            rate: state.isPlaying ? state.speed : 0
        )
    }

    private func checkAndSaveCumulativeProgress() {
        let currentCumSec = cumulativeSeconds()
        if abs(currentCumSec - lastSavedSec) >= 10 {
            lastSavedSec = currentCumSec
            saveCumulativeProgress(seconds: currentCumSec)
        }
    }

    public func saveCurrentProgress() {
        let sec = cumulativeSeconds()
        saveCumulativeProgress(seconds: sec)
    }

    private func cumulativeSeconds() -> Int {
        var cum: Double = 0
        for i in 0..<state.currentIndex {
            if i < state.tracks.count {
                cum += state.tracks[i].durationSec ?? 0
            }
        }
        cum += Double(state.positionMs) / 1000.0
        return Int(cum)
    }

    private func saveCumulativeProgress(seconds: Int) {
        Task {
            try? await client.saveProgress(mediaId: mediaId, progress: seconds)
        }
    }
}
