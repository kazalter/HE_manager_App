import SwiftUI
import AVKit
import MediaPlayer

public struct VideoPlayerContainerView: UIViewControllerRepresentable {
    public let player: AVPlayer?
    public var gravity: AVLayerVideoGravity = .resizeAspect

    public func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = false
        controller.videoGravity = gravity
        controller.view.backgroundColor = .black
        return controller
    }

    public func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        uiViewController.player = player
        uiViewController.videoGravity = gravity
    }
}

public struct VideoPlayerView: View {
    public let mediaItem: MediaItem
    public let playlist: [Int]

    @State private var viewModel: VideoPlayerViewModel
    @State private var showControls: Bool = true
    @State private var controlsTimer: Task<Void, Never>? = nil
    @State private var showSpeedSheet: Bool = false
    @State private var videoGravity: AVLayerVideoGravity = .resizeAspect

    // HUD feedback states
    @State private var brightnessLevel: CGFloat = UIScreen.main.brightness
    @State private var showBrightnessHud: Bool = false
    @State private var showSeekHud: Bool = false
    @State private var seekHudDelta: Double = 0
    @State private var doubleTapFeedbackLeft: Bool = false
    @State private var doubleTapFeedbackRight: Bool = false

    @Environment(\.dismiss) private var dismiss

