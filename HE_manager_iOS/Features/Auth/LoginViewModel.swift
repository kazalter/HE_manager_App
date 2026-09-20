import Foundation
import SwiftUI

@Observable
public final class LoginViewModel {
    public var server: String = ""
    public var username: String = ""
    public var password: String = ""
    public var isBusy: Bool = false
    public var message: String = ""
    public var isBootstrap: Bool = false

    private let preferences: Preferences

    public init(preferences: Preferences = .shared) {
        self.preferences = preferences
        self.server = preferences.serverUrl.isEmpty ? "http://192.168.1.100:8010" : preferences.serverUrl
    }

    public func submit(onSuccess: @escaping () -> Void) {
        let targetServer = ApiClient.trimSlash(server)
        guard !targetServer.isEmpty else {
            message = "// ERR · 请输入服务器地址"
            return
        }
        guard !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !password.isEmpty else {
            message = "// ERR · 请填写用户名和密码"
            return
        }

        isBusy = true
        message = "// LINK · 正在连接终端..."

        Task { @MainActor in
            let client = ApiClient(baseUrl: targetServer)
            do {
                let token: String
                if isBootstrap {
                    token = try await client.bootstrap(username: username, password: password)
                } else {
                    token = try await client.login(username: username, password: password)
                }
                preferences.saveCredentials(serverUrl: targetServer, token: token)
                isBusy = false
                message = "// OK · 身份认证通过"
                onSuccess()
            } catch {
                isBusy = false
                message = "// ERR · \(error.localizedDescription)"
            }
        }
    }

    public func selectServer(_ url: String) {
        self.server = url
    }

    public func removeHistoryServer(_ url: String) {
        preferences.removeServerHistory(url)
    }
}
