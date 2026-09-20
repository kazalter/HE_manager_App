import SwiftUI

public actor ImageCacheManager {
    public static let shared = ImageCacheManager()

    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL

    private init() {
        memoryCache.countLimit = 300
        memoryCache.totalCostLimit = 120 * 1024 * 1024 // 120MB memory

        let paths = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)
        cacheDirectory = paths[0].appendingPathComponent("he_cover_cache")
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
    }

    public func image(for urlString: String) -> UIImage? {
        if let mem = memoryCache.object(forKey: urlString as NSString) {
            return mem
        }
        let fileUrl = cacheFilePath(for: urlString)
        if let data = try? Data(contentsOf: fileUrl), let diskImg = UIImage(data: data) {
            memoryCache.setObject(diskImg, forKey: urlString as NSString, cost: decodedCost(diskImg))
            return diskImg
        }
        return nil
    }

    public func store(image: UIImage, data: Data, for urlString: String) {
        memoryCache.setObject(image, forKey: urlString as NSString, cost: decodedCost(image))
        let fileUrl = cacheFilePath(for: urlString)
        try? data.write(to: fileUrl)
    }

    public func clearDiskCache() {
        memoryCache.removeAllObjects()
        try? fileManager.removeItem(at: cacheDirectory)
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
    }

    public func diskCacheSizeMB() -> Double {
        guard let files = try? fileManager.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: [.fileSizeKey]) else {
            return 0.0
        }
        var totalBytes: Int64 = 0
        for file in files {
            if let resources = try? file.resourceValues(forKeys: [.fileSizeKey]), let size = resources.fileSize {
                totalBytes += Int64(size)
            }
        }
        return Double(totalBytes) / (1024.0 * 1024.0)
    }

    private func decodedCost(_ image: UIImage) -> Int {
        if let cgImage = image.cgImage { return cgImage.bytesPerRow * cgImage.height }
        return Int(image.size.width * image.scale * image.size.height * image.scale * 4)
    }

    private func cacheFilePath(for urlString: String) -> URL {
        let hash = String(urlString.hashValue)
        return cacheDirectory.appendingPathComponent(hash)
    }
}

public struct AsyncCoverImage: View {
    public let urlString: String?
    public let contentMode: ContentMode

    @State private var image: UIImage? = nil
    @State private var isLoading: Bool = false

    public init(urlString: String?, contentMode: ContentMode = .fill) {
        self.urlString = urlString
        self.contentMode = contentMode
    }

    public var body: some View {
        ZStack {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Rectangle()
                    .fill(OPTheme.surfaceAlt)
                    .overlay(
                        Group {
                            if isLoading {
                                ProgressView()
                                    .tint(OPTheme.yellow)
                            } else {
                                Image(systemName: "photo")
                                    .foregroundColor(OPTheme.opWhiteFaint)
                                    .font(.system(size: 24))
                            }
                        }
                    )
            }
        }
        .task(id: urlString) {
            await loadImage()
        }
    }

    private func loadImage() async {
        guard let urlString = urlString, !urlString.isEmpty else {
            image = nil
            return
        }

        if let cached = await ImageCacheManager.shared.image(for: urlString) {
            self.image = cached
            return
        }

        guard let url = URL(string: urlString) else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            if let loaded = UIImage(data: data) {
                await ImageCacheManager.shared.store(image: loaded, data: data, for: urlString)
                self.image = loaded
            }
        } catch {
            // failed
        }
    }
}
