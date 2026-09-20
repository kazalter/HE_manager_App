import SwiftUI

@main
struct HEManagerApp: App {
    @Bindable var preferences = Preferences.shared
    @State private var showAuthExpiredAlert: Bool = false

    var body: some Scene {
        WindowGroup {
            ZStack {
                OPTheme.void.ignoresSafeArea()

                if preferences.isLoggedIn {
                    LibraryView(onLogout: {
                        preferences.logout()
                    })
                } else {
                    LoginView(onLoggedIn: {
                        // preferences is updated inside LoginViewModel
                    })
                }
            }
            .preferredColorScheme(.dark)
            .alert("登录已过期", isPresented: $showAuthExpiredAlert) {
                Button("确定", role: .cancel) {
                    preferences.logout()
                }
            } message: {
                Text("与终端的连接身份验证已失效，请重新登录。")
            }
            .onReceive(NotificationCenter.default.publisher(for: .authDidExpire)) { _ in
                showAuthExpiredAlert = true
            }
        }
    }
}
