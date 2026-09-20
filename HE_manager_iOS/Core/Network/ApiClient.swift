import Foundation

extension Notification.Name {
    public static let authDidExpire = Notification.Name("com.hemanager.authDidExpire")
}

public actor ApiClient {
    public let baseUrl: String
    public let token: String
    private let session: URLSession

    public init(baseUrl: String, token: String = "") {
        self.baseUrl = Self.trimSlash(baseUrl)
        self.token = token
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20.0
        config.timeoutIntervalForResource = 60.0
        self.session = URLSession(configuration: config)
    }

    public static func trimSlash(_ url: String) -> String {
        var text = url.trimmingCharacters(in: .whitespacesAndNewlines)
        while text.hasSuffix("/") {
            text.removeLast()
        }
        return text
    }

    public static func tokenQuery(_ token: String) -> String {
        let encoded = token.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? token
        return "token=\(encoded)"
    }

    public nonisolated func mobileUrl(path: String) -> String {
        let separator = path.contains("?") ? "&" : "?"
        return "\(baseUrl)\(path)\(separator)\(Self.tokenQuery(token))"
    }

    public nonisolated func thumbnailStreamUrl(coverPath: String) -> String? {
        guard !coverPath.isEmpty, coverPath != "null" else { return nil }
        guard let encoded = coverPath.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) else { return nil }
        return mobileUrl(path: "/mobile/thumbnails/\(encoded)")
    }

    public nonisolated func mediaStreamUrl(mediaId: Int) -> String {
        return mobileUrl(path: "/mobile/stream/\(mediaId)")
    }

    public nonisolated func audioTrackStreamUrl(mediaId: Int, index: Int) -> String {
        return mobileUrl(path: "/audio/\(mediaId)/track/\(index)")
    }

    public nonisolated func mangaPageUrl(mediaId: Int, page: Int) -> String {
        return mobileUrl(path: "/mobile/manga/\(mediaId)/page/\(page)")
    }

    public func getMangaTotalPages(mediaId: Int) async throws -> Int {
        guard let url = URL(string: "\(baseUrl)/mobile/manga/\(mediaId)/pages") else {
            throw NetworkError.invalidUrl("\(baseUrl)/mobile/manga/\(mediaId)/pages")
        }
        let data = try await executeRequest(url: url, method: "GET", body: nil, auth: true)
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let total = json["total_pages"] as? Int {
            return max(1, total)
        }
        return 1
    }

    // MARK: - Auth Methods

    public func login(username: String, password: String) async throws -> String {
        let body: [String: Any] = ["username": username.trimmingCharacters(in: .whitespacesAndNewlines), "password": password]
        let json = try await postJson(path: "/auth/login", body: body, auth: false)
        guard let token = json["access_token"] as? String else {
            throw NetworkError.decodingError("未返回有效的 access_token")
        }
        return token
    }

    public func bootstrap(username: String, password: String) async throws -> String {
        let body: [String: Any] = ["username": username.trimmingCharacters(in: .whitespacesAndNewlines), "password": password, "is_admin": true]
        let json = try await postJson(path: "/auth/bootstrap", body: body, auth: false)
        guard let token = json["access_token"] as? String else {
            throw NetworkError.decodingError("未返回有效的 access_token")
        }
        return token
    }

    // MARK: - Media Library

    public func getMedia(mediaType: String = "", search: String = "", sort: String = "date") async throws -> [MediaItem] {
        return try await retrying {
            var components = URLComponents(string: "\(self.baseUrl)/mobile/media")
            var queryItems: [URLQueryItem] = [
                URLQueryItem(name: "sort", value: sort.isEmpty ? "date" : sort)
            ]
            if !mediaType.isEmpty {
                queryItems.append(URLQueryItem(name: "media_type", value: mediaType))
            }
            if !search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                queryItems.append(URLQueryItem(name: "search", value: search.trimmingCharacters(in: .whitespacesAndNewlines)))
            }
            components?.queryItems = queryItems

            guard let url = components?.url else {
                throw NetworkError.invalidUrl("\(self.baseUrl)/mobile/media")
            }

            let data = try await self.executeRequest(url: url, method: "GET", body: nil, auth: true)
            return try JSONDecoder().decode([MediaItem].self, from: data)
        }
    }

    public func getMediaDetail(mediaId: Int) async throws -> MediaItem {
        guard let url = URL(string: "\(baseUrl)/mobile/media/\(mediaId)") else {
            throw NetworkError.invalidUrl("\(baseUrl)/mobile/media/\(mediaId)")
        }
        let data = try await executeRequest(url: url, method: "GET", body: nil, auth: true)
        return try JSONDecoder().decode(MediaItem.self, from: data)
    }

    public func toggleFavorite(mediaId: Int, favorite: Bool) async throws -> MediaItem {
        let body: [String: Any] = ["favorite": favorite]
        let json = try await patchJson(path: "/media/\(mediaId)", body: body, auth: true)
        let data = try JSONSerialization.data(withJSONObject: json)
        return try JSONDecoder().decode(MediaItem.self, from: data)
    }

    public func deleteMedia(mediaId: Int) async throws {
        _ = try await deleteJson(path: "/media/\(mediaId)", auth: true)
    }

    public func getTags() async throws -> [TagItem] {
        guard let url = URL(string: "\(baseUrl)/tags") else {
            throw NetworkError.invalidUrl("\(baseUrl)/tags")
        }
        let data = try await executeRequest(url: url, method: "GET", body: nil, auth: true)
        return try JSONDecoder().decode([TagItem].self, from: data)
    }

    public func addTag(mediaId: Int, tagName: String) async throws -> MediaItem {
        let body: [String: Any] = ["name": tagName]
        let json = try await postJson(path: "/media/\(mediaId)/tags", body: body, auth: true)
        let data = try JSONSerialization.data(withJSONObject: json)
        return try JSONDecoder().decode(MediaItem.self, from: data)
    }

    public func saveProgress(mediaId: Int, progress: Int, duration: Int? = nil) async throws {
        var body: [String: Any] = ["progress": progress]
        if let duration = duration, duration > 0 {
            body["duration"] = duration
        }
        _ = try await patchJson(path: "/media/\(mediaId)", body: body, auth: true)
    }

    // MARK: - Creators

    public func getCreators(typeFilter: String = "all", search: String = "", sort: String = "") async throws -> [Creator] {
        return try await retrying {
            var components = URLComponents(string: "\(self.baseUrl)/mobile/creators")
            var queryItems: [URLQueryItem] = []
            if !typeFilter.isEmpty && typeFilter != "all" {
                queryItems.append(URLQueryItem(name: "kind", value: typeFilter))
            }
            if !search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                queryItems.append(URLQueryItem(name: "search", value: search.trimmingCharacters(in: .whitespacesAndNewlines)))
            }
            if !sort.isEmpty {
                queryItems.append(URLQueryItem(name: "sort", value: sort))
            }
            components?.queryItems = queryItems.isEmpty ? nil : queryItems

            guard let url = components?.url else {
                throw NetworkError.invalidUrl("\(self.baseUrl)/mobile/creators")
            }

            let data = try await self.executeRequest(url: url, method: "GET", body: nil, auth: true)
            return try JSONDecoder().decode([Creator].self, from: data)
        }
    }

    public func getCreatorDetail(key: String) async throws -> CreatorDetail {
        return try await retrying {
            var components = URLComponents(string: "\(self.baseUrl)/mobile/creators/detail")
            components?.queryItems = [URLQueryItem(name: "key", value: key)]
            guard let url = components?.url else {
                throw NetworkError.invalidUrl("\(self.baseUrl)/mobile/creators/detail")
            }
            let data = try await self.executeRequest(url: url, method: "GET", body: nil, auth: true)
            return try JSONDecoder().decode(CreatorDetail.self, from: data)
        }
    }

    // MARK: - Audio (ASMR)

    public func getAudioTracks(mediaId: Int) async throws -> [AudioTrack] {
        guard let url = URL(string: "\(baseUrl)/audio/\(mediaId)/tracks") else {
            throw NetworkError.invalidUrl("\(baseUrl)/audio/\(mediaId)/tracks")
        }
        let data = try await executeRequest(url: url, method: "GET", body: nil, auth: true)
        struct TracksResponse: Codable {
            let tracks: [AudioTrack]
        }
        return (try? JSONDecoder().decode(TracksResponse.self, from: data).tracks) ?? []
    }

    public func getAudioLyrics(mediaId: Int, trackIndex: Int) async throws -> [LyricLine] {
        guard let url = URL(string: "\(baseUrl)/audio/\(mediaId)/track/\(trackIndex)/lyrics") else {
            return []
        }
        do {
            let data = try await executeRequest(url: url, method: "GET", body: nil, auth: true)
            struct LyricsResponse: Codable {
                let lines: [LyricLine]
            }
            let resp = try JSONDecoder().decode(LyricsResponse.self, from: data)
            return resp.lines.sorted { $0.timeSec < $1.timeSec }
        } catch {
            return []
        }
    }

    // MARK: - Manga

    public func getMangaInfo(mediaId: Int) async throws -> [String: Any] {
        guard let url = URL(string: "\(baseUrl)/manga/\(mediaId)/info") else {
            throw NetworkError.invalidUrl("\(baseUrl)/manga/\(mediaId)/info")
        }
        let data = try await executeRequest(url: url, method: "GET", body: nil, auth: true)
        return (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    // MARK: - Raw JSON Helpers

    public func postJson(path: String, body: [String: Any], auth: Bool) async throws -> [String: Any] {
        guard let url = URL(string: "\(baseUrl)\(path)") else {
            throw NetworkError.invalidUrl("\(baseUrl)\(path)")
        }
        let data = try JSONSerialization.data(withJSONObject: body)
        let respData = try await executeRequest(url: url, method: "POST", body: data, auth: auth)
        return (try JSONSerialization.jsonObject(with: respData) as? [String: Any]) ?? [:]
    }

    public func patchJson(path: String, body: [String: Any], auth: Bool) async throws -> [String: Any] {
        guard let url = URL(string: "\(baseUrl)\(path)") else {
            throw NetworkError.invalidUrl("\(baseUrl)\(path)")
        }
        let data = try JSONSerialization.data(withJSONObject: body)
        let respData = try await executeRequest(url: url, method: "PATCH", body: data, auth: auth)
        return (try JSONSerialization.jsonObject(with: respData) as? [String: Any]) ?? [:]
    }

    public func deleteJson(path: String, auth: Bool) async throws -> [String: Any] {
        guard let url = URL(string: "\(baseUrl)\(path)") else {
            throw NetworkError.invalidUrl("\(baseUrl)\(path)")
        }
        let respData = try await executeRequest(url: url, method: "DELETE", body: nil, auth: auth)
        return (try? JSONSerialization.jsonObject(with: respData) as? [String: Any]) ?? [:]
    }

    // MARK: - Low-level Request Execution

    private func executeRequest(url: URL, method: String, body: Data?, auth: Bool) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
        }
        if auth && !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw NetworkError.unknown("未知的网络响应")
            }

            let code = httpResponse.statusCode
            if code == 401 {
                var detail = "登录已过期，请重新登录"
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let d = json["detail"] as? String, !d.isEmpty {
                    detail = d
                }
                Task { @MainActor in
                    NotificationCenter.default.post(name: .authDidExpire, object: nil)
                }
                throw NetworkError.unauthorized(detail)
            }

            guard (200...299).contains(code) else {
                var detail = ""
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let d = json["detail"] as? String {
                    detail = d
                } else if let str = String(data: data, encoding: .utf8) {
                    detail = str
                }
                throw NetworkError.serverError(statusCode: code, message: detail)
            }

            return data
        } catch let error as NetworkError {
            throw error
        } catch {
            throw NetworkError.networkUnavailable(error.localizedDescription)
        }
    }

    private func retrying<T: Sendable>(operation: @Sendable () async throws -> T) async throws -> T {
        let delaysMs: [UInt64] = [200, 600]
        var lastError: Error?

        for attempt in 0...delaysMs.count {
            do {
                return try await operation()
            } catch let error as NetworkError {
                switch error {
                case .networkUnavailable:
                    lastError = error
                    if attempt < delaysMs.count {
                        try await Task.sleep(nanoseconds: delaysMs[attempt] * 1_000_000)
                    }
                default:
                    throw error
                }
            } catch {
                lastError = error
                if attempt < delaysMs.count {
                    try await Task.sleep(nanoseconds: delaysMs[attempt] * 1_000_000)
                }
            }
        }
        throw lastError ?? NetworkError.unknown("请求重试失败")
    }
}