    public init(mediaItem: MediaItem, playlist: [Int] = []) {
        self.mediaItem = mediaItem
        self.playlist = playlist
        _viewModel = State(initialValue: VideoPlayerViewModel(
            mediaId: mediaItem.id,
            title: mediaItem.title,
            initialProgress: mediaItem.progress,
            playlist: playlist
        ))
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Video Player Layer
            VideoPlayerContainerView(player: viewModel.player, gravity: videoGravity)
                .ignoresSafeArea()

            // Gesture Detector Layer
            gestureOverlay

            // Double Tap Ripple Animations
            doubleTapOverlays

            // Floating Gesture HUD (Brightness / Seek feedback)
            gestureHuds

            // Controls Overlay
            if showControls {
                VStack {
                    topBar
                    Spacer()
                    centerPlayButton
                    Spacer()
                    bottomBar
                }
                .transition(.opacity)
            }
        }
        .statusBarHidden(!showControls)
        .onAppear {
            viewModel.setupPlayer()
            scheduleControlsTimer()
        }
        .onDisappear {
            viewModel.cleanup()
        }
        .confirmationDialog("PLAYBACK SPEED", isPresented: $showSpeedSheet, titleVisibility: .visible) {
            Button("0.75x") { viewModel.setSpeed(0.75) }
            Button("1.0x (Normal)") { viewModel.setSpeed(1.0) }
            Button("1.25x") { viewModel.setSpeed(1.25) }
            Button("1.5x") { viewModel.setSpeed(1.5) }
            Button("2.0x") { viewModel.setSpeed(2.0) }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: - Gestures Overlay
    private var gestureOverlay: some View {
        GeometryReader { geo in
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { location in
                    if location.x < geo.size.width / 2 {
                        // Double tap left: Rewind 10s
                        viewModel.skip(seconds: -10)
                        triggerDoubleTapFeedback(isLeft: true)
                    } else {
                        // Double tap right: Forward 10s
                        viewModel.skip(seconds: 10)
                        triggerDoubleTapFeedback(isLeft: false)
                    }
                }
                .onTapGesture(count: 1) {
                    toggleControls()
                }
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            let width = geo.size.width
                            let startX = value.startLocation.x
                            let translationY = value.translation.height
                            let translationX = value.translation.width

                            if abs(translationX) > abs(translationY) && abs(translationX) > 20 {
                                // Horizontal Seek Scrub
                                showSeekHud = true
                                let delta = Double(translationX / 10)
                                seekHudDelta = delta
                            } else if abs(translationY) > 15 {
                                // Vertical Drag
                                if startX < width / 2 {
                                    // Left side: Brightness
                                    let delta = -translationY / 300
                                    let newB = max(0, min(1, brightnessLevel + delta))
                                    UIScreen.main.brightness = newB
                                    brightnessLevel = newB
                                    showBrightnessHud = true
                                }
                            }
                        }
                        .onEnded { value in
                            if showSeekHud {
                                viewModel.skip(seconds: seekHudDelta)
                                showSeekHud = false
                            }
                            showBrightnessHud = false
                        }
                )
        }
    }

    // MARK: - Top Bar
    private var topBar: some View {
        HStack(spacing: 12) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(OPTheme.opWhite)
                    .padding(10)
                    .background(Color.black.opacity(0.65))
                    .clipShape(Circle())
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(mediaItem.title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(OPTheme.opWhite)
                    .lineLimit(1)
            }

            Spacer()

            // Aspect Ratio Switcher
            Button {
                videoGravity = videoGravity == .resizeAspect ? .resizeAspectFill : .resizeAspect
            } label: {
                Image(systemName: videoGravity == .resizeAspect ? "aspectratio" : "aspectratio.fill")
                    .font(.system(size: 16))
                    .foregroundColor(OPTheme.opWhite)
                    .padding(8)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            LinearGradient(colors: [Color.black.opacity(0.85), Color.clear], startPoint: .top, endPoint: .bottom)
        )
    }

    // MARK: - Center Play Button
    private var centerPlayButton: some View {
        Button {
            viewModel.togglePlayPause()
            scheduleControlsTimer()
        } label: {
            Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 38))
                .foregroundColor(OPTheme.yellow)
                .padding(20)
                .background(Color.black.opacity(0.6))
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(OPTheme.yellow.opacity(0.4), lineWidth: 2)
                )
        }
    }

    // MARK: - Bottom Bar
    private var bottomBar: some View {
        VStack(spacing: 6) {
            // Time Labels
            HStack {
                Text(formatTime(viewModel.currentTime))
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(OPTheme.yellow)
                Text("/")
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(OPTheme.opWhiteMuted)
                Text(formatTime(viewModel.duration))
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(OPTheme.opWhiteSoft)

                Spacer()

                // Playback speed selector
                Button {
                    showSpeedSheet = true
                } label: {
                    Text(String(format: "%.2fx", viewModel.playbackRate))
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.onYellow)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(OPTheme.yellow)
                        .clipShape(RoundedRectangle(cornerRadius: 3))
                }
            }

            // Scrub Slider
            Slider(
                value: Binding(
                    get: { viewModel.isSeeking ? viewModel.seekTime : viewModel.currentTime },
                    set: {
                        viewModel.isSeeking = true
                        viewModel.seekTime = $0
                    }
                ),
                in: 0...max(1, viewModel.duration),
                onEditingChanged: { editing in
                    if !editing {
                        viewModel.seek(to: viewModel.seekTime)
                        scheduleControlsTimer()
                    }
                }
            )
            .tint(OPTheme.yellow)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(
            LinearGradient(colors: [Color.clear, Color.black.opacity(0.85)], startPoint: .top, endPoint: .bottom)
        )
    }

    // MARK: - HUD Overlays
    private var doubleTapOverlays: some View {
        HStack {
            if doubleTapFeedbackLeft {
                VStack(spacing: 6) {
                    Image(systemName: "gobackward.10")
                        .font(.system(size: 32, weight: .bold))
                    Text("-10s")
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                }
                .foregroundColor(OPTheme.yellow)
                .frame(maxWidth: .infinity)
                .transition(.opacity)
            } else {
                Spacer()
            }

            if doubleTapFeedbackRight {
                VStack(spacing: 6) {
                    Image(systemName: "goforward.10")
                        .font(.system(size: 32, weight: .bold))
                    Text("+10s")
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                }
                .foregroundColor(OPTheme.yellow)
                .frame(maxWidth: .infinity)
                .transition(.opacity)
            } else {
                Spacer()
            }
        }
        .allowsHitTesting(false)
    }

    private var gestureHuds: some View {
        Group {
            if showBrightnessHud {
                VStack(spacing: 8) {
                    Image(systemName: "sun.max.fill")
                        .font(.system(size: 26))
                        .foregroundColor(OPTheme.yellow)
                    ProgressView(value: Double(brightnessLevel))
                        .tint(OPTheme.yellow)
                        .frame(width: 80)
                }
                .padding(16)
                .background(Color.black.opacity(0.75))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            if showSeekHud {
                HStack(spacing: 6) {
                    Image(systemName: seekHudDelta >= 0 ? "forward.fill" : "backward.fill")
                        .foregroundColor(OPTheme.yellow)
                    Text(String(format: "%+.0fs", seekHudDelta))
                        .font(.system(size: 18, weight: .heavy, design: .monospaced))
                        .foregroundColor(OPTheme.opWhite)
                }
                .padding(16)
                .background(Color.black.opacity(0.8))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .allowsHitTesting(false)
    }

    private func toggleControls() {
        withAnimation(.easeInOut(duration: 0.2)) {
            showControls.toggle()
        }
        if showControls {
            scheduleControlsTimer()
        } else {
            controlsTimer?.cancel()
        }
    }

    private func scheduleControlsTimer() {
        controlsTimer?.cancel()
        controlsTimer = Task {
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            if !Task.isCancelled {
                withAnimation(.easeInOut(duration: 0.2)) {
                    showControls = false
                }
            }
        }
    }

    private func triggerDoubleTapFeedback(isLeft: Bool) {
        if isLeft {
            withAnimation(.easeIn(duration: 0.1)) { doubleTapFeedbackLeft = true }
            Task {
                try? await Task.sleep(nanoseconds: 500_000_000)
                withAnimation { doubleTapFeedbackLeft = false }
            }
        } else {
            withAnimation(.easeIn(duration: 0.1)) { doubleTapFeedbackRight = true }
            Task {
                try? await Task.sleep(nanoseconds: 500_000_000)
                withAnimation { doubleTapFeedbackRight = false }
            }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        guard !seconds.isNaN && seconds >= 0 else { return "00:00" }
        let s = Int(seconds)
        let m = s / 60
        let sec = s % 60
        if m >= 60 {
            let h = m / 60
            let rm = m % 60
            return String(format: "%02d:%02d:%02d", h, rm, sec)
        }
        return String(format: "%02d:%02d", m, sec)
    }
}
