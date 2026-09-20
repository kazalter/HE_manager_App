import SwiftUI

public struct LyricsView: View {
    public let lyrics: [LyricLine]
    public let currentTimeSec: Double
    public let onSelectTime: (Double) -> Void

    public init(lyrics: [LyricLine], currentTimeSec: Double, onSelectTime: @escaping (Double) -> Void) {
        self.lyrics = lyrics
        self.currentTimeSec = currentTimeSec
        self.onSelectTime = onSelectTime
    }

    public var activeIndex: Int {
        guard !lyrics.isEmpty else { return -1 }
        for i in (0..<lyrics.count).reversed() {
            if currentTimeSec >= lyrics[i].timeSec {
                return i
            }
        }
        return 0
    }

    public var body: some View {
        if lyrics.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "music.note.list")
                    .font(.system(size: 36))
                    .foregroundColor(OPTheme.opWhiteFaint)
                Text("// NO TIMED LYRICS //")
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(OPTheme.opWhiteMuted)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 20) {
                        Color.clear.frame(height: 120)

                        ForEach(Array(lyrics.enumerated()), id: \.offset) { index, line in
                            let isActive = index == activeIndex
                            Button {
                                onSelectTime(line.timeSec)
                            } label: {
                                Text(line.text)
                                    .font(.system(size: isActive ? 18 : 14, weight: isActive ? .bold : .medium))
                                    .foregroundColor(isActive ? OPTheme.yellow : OPTheme.opWhiteMuted)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 24)
                                    .scaleEffect(isActive ? 1.05 : 1.0)
                                    .animation(.easeInOut(duration: 0.2), value: isActive)
                            }
                            .buttonStyle(.plain)
                            .id(index)
                        }

                        Color.clear.frame(height: 160)
                    }
                }
                .onChange(of: activeIndex) { _, newIndex in
                    if newIndex >= 0 {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            proxy.scrollTo(newIndex, anchor: .center)
                        }
                    }
                }
            }
        }
    }
}
