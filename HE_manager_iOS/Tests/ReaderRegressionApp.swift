import UIKit

// A standalone simulator test app, compiled with the actual reader/cache
// sources. It never opens a real server or changes the user's app data.
final class ReaderFixtureProtocol: URLProtocol {
    struct Fixture {
        let data: Data
        let delay: Double
        let status: Int
    }
    static var fixtures: [String: Fixture] = [:]
    private var work: DispatchWorkItem?
    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host == "reader-test.invalid"
    }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let url = request.url, let fixture = Self.fixtures[url.absoluteString] else {
            client?.urlProtocol(self, didFailWithError: URLError(.fileDoesNotExist))
            return
        }
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            let response = HTTPURLResponse(url: url, statusCode: fixture.status,
                                           httpVersion: nil, headerFields: ["Content-Type": "image/png"])!
            self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            self.client?.urlProtocol(self, didLoad: fixture.data)
            self.client?.urlProtocolDidFinishLoading(self)
        }
        self.work = work
        DispatchQueue.main.asyncAfter(deadline: .now() + fixture.delay, execute: work)
    }
    override func stopLoading() { work?.cancel() }
}

@main
@MainActor
final class ReaderRegressionApp: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private var failures = 0
    private var checks = 0
    private var host: WebtoonZoomHostView?
    private var output = ""

    private func record(_ line: String) {
        print(line)
        output += line + "\n"
        let name = "results-" + (CommandLine.arguments.last ?? "test") + ".log"
        let file = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(name)
        try? output.write(to: file, atomically: true, encoding: .utf8)
    }

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        URLProtocol.registerClass(ReaderFixtureProtocol.self)
        return true
    }

    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "Regression", sessionRole: session.role)
        config.delegateClass = ReaderRegressionScene.self
        return config
    }

    func begin(in scene: UIWindowScene) {
        window = UIWindow(windowScene: scene)
        window?.rootViewController = UIViewController()
        window?.makeKeyAndVisible()
        Task { await run() }
    }

    private func check(_ condition: @autoclosure () -> Bool, _ name: String) {
        checks += 1
        if condition() { record("PASS: \(name)") }
        else { failures += 1; record("FAIL: \(name)") }
    }

    private func near(_ a: CGFloat, _ b: CGFloat) -> Bool { abs(a - b) < 1 }

    private func settle(_ seconds: Double = 0.15) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    private func image(_ height: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 100, height: height), format: format).image { ctx in
            UIColor.orange.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 100, height: height))
        }
    }

    private func mount(_ urls: [String], initial: Int = 0, jump: ReaderPageJump? = nil) -> (WebtoonZoomHostView, UIScrollView) {
        host?.stopLoading()
        host?.removeFromSuperview()
        let view = WebtoonZoomHostView(frame: CGRect(x: 0, y: 0, width: 400, height: 800))
        window!.rootViewController!.view.addSubview(view)
        host = view
        view.configure(urls: urls, initialPage: initial, jump: jump)
        view.setNeedsLayout()
        view.layoutIfNeeded()
        let scroll = view.subviews.compactMap { $0 as? UIScrollView }.first!
        return (view, scroll)
    }

    private func run() async {
        let run = UUID().uuidString
        let urls = (0..<100).map { "https://reader-test.invalid/\(run)/page/\($0)" }
        let normal = image(140)
        for url in urls {
            await ImageCacheManager.shared.store(image: normal, data: normal.pngData()!, for: url)
        }
        let (view, scroll) = mount(urls, initial: 10)
        var reported = 10
        view.onPageChange = { page in
            reported = page
            // Emulate SwiftUI's re-render after a binding update.
            view.configure(urls: urls, initialPage: page, jump: nil)
        }
        await settle()
        check(near(scroll.contentOffset.y, 5600), "initial page restores after layout")
        view.scrollViewWillBeginDragging(scroll)
        scroll.contentOffset = CGPoint(x: 0, y: 5930)
        await settle()
        check(reported == 11, "viewport midpoint reports the visible page")
        check(near(scroll.contentOffset.y, 5930), "passive page report does not snap to page top")

        let jump = ReaderPageJump(page: 11)
        view.configure(urls: urls, initialPage: 11, jump: jump)
        await settle()
        check(near(scroll.contentOffset.y, 6160), "explicit slider command jumps to page top")
        scroll.contentOffset.y = 6300
        view.configure(urls: urls, initialPage: 11, jump: jump)
        check(near(scroll.contentOffset.y, 6300), "the same command is not replayed on re-render")
        view.configure(urls: urls, initialPage: 11, jump: ReaderPageJump(page: 11))
        check(near(scroll.contentOffset.y, 6160), "a new command can revisit the same page")

        let canvas = view.viewForZooming(in: scroll)!
        let focus = CGPoint(x: 200, y: scroll.contentOffset.y + 400)
        view.toggleZoom(at: focus)
        await settle(0.6)
        check(near(scroll.zoomScale, 2.5), "double-tap zoom reaches the intended scale")
        let focusedAfterZoom = canvas.convert(focus, to: view)
        check(near(focusedAfterZoom.x, 200) && near(focusedAfterZoom.y, 400),
              "double-tap preserves both coordinates of a centered focal point")
        view.toggleZoom(at: focus)
        await settle(0.6)
        check(near(scroll.zoomScale, 1) && near(scroll.contentOffset.y, 6160),
              "double-tap reset returns to fit-width without losing the reading location")
        scroll.setZoomScale(2.5, animated: false)
        scroll.contentOffset = CGPoint(x: 180, y: 16000)
        await settle()
        check(near(scroll.bounds.width, 400) && near(canvas.bounds.width, 400),
              "zoom scales the document, not the viewport or its layout width")
        check(near(scroll.contentSize.width, 1000), "zoomed horizontal range follows native content size")
        check(near(scroll.contentOffset.x, 180), "zoomed content supports horizontal panning")

        let oldY = scroll.contentOffset.y / scroll.zoomScale
        let oldXFraction = (scroll.contentOffset.x + 200) / scroll.zoomScale / 400
        view.frame.size = CGSize(width: 800, height: 400)
        view.setNeedsLayout()
        view.layoutIfNeeded()
        await settle()
        check(near(scroll.contentOffset.y / scroll.zoomScale, oldY * 2),
              "rotation preserves page and in-page position at non-unit zoom")
        check(abs((scroll.contentOffset.x + 400) / scroll.zoomScale / 800 - oldXFraction) < 0.001,
              "rotation preserves horizontal reading position")
        check(canvas.subviews.count < 12, "a 100-page book retains only nearby page views")

        let (_, remounted) = mount(urls, initial: 14, jump: jump)
        await settle()
        check(near(remounted.contentOffset.y, 7840), "mode switching ignores stale slider commands")

        // An earlier page resolves after the reader is positioned on page 1.
        let slowURLs = (0..<8).map { "https://reader-test.invalid/\(run)/slow/\($0)" }
        ReaderFixtureProtocol.fixtures[slowURLs[0]] = .init(data: image(500).pngData()!, delay: 0.6, status: 200)
        for url in slowURLs.dropFirst() {
            await ImageCacheManager.shared.store(image: normal, data: normal.pngData()!, for: url)
        }
        let (slow, slowScroll) = mount(slowURLs, initial: 1)
        await settle()
        check(near(slowScroll.contentOffset.y, 560), "unknown earlier page uses a stable placeholder")
        slow.scrollViewWillBeginDragging(slowScroll)
        slowScroll.contentOffset.y = 700
        await settle(0.8)
        check(near(slowScroll.contentOffset.y, 2140),
              "late height change above viewport preserves the same page and fraction")
        let slowCanvas = slow.viewForZooming(in: slowScroll)!
        check(near(slowCanvas.subviews.first(where: { $0.tag == 0 })!.frame.height, 2000),
              "long image receives its true aspect-ratio height")

        // A page-count refresh must retain measurements, zoom and position.
        let before = slowScroll.contentOffset
        slow.configure(urls: Array(slowURLs.prefix(6)), initialPage: 0, jump: nil)
        await settle()
        check(near(slowScroll.contentOffset.y, before.y), "page-count refresh preserves reading position")

        let errorURL = "https://reader-test.invalid/\(run)/error"
        ReaderFixtureProtocol.fixtures[errorURL] = .init(data: Data(), delay: 0.05, status: 500)
        let (failedView, failedScroll) = mount([errorURL])
        await settle(0.3)
        let failedCanvas = failedView.viewForZooming(in: failedScroll)!
        let failedPage = failedCanvas.subviews.first!
        let button = failedPage.subviews.compactMap { $0 as? UIButton }.first!
        check(!button.isHidden, "HTTP failure exposes retry without removing the placeholder")
        check(near(failedPage.frame.height, 560), "failed image retains stable height")
        await ImageCacheManager.shared.store(image: normal, data: normal.pngData()!, for: errorURL)
        button.sendActions(for: .touchUpInside)
        await settle()
        check(button.isHidden && failedPage.subviews.compactMap { $0 as? UIImageView }.first!.image != nil,
              "retry replaces the error with the loaded image")

        // A cancelled request for another book must not overwrite the new book.
        let staleURL = "https://reader-test.invalid/\(run)/stale"
        ReaderFixtureProtocol.fixtures[staleURL] = .init(data: image(900).pngData()!, delay: 0.5, status: 200)
        let (stale, staleScroll) = mount([staleURL])
        await settle(0.05)
        stale.configure(urls: [urls[0]], initialPage: 0, jump: nil)
        await settle(0.7)
        check(near(stale.viewForZooming(in: staleScroll)!.bounds.height, 560),
              "cancelled old-book response cannot overwrite current layout")

        let zoomURLs = (0..<8).map { "https://reader-test.invalid/\(run)/zoom-load/\($0)" }
        ReaderFixtureProtocol.fixtures[zoomURLs[0]] = .init(data: image(500).pngData()!, delay: 0.18, status: 200)
        for url in zoomURLs.dropFirst() {
            await ImageCacheManager.shared.store(image: normal, data: normal.pngData()!, for: url)
        }
        let (zoomLoad, zoomScroll) = mount(zoomURLs, initial: 1)
        await settle(0.08)
        zoomLoad.toggleZoom(at: CGPoint(x: 200, y: 960))
        await settle(0.65)
        check(near(zoomScroll.zoomScale, 2.5), "late image completion does not cancel zoom animation")
        let zoomCanvas = zoomLoad.viewForZooming(in: zoomScroll)!
        let second = zoomCanvas.subviews.first(where: { $0.tag == 1 })!
        let fraction = (zoomScroll.contentOffset.y / zoomScroll.zoomScale - second.frame.minY) / second.frame.height
        check(abs(fraction - 240.0 / 560.0) < 0.001, "deferred height change preserves position after zoom finishes")

        zoomLoad.configure(urls: zoomURLs, initialPage: 0, jump: ReaderPageJump(page: 999))
        await settle()
        let lastPage = zoomCanvas.subviews.first(where: { $0.tag == 7 })!
        let lastPageOffset = min(lastPage.frame.minY * zoomScroll.zoomScale,
                                 zoomScroll.contentSize.height - zoomScroll.bounds.height)
        check(near(zoomScroll.contentOffset.y, lastPageOffset),
              "out-of-range jump clamps to the last page and bottom boundary")
        zoomLoad.configure(urls: [], initialPage: 0, jump: nil)
        await settle()
        check(zoomLoad.viewForZooming(in: zoomScroll)!.bounds.height == 0, "empty page update is safe")

        host?.stopLoading()
        record("\(checks - failures)/\(checks) reader regression checks passed")
        record(failures == 0 ? "HE_READER_TESTS_OK" : "HE_READER_TESTS_FAILED")
        fflush(stdout)
        exit(failures == 0 ? 0 : 1)
    }
}

@MainActor
final class ReaderRegressionScene: UIResponder, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene,
              let app = UIApplication.shared.delegate as? ReaderRegressionApp else { return }
        app.begin(in: scene)
    }
}
