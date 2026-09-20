import SwiftUI

public struct PagedReaderView: View {
    public let totalPages: Int
    @Binding public var currentPage: Int
    public let pageUrlBuilder: (Int) -> String
    public let onSingleTap: () -> Void

    public var body: some View {
        TabView(selection: $currentPage) {
            ForEach(0..<max(1, totalPages), id: \.self) { pageIndex in
                ZoomableImageView(
                    urlString: pageUrlBuilder(pageIndex),
                    onSingleTap: onSingleTap
                )
                .tag(pageIndex)
                .ignoresSafeArea()
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(Color.black)
    }
}

public struct WebtoonReaderView: View {
    public let totalPages: Int
    @Binding public var currentPage: Int
    public let jump: ReaderPageJump?
    public let pageUrlBuilder: (Int) -> String
    public let onSingleTap: () -> Void

    public var body: some View {
        WebtoonZoomContainer(
            urls: (0..<max(1, totalPages)).map(pageUrlBuilder),
            initialPage: currentPage,
            jump: jump,
            onPageChange: { currentPage = $0 },
            onSingleTap: onSingleTap
        )
        .ignoresSafeArea()
        .background(Color.black)
    }
}
