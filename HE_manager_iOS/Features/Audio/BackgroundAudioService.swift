import Foundation
import AVFoundation
import MediaPlayer
import UIKit

public final class BackgroundAudioService: NSObject {
    public static let shared = BackgroundAudioService()

    private var player: AVPlayer?
    private var timeObserverToken: Any?

    public var onProgressUpdate: ((Double, Double) -> Void)?
    public var onTrackDidFinish: (() -> Void)?
    public var onRemoteCommand: ((RemoteCommand) -> Void)?

    public enum RemoteCommand {
        case play, pause, togglePlayPause, next, previous, seek(Double)
    }

    private override init() {
        super.init()
        setupAudioSession()
        setupRemoteCommandCenter()
    }

    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, policy: .longFormAudio)
            try session.setActive(true)
        } catch {
            print("Failed to set audio session category: \(error)")
        }
    }

    private func setupRemoteCommandCenter() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.onRemoteCommand?(.play)
            return .success
        }

        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.onRemoteCommand?(.pause)
            return .success
        }

        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.onRemoteCommand?(.togglePlayPause)
            return .success
        }

        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            self?.onRemoteCommand?(.next)
            return .success
        }

        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            self?.onRemoteCommand?(.previous)
            return .success
        }

        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.onRemoteCommand?(.seek(event.positionTime))
            return .success
        }
    }

    public func play(url: URL) {
        let item = AVPlayerItem(url: url)
        if player == nil {
            player = AVPlayer(playerItem: item)
        } else {
            player?.replaceCurrentItem(with: item)
        }

        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(itemDidFinishPlaying), name: .AVPlayerItemDidPlayToEndTime, object: item)

        setupTimeObserver()
        player?.play()
    }

    public func resume() {
        player?.play()
    }

    public func pause() {
        player?.pause()
    }

    public func seek(to seconds: Double) {
        let target = CMTime(seconds: seconds, preferredTimescale: 600)
        player?.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    public func setRate(_ rate: Float) {
        player?.rate = rate
    }

    public func updateNowPlayingInfo(
        title: String,
        workTitle: String,
        coverImage: UIImage? = nil,
        currentTime: Double,
        duration: Double,
        rate: Float
    ) {
        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = title
        info[MPMediaItemPropertyAlbumTitle] = workTitle
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        info[MPMediaItemPropertyPlaybackDuration] = duration
        info[MPNowPlayingInfoPropertyPlaybackRate] = rate

        if let image = coverImage {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func setupTimeObserver() {
        if let token = timeObserverToken {
            player?.removeTimeObserver(token)
            timeObserverToken = nil
        }

        let interval = CMTime(seconds: 0.25, preferredTimescale: 600)
        timeObserverToken = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self, let currentItem = self.player?.currentItem else { return }
            let currentSec = CMTimeGetSeconds(time)
            let durationSec = CMTimeGetSeconds(currentItem.duration)
            if !currentSec.isNaN {
                self.onProgressUpdate?(currentSec, durationSec.isNaN ? 0 : durationSec)
            }
        }
    }

    @objc private func itemDidFinishPlaying() {
        onTrackDidFinish?()
    }
}
