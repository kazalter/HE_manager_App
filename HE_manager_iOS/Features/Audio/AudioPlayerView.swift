import SwiftUI

public struct AudioPlayerView: View {
    public let mediaItem: MediaItem
    @State private var viewModel: AudioPlayerViewModel
    @Environment(\.dismiss) private var dismiss

    public init(mediaItem: MediaItem) {
        self.mediaItem = mediaItem
        _viewModel = State(initialValue: AudioPlayerViewModel(mediaId: mediaItem.id))
    }

    public var body: some View {
        ZStack {
            OPTheme.void.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Bar
                topBar

                // Center (Artwork or Lyrics)
                ZStack {
                    if viewModel.showLyrics {
                        LyricsView(
                            lyrics: viewModel.state.lyrics,
                            currentTimeSec: Double(viewModel.state.positionMs) / 1000.0,
                            onSelectTime: { time in
                                viewModel.seek(to: time)
                            }
                        )
                    } else {
                        artworkView
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                // Bottom Controls
                bottomControls
            }
        }
        .sheet(isPresented: $viewModel.showTracklist) {
            tracklistSheet
        }
        .task {
            await viewModel.loadWork(initialProgressSeconds: mediaItem.progress)
        }
        .onDisappear {
            viewModel.saveCurrentProgress()
        }
    }

    // MARK: - Top Bar
    private var topBar: some View {
        HStack(spacing: 12) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(OPTheme.opWhite)
                    .padding(10)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("NOW PLAYING // ASMR")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(OPTheme.yellowDim)
                Text(viewModel.state.workTitle)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(OPTheme.opWhite)
                    .lineLimit(1)
            }

            Spacer()

            // Lyrics Toggle Button
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    viewModel.showLyrics.toggle()
                }
            } label: {
                Image(systemName: "quote.bubble")
                    .font(.system(size: 16))
                    .foregroundColor(viewModel.showLyrics ? OPTheme.yellow : OPTheme.opWhiteSoft)
                    .padding(8)
            }

            // Sleep Timer Menu
            Menu {
                Button("Off // 关闭定时") { viewModel.setSleepTimer(minutes: 0) }
                Button("15 Minutes // 15分钟") { viewModel.setSleepTimer(minutes: 15) }
                Button("30 Minutes // 30分钟") { viewModel.setSleepTimer(minutes: 30) }
                Button("45 Minutes // 45分钟") { viewModel.setSleepTimer(minutes: 45) }
                Button("60 Minutes // 60分钟") { viewModel.setSleepTimer(minutes: 60) }
                Divider()
                Button(viewModel.state.sleepAfterCurrentTrack ? "✓ 播完本轨后停止" : "播完本轨后停止") {
                    viewModel.setSleepAfterCurrentTrack()
                }
            } label: {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "moon.zzz")
                        .font(.system(size: 16))
                        .foregroundColor(viewModel.state.sleepTimerArmed ? OPTheme.yellow : OPTheme.opWhiteSoft)
                        .padding(8)

                    if viewModel.state.sleepTimerArmed {
                        Circle()
                            .fill(OPTheme.yellow)
                            .frame(width: 7, height: 7)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(OPTheme.ink)
    }

    // MARK: - Artwork View
    private var artworkView: some View {
        VStack(spacing: 24) {
            Spacer()
            AngularPanel(cut: 16, yellowCorner: true, hairline: true) {
                AsyncCoverImage(urlString: viewModel.state.coverUrl, contentMode: .fit)
                    .frame(maxWidth: 300, maxHeight: 300)
            }
            .padding(.horizontal, 40)
            Spacer()
        }
    }

    // MARK: - Bottom Controls
    private var bottomControls: some View {
        VStack(spacing: 16) {
            // Track Info
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    if let track = viewModel.state.currentTrack {
                        Text("TRACK \(String(format: "%02d", track.index))")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(OPTheme.yellowDim)
                        Text(track.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(OPTheme.opWhite)
                            .lineLimit(1)
                    }
                }
                Spacer()

                // Tracklist button
                Button {
                    viewModel.showTracklist = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "list.bullet")
                        Text("\(viewModel.state.tracks.count)")
                            .font(.system(size: 11, design: .monospaced))
                    }
                    .foregroundColor(OPTheme.opWhiteSoft)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(OPTheme.panel)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                }
            }

            // Progress Slider
            VStack(spacing: 4) {
                Slider(
                    value: Binding(
                        get: { Double(viewModel.state.positionMs) / 1000.0 },
                        set: { viewModel.seek(to: $0) }
                    ),
                    in: 0...Double(max(1, viewModel.state.durationMs)) / 1000.0
                )
                .tint(OPTheme.yellow)

                HStack {
                    Text(formatSeconds(Double(viewModel.state.positionMs) / 1000.0))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(OPTheme.yellow)
                    Spacer()
                    Text(formatSeconds(Double(viewModel.state.durationMs) / 1000.0))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteMuted)
                }
            }

            // Playback Buttons
            HStack(spacing: 36) {
                // Loop Mode
                Button {
                    // toggle loop mode
                } label: {
                    Image(systemName: "repeat")
                        .font(.system(size: 18))
                        .foregroundColor(OPTheme.opWhiteSoft)
                }

                // Prev Track
                Button {
                    viewModel.previousTrack()
                } label: {
                    Image(systemName: "backward.fill")
                        .font(.system(size: 24))
                        .foregroundColor(OPTheme.opWhite)
                }

                // Play / Pause
                Button {
                    viewModel.togglePlayPause()
                } label: {
                    Image(systemName: viewModel.state.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 32))
                        .foregroundColor(OPTheme.onYellow)
                        .padding(20)
                        .background(OPTheme.yellow)
                        .clipShape(Circle())
                }

                // Next Track
                Button {
                    viewModel.nextTrack()
                } label: {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 24))
                        .foregroundColor(OPTheme.opWhite)
                }

                // Speed
                Button {
                    // speed
                } label: {
                    Text("1.0x")
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteSoft)
                }
            }
            .padding(.top, 8)
        }
        .padding(24)
        .background(OPTheme.ink)
    }

    // MARK: - Tracklist Sheet
    private var tracklistSheet: some View {
        NavigationStack {
            ZStack {
                OPTheme.void.ignoresSafeArea()

                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(Array(viewModel.state.tracks.enumerated()), id: \.offset) { index, track in
                            let isCurrent = index == viewModel.state.currentIndex
                            Button {
                                viewModel.playTrack(at: index)
                                viewModel.showTracklist = false
                            } label: {
                                HStack(spacing: 12) {
                                    Text(String(format: "%02d", track.index))
                                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                                        .foregroundColor(isCurrent ? OPTheme.yellow : OPTheme.opWhiteMuted)

                                    Text(track.title)
                                        .font(.system(size: 13, weight: isCurrent ? .bold : .medium))
                                        .foregroundColor(isCurrent ? OPTheme.yellow : OPTheme.opWhite)
                                        .lineLimit(1)

                                    Spacer()

                                    if let dur = track.durationSec {
                                        Text(formatSeconds(dur))
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundColor(OPTheme.opWhiteMuted)
                                    }

                                    if track.hasLyrics {
                                        Image(systemName: "quote.bubble.fill")
                                            .font(.system(size: 10))
                                            .foregroundColor(OPTheme.cyan)
                                    }
                                }
                                .padding(12)
                                .background(isCurrent ? OPTheme.opSurface : OPTheme.panel)
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 6)
                                        .stroke(isCurrent ? OPTheme.yellow : OPTheme.hairline, lineWidth: 1)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("TRACKLIST // 音轨列表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("CLOSE") {
                        viewModel.showTracklist = false
                    }
                    .foregroundColor(OPTheme.yellow)
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                }
            }
        }
    }

    private func formatSeconds(_ seconds: Double) -> String {
        guard !seconds.isNaN && seconds >= 0 else { return "00:00" }
        let s = Int(seconds)
        let m = s / 60
        let sec = s % 60
        return String(format: "%02d:%02d", m, sec)
    }
}
