import SwiftUI

public struct DrawerView: View {
    public let serverUrl: String
    public let totalCount: Int
    public let onSelectContinue: () -> Void
    public let onSelectStarred: () -> Void
    public let onSelectCreators: () -> Void
    public let onSelectSettings: () -> Void
    public let onRefresh: () -> Void
    public let onDisconnect: () -> Void
    public let onClose: () -> Void

    public init(
        serverUrl: String,
        totalCount: Int,
        onSelectContinue: @escaping () -> Void = {},
        onSelectStarred: @escaping () -> Void = {},
        onSelectCreators: @escaping () -> Void,
        onSelectSettings: @escaping () -> Void,
        onRefresh: @escaping () -> Void = {},
        onDisconnect: @escaping () -> Void,
        onClose: @escaping () -> Void
    ) {
        self.serverUrl = serverUrl
        self.totalCount = totalCount
        self.onSelectContinue = onSelectContinue
        self.onSelectStarred = onSelectStarred
        self.onSelectCreators = onSelectCreators
        self.onSelectSettings = onSelectSettings
        self.onRefresh = onRefresh
        self.onDisconnect = onDisconnect
        self.onClose = onClose
    }

    private var cleanHost: String {
        serverUrl.replacingOccurrences(of: "http://", with: "").replacingOccurrences(of: "https://", with: "")
    }

    public var body: some View {
        ZStack(alignment: .leading) {
            Color.black.opacity(0.65)
                .ignoresSafeArea()
                .onTapGesture {
                    onClose()
                }

            VStack(alignment: .leading, spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        // Section 1: 操作员面板
                        VStack(alignment: .leading, spacing: 14) {
                            HStack(spacing: 6) {
                                Text("//")
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(OPTheme.yellow)
                                Text("操作员面板")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(OPTheme.opWhiteSoft)
                                Text("Operator")
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundColor(OPTheme.opWhiteMuted)
                            }

                            HStack(spacing: 14) {
                                // Cut-corner HE Avatar box
                                ZStack {
                                    CutCornerShape(cut: 6)
                                        .stroke(OPTheme.yellow, lineWidth: 1.5)
                                        .frame(width: 48, height: 48)
                                    Text("HE")
                                        .font(.system(size: 15, weight: .heavy, design: .monospaced))
                                        .foregroundColor(OPTheme.yellow)
                                }

                                VStack(alignment: .leading, spacing: 3) {
                                    Text("ADMIN")
                                        .font(.system(size: 16, weight: .heavy, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhite)
                                        .tracking(1.0)

                                    Text("ARCHIVIST · LVL 14")
                                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhiteMuted)
                                        .tracking(0.5)

                                    Text("UID:1416-176-661")
                                        .font(.system(size: 9.5, design: .monospaced))
                                        .foregroundColor(OPTheme.opWhiteFaint)
                                }
                            }
                        }

                        // Section 2: 服务连接
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 6) {
                                Text("//")
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(OPTheme.yellow)
                                Text("服务连接")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(OPTheme.opWhiteSoft)
                                Text("Server Link")
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundColor(OPTheme.opWhiteMuted)
                            }

                            HStack {
                                HStack(spacing: 4) {
                                    Circle().fill(OPTheme.online).frame(width: 5, height: 5)
                                    Circle().fill(OPTheme.online).frame(width: 5, height: 5)
                                    Text("ONLINE")
                                        .font(.system(size: 10.5, weight: .bold, design: .monospaced))
                                        .foregroundColor(OPTheme.online)
                                        .tracking(0.5)
                                }

                                Spacer()

                                Text(cleanHost)
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(OPTheme.opWhiteSoft)
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(OPTheme.panel)
                            .clipShape(CutCornerShape(cut: 8))
                            .overlay(
                                CutCornerShape(cut: 8)
                                    .stroke(OPTheme.hairlineMid, lineWidth: 1)
                            )
                        }

                        // Section 3: 导航
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 6) {
                                Text("//")
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(OPTheme.yellow)
                                Text("导航")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(OPTheme.opWhiteSoft)
                                Text("Navigate")
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundColor(OPTheme.opWhiteMuted)
                            }

                            // 媒体库 (Active)
                            Button {
                                onClose()
                            } label: {
                                HStack(spacing: 12) {
                                    Rectangle()
                                        .fill(OPTheme.yellow)
                                        .frame(width: 3, height: 18)

                                    Image(systemName: "house.fill")
                                        .font(.system(size: 14))
                                        .foregroundColor(OPTheme.yellow)

                                    VStack(alignment: .leading, spacing: 1) {
                                        Text("Library")
                                            .font(.system(size: 9.5, design: .monospaced))
                                            .foregroundColor(OPTheme.yellow)
                                        Text("媒体库")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(OPTheme.opWhite)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(OPTheme.opSurface)
                                .clipShape(CutCornerShape(cut: 8))
                            }
                            .buttonStyle(.plain)

                            // 继续看
                            drawerNavItem(icon: "clock.arrow.circlepath", en: "Continue", cn: "继续看") {
                                onClose()
                                onSelectContinue()
                            }

                            // 收藏夹
                            drawerNavItem(icon: "star", en: "Starred", cn: "收藏夹") {
                                onClose()
                                onSelectStarred()
                            }

                            // 创作者
                            drawerNavItem(icon: "tag", en: "Curators", cn: "创作者") {
                                onClose()
                                onSelectCreators()
                            }

                            // 设置
                            drawerNavItem(icon: "gearshape", en: "Settings", cn: "设置") {
                                onClose()
                                onSelectSettings()
                            }
                        }
                    }
                    .padding(20)
                    .padding(.top, 40)
                }

                Spacer()

                // Bottom: Refresh circle button + Disconnect button
                HStack(spacing: 10) {
                    IconBtn4(icon: "arrow.clockwise") {
                        onClose()
                        onRefresh()
                    }

                    Button {
                        onClose()
                        onDisconnect()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                .font(.system(size: 13, weight: .bold))
                            Text("DISCONNECT 退出登录")
                                .font(.system(size: 11.5, weight: .bold, design: .monospaced))
                                .tracking(0.5)
                        }
                        .foregroundColor(OPTheme.danger)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(
                            CutCornerShape(cut: 6)
                                .fill(Color.clear)
                        )
                        .overlay(
                            CutCornerShape(cut: 6)
                                .stroke(OPTheme.danger.opacity(0.45), lineWidth: 1)
                        )
                        .clipShape(CutCornerShape(cut: 6))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .frame(width: 300)
            .background(OPTheme.ink)
            .ignoresSafeArea()
        }
    }

    private func drawerNavItem(icon: String, en: String, cn: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundColor(OPTheme.opWhiteSoft)
                    .frame(width: 18)

                VStack(alignment: .leading, spacing: 1) {
                    Text(en)
                        .font(.system(size: 9.5, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteMuted)
                    Text(cn)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(OPTheme.opWhite)
                }
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}
