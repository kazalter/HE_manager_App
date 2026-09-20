import Foundation

public enum NetworkError: LocalizedError, Sendable {
    case invalidUrl(String)
    case unauthorized(String)
    case serverError(statusCode: Int, message: String)
    case networkUnavailable(String)
    case decodingError(String)
    case unknown(String)

    public var errorDescription: String? {
        switch self {
        case .invalidUrl(let url):
            return "无效的服务器地址: \(url)"
        case .unauthorized(let msg):
            return msg.isEmpty ? "登录已过期，请重新登录" : msg
        case .serverError(let code, let msg):
            return msg.isEmpty ? "服务器错误 (HTTP \(code))" : msg
        case .networkUnavailable(let msg):
            return "无法连接至服务器: \(msg)"
        case .decodingError(let msg):
            return "数据解析错误: \(msg)"
        case .unknown(let msg):
            return msg
        }
    }
}
