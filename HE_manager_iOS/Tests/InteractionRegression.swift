import Foundation

private final class NotificationCounter: @unchecked Sendable { var count = 0 }

@main
struct InteractionRegression {
    @MainActor static func main() async throws {
        let port = try String(contentsOfFile: CommandLine.arguments[1]).trimmingCharacters(in: .whitespacesAndNewlines)
        let base = "http://127.0.0.1:\(port)"
        let suite = "he-interaction-regression-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let prefs = Preferences(defaults: defaults)
        prefs.saveCredentials(serverUrl: base, token: "synthetic-fixture")

        func control(_ scenario: String) async throws {
            var request = URLRequest(url: URL(string: base + "/control")!)
            request.httpMethod = "POST"
            request.httpBody = try JSONSerialization.data(withJSONObject: ["scenario": scenario])
            _ = try await URLSession.shared.data(for: request)
        }
        func state() async throws -> [String: Any] {
            let (data, _) = try await URLSession.shared.data(from: URL(string: base + "/state")!)
            return try JSONSerialization.jsonObject(with: data) as! [String: Any]
        }
        func waitFor(_ condition: () async throws -> Bool) async throws {
            for _ in 0..<200 {
                if try await condition() { return }
                try await Task.sleep(nanoseconds: 20_000_000)
            }
            fatalError("Fixture condition timed out")
        }
        func check(_ condition: @autoclosure () -> Bool, _ name: String) {
            precondition(condition(), name)
            print("PASS \(name)")
        }

        let counter = NotificationCounter()
        let observer = NotificationCenter.default.addObserver(forName: .authDidExpire, object: nil, queue: nil) { _ in counter.count += 1 }
        do { _ = try await ApiClient(baseUrl: base).login(username: "wrong", password: "wrong") } catch {}
        try await Task.sleep(nanoseconds: 80_000_000)
        check(counter.count == 0, "invalid login does not expire a session")
        do { _ = try await ApiClient(baseUrl: base, token: "expired").getMedia() } catch {}
        try await waitFor { counter.count == 1 }
        check(counter.count == 1, "authenticated 401 still expires a session")
        NotificationCenter.default.removeObserver(observer)

        try await control("normal")
        let library = LibraryViewModel(preferences: prefs)
        await library.loadData()
        library.toggleFavorite(for: library.allItems[0])
        try await waitFor { (try await state()["patches"] as? [Bool])?.count == 1 }
        await library.loadData()
        check(library.allItems[0].favorite, "refresh preserves the pending favorite intent")
        library.toggleFavorite(for: library.allItems[0])
        try await waitFor { (try await state()["patch_done"] as? Int) == 2 }
        try await waitFor { !library.allItems[0].favorite }
        let writes = try await state()["patches"] as! [Bool]
        check(writes == [true, false], "rapid favorite writes reach the server in user order")
        check(!library.allItems[0].favorite, "late favorite response does not overwrite the final intent")

        try await control("favorite_fail")
        library.toggleFavorite(for: library.allItems[0])
        try await waitFor { (try await state()["patches"] as? [Bool])?.count == 1 }
        library.toggleFavorite(for: library.allItems[0])
        try await waitFor { library.errorMessage != nil }
        check(library.allItems[0].favorite, "failed second favorite restores the confirmed server value")
        check(library.errorMessage?.contains("收藏失败") == true, "favorite failure is visible")

        try await control("media_fail")
        await library.loadData()
        check(library.allItems.count == 1 && library.errorMessage != nil && !library.isLoading,
              "failed refresh preserves data and exposes a recoverable error")

        try await control("media_race")
        let firstLoad = Task { await library.loadData() }
        try await waitFor { (try await state()["media_requests"] as? Int) == 1 }
        await library.loadData()
        await firstLoad.value
        check(library.allItems.first?.id == 2, "older library refresh cannot overwrite a newer refresh")

        try await control("normal")
        let creators = CreatorsViewModel(preferences: prefs)
        creators.selectedKind = "artist"
        let oldFilter = Task { await creators.loadCreators() }
        try await waitFor { (try await state()["creator_requests"] as? Int) == 1 }
        creators.selectedKind = "x"
        await creators.loadCreators()
        await oldFilter.value
        check(creators.filteredCreators.count == 1 && creators.creators.first?.kind == "x",
              "older creator filter cannot blank the selected category")
        print("HE_INTERACTION_TESTS_OK")
    }
}
