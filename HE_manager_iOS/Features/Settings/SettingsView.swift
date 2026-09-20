import SwiftUI

public struct SettingsView: View {
    @Bindable var preferences = Preferences.shared
    @State private var diskCacheMB: Double = 0.0
    @State private var isClearingCache: Bool = false
    @Environment(\.dismiss) private var dismiss
    public let onDisconnect: () -> Void

    public init(onDisconnect: @escaping () -> Void) {
        self.onDisconnect = onDisconnect
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                OPTheme.void.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Section: Display
                        settingsSection(title: "DISPLAY // 界面显示") {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Text("GALLERY COLUMNS")
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhite)
                                    Spacer()
                                    Text("\(preferences.galleryColumns) COLS")
                                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                                        .foregroundColor(OPTheme.yellow)
                                }

                                Slider(
                                    value: Binding(
                                        get: { Double(preferences.galleryColumns) },
                                        set: { preferences.galleryColumns = Int($0) }
                                    ),
                                    in: Double(Preferences.minGalleryColumns)...Double(Preferences.maxGalleryColumns),
                                    step: 1
                                )
                                .tint(OPTheme.yellow)

                                Text("可通过主库双指捏合手势或顶部菜单快速调整列数 (2 ~ 5 列)")
                                    .font(.system(size: 11))
                                    .foregroundColor(OPTheme.opWhiteMuted)
                            }
                        }

                        // Section: Storage & Cache
                        settingsSection(title: "STORAGE & CACHE // 存储缓存") {
                            VStack(alignment: .leading, spacing: 14) {
                                HStack {
                                    Text("COVER DISK CACHE")
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhite)
                                    Spacer()
                                    Text(String(format: "%.1f MB", diskCacheMB))
                                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                                        .foregroundColor(OPTheme.cyan)
                                }

                                GhostCTA(
                                    title: isClearingCache ? "CLEARING..." : "CLEAR COVER CACHE",
                                    icon: "trash",
                                    size: .small,
                                    fullWidth: true
                                ) {
                                    isClearingCache = true
                                    Task {
                                        await ImageCacheManager.shared.clearDiskCache()
                                        diskCacheMB = await ImageCacheManager.shared.diskCacheSizeMB()
                                        isClearingCache = false
                                    }
                                }
                                .disabled(isClearingCache)
                            }
                        }

                        // Section: Playback
                        settingsSection(title: "PLAYBACK // 播放偏好") {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Text("DEFAULT SPEED")
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhite)
                                    Spacer()
                                    Picker("Speed", selection: $preferences.defaultPlaybackSpeed) {
                                        Text("0.75x").tag(Float(0.75))
                                        Text("1.0x").tag(Float(1.0))
                                        Text("1.25x").tag(Float(1.25))
                                        Text("1.5x").tag(Float(1.5))
                                        Text("2.0x").tag(Float(2.0))
                                    }
                                    .pickerStyle(.menu)
                                    .tint(OPTheme.yellow)
                                }

                                Toggle(isOn: $preferences.autoResume) {
                                    Text("AUTO RESUME PROGRESS")
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhite)
                                }
                                .tint(OPTheme.yellow)
                            }
                        }

                        // Section: Connection & Account
                        settingsSection(title: "SERVER // 连接配置") {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Text("CURRENT ENDPOINT")
                                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhiteMuted)
                                    Spacer()
                                }
                                Text(preferences.serverUrl)
                                    .font(.system(size: 13, design: .monospaced))
                                    .foregroundColor(OPTheme.yellowDim)

                                Divider()
                                    .background(OPTheme.hairline)

                                Button(role: .destructive) {
                                    preferences.logout()
                                    dismiss()
                                    onDisconnect()
                                } label: {
                                    HStack {
                                        Image(systemName: "rectangle.portrait.and.arrow.right")
                                        Text("DISCONNECT / 退出连接")
                                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                                    }
                                    .foregroundColor(OPTheme.danger)
                                    .padding(.vertical, 4)
                                }
                            }
                        }

                        // App Info
                        VStack(spacing: 4) {
                            Text("HE MANAGER FOR iOS")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(OPTheme.opWhiteMuted)
                            Text("VERSION 1.0.0 (BUILD 1)")
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(OPTheme.opWhiteFaint)
                        }
                        .padding(.top, 16)
                    }
                    .padding(16)
                }
            }
            .navigationTitle("SETTINGS")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("DONE") {
                        dismiss()
                    }
                    .foregroundColor(OPTheme.yellow)
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                }
            }
            .task {
                diskCacheMB = await ImageCacheManager.shared.diskCacheSizeMB()
            }
        }
    }

    private func settingsSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(OPTheme.yellowDim)
                .padding(.horizontal, 4)

            AngularPanel(cut: 8, hairline: true) {
                content()
                    .padding(14)
            }
        }
    }
}
