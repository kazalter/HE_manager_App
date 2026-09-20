import SwiftUI
import UIKit

public struct ZoomableImageView: UIViewRepresentable {
    public let urlString: String
    public let onSingleTap: () -> Void

    public init(urlString: String, onSingleTap: @escaping () -> Void = {}) {
        self.urlString = urlString
        self.onSingleTap = onSingleTap
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        scrollView.delegate = context.coordinator
        scrollView.minimumZoomScale = 1.0
        scrollView.maximumZoomScale = 4.0
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false
        scrollView.backgroundColor = .clear

        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = true
        scrollView.addSubview(imageView)
        context.coordinator.imageView = imageView
        context.coordinator.scrollView = scrollView

        // Double tap to zoom
        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        scrollView.addGestureRecognizer(doubleTap)

        // Single tap for controls
        let singleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleSingleTap))
        singleTap.numberOfTapsRequired = 1
        singleTap.require(toFail: doubleTap)
        scrollView.addGestureRecognizer(singleTap)

        context.coordinator.loadImage(urlString: urlString)
        return scrollView
    }

    public func updateUIView(_ uiView: UIScrollView, context: Context) {
        if context.coordinator.currentUrl != urlString {
            context.coordinator.loadImage(urlString: urlString)
        }
    }

    public class Coordinator: NSObject, UIScrollViewDelegate {
        var parent: ZoomableImageView
        weak var scrollView: UIScrollView?
        weak var imageView: UIImageView?
        var currentUrl: String = ""

        init(_ parent: ZoomableImageView) {
            self.parent = parent
        }

        public func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            return imageView
        }

        public func scrollViewDidZoom(_ scrollView: UIScrollView) {
            guard let imageView = imageView else { return }
            let offsetX = max((scrollView.bounds.width - scrollView.contentSize.width) * 0.5, 0)
            let offsetY = max((scrollView.bounds.height - scrollView.contentSize.height) * 0.5, 0)
            imageView.center = CGPoint(x: scrollView.contentSize.width * 0.5 + offsetX,
                                       y: scrollView.contentSize.height * 0.5 + offsetY)
        }

        @objc func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
            guard let scrollView = scrollView else { return }
            if scrollView.zoomScale > 1.0 {
                scrollView.setZoomScale(1.0, animated: true)
            } else {
                let point = gesture.location(in: imageView)
                let zoomRect = CGRect(x: point.x - 50, y: point.y - 50, width: 100, height: 100)
                scrollView.zoom(to: zoomRect, animated: true)
            }
        }

        @objc func handleSingleTap() {
            parent.onSingleTap()
        }

        func loadImage(urlString: String) {
            self.currentUrl = urlString
            scrollView?.setZoomScale(1.0, animated: false)

            Task { @MainActor in
                if let cached = await ImageCacheManager.shared.image(for: urlString) {
                    self.applyImage(cached)
                    return
                }

                guard let url = URL(string: urlString) else { return }
                do {
                    let (data, _) = try await URLSession.shared.data(from: url)
                    if let img = UIImage(data: data) {
                        await ImageCacheManager.shared.store(image: img, data: data, for: urlString)
                        self.applyImage(img)
                    }
                } catch {
                    // ignore
                }
            }
        }

        private func applyImage(_ image: UIImage) {
            guard let imageView = imageView, let scrollView = scrollView else { return }
            imageView.image = image
            imageView.frame = CGRect(origin: .zero, size: scrollView.bounds.size)
            scrollView.contentSize = scrollView.bounds.size
            self.scrollViewDidZoom(scrollView)
        }
    }
}
