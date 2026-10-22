package net.jkraemer.photos.media

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind

/**
 * The item's URI in its own collection. Trash requests reject the generic
 * Files URI, so images and videos each need their collection's URI.
 */
fun contentUriOf(item: MediaItem): Uri = ContentUris.withAppendedId(
    when (item.kind) {
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    },
    item.id,
)
