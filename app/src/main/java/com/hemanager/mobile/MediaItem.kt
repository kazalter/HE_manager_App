package com.hemanager.mobile

import org.json.JSONObject

data class MediaItem(
    @JvmField val id: Int = 0,
    @JvmField val title: String = "Untitled",
    @JvmField val mediaType: String = "",
    @JvmField val extension: String = "",
    @JvmField val coverPath: String = "",
    @JvmField val duration: Int = 0,
    @JvmField val pageCount: Int = 0,
    @JvmField val progress: Int = 0,
    @JvmField val rating: Int = 0,
    @JvmField val favorite: Boolean = false,
    @JvmField val viewStatus: String = "unviewed",
    @JvmField val missing: Boolean = false,
    /** Null or blank means local media; non-empty values are external source sites. */
    @JvmField val sourceSite: String? = null,
    @JvmField val createdAt: String = "",
    @JvmField val lastOpenedAt: String = "",
    @JvmField val tags: List<TagItem> = emptyList(),
) {
    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject): MediaItem {
            val sourceSite = if (json.isNull("source_site")) {
                null
            } else {
                json.optString("source_site", "").trim().ifBlank { null }
            }
            val tags = buildList {
                val tagsArray = json.optJSONArray("tags") ?: return@buildList
                for (i in 0 until tagsArray.length()) {
                    val tagJson = tagsArray.optJSONObject(i)
                    if (tagJson != null) add(TagItem.fromJson(tagJson))
                }
            }

            return MediaItem(
                id = json.optInt("id"),
                title = json.optString("title", "Untitled"),
                mediaType = json.optString("media_type", ""),
                extension = json.optString("extension", ""),
                coverPath = json.optString("cover_path", ""),
                duration = json.optInt("duration", 0),
                pageCount = json.optInt("page_count", 0),
                progress = json.optInt("progress", 0),
                rating = json.optInt("rating", 0),
                favorite = json.optBoolean("favorite", false),
                viewStatus = json.optString("view_status", "unviewed"),
                missing = json.optBoolean("is_missing", false),
                sourceSite = sourceSite,
                createdAt = nullableString(json, "created_at"),
                lastOpenedAt = nullableString(json, "last_opened_at"),
                tags = tags,
            )
        }

        private fun nullableString(json: JSONObject, key: String): String {
            if (json.isNull(key)) return ""
            return json.optString(key, "").trim()
        }
    }
}
