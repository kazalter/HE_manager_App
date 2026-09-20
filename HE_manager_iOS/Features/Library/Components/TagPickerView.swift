import SwiftUI

public struct TagPickerView: View {
    public let item: MediaItem
    public let allTags: [TagItem]
    public let onAddTag: (String) -> Void
    public let onDismiss: () -> Void

    @State private var newTagName: String = ""

    public var body: some View {
        NavigationStack {
            ZStack {
                OPTheme.void.ignoresSafeArea()

                VStack(alignment: .leading, spacing: 18) {
                    Text("TAGS FOR: \(item.title)")
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.yellowDim)
                        .lineLimit(1)

                    // Add custom new tag input
                    HStack(spacing: 8) {
                        TextField("New Tag Name", text: $newTagName)
                            .font(.system(size: 13, design: .monospaced))
                            .foregroundColor(OPTheme.opWhite)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(OPTheme.panel)
                            .clipShape(RoundedRectangle(cornerRadius: 4))

                        YellowCTA(title: "ADD", size: .small) {
                            guard !newTagName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                            onAddTag(newTagName.trimmingCharacters(in: .whitespacesAndNewlines))
                            newTagName = ""
                        }
                    }

                    Text("EXISTING SYSTEM TAGS")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(OPTheme.opWhiteMuted)

                    ScrollView {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 90))], spacing: 8) {
                            ForEach(allTags) { tag in
                                let hasTag = item.tags.contains(where: { $0.id == tag.id || $0.name == tag.name })
                                Button {
                                    if !hasTag {
                                        onAddTag(tag.name)
                                    }
                                } label: {
                                    HStack(spacing: 4) {
                                        Text("#\(tag.name)")
                                            .font(.system(size: 11, design: .monospaced))
                                        if hasTag {
                                            Image(systemName: "checkmark")
                                                .font(.system(size: 9, weight: .bold))
                                        }
                                    }
                                    .foregroundColor(hasTag ? OPTheme.onYellow : OPTheme.opWhiteSoft)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 6)
                                    .frame(maxWidth: .infinity)
                                    .background(hasTag ? OPTheme.yellow : OPTheme.panel)
                                    .clipShape(RoundedRectangle(cornerRadius: 4))
                                }
                            }
                        }
                    }

                    Spacer()
                }
                .padding(20)
            }
            .navigationTitle("EDIT TAGS")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("CLOSE") {
                        onDismiss()
                    }
                    .foregroundColor(OPTheme.yellow)
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                }
            }
        }
    }
}
