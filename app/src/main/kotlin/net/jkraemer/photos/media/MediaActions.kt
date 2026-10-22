package net.jkraemer.photos.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.provider.MediaStore
import net.jkraemer.photos.core.MediaItem

/** The system share sheet for [item], with read access granted to the receiving app. */
fun shareIntent(item: MediaItem): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND)
        .setType(item.mimeType)
        .putExtra(Intent.EXTRA_STREAM, contentUriOf(item))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    null,
)

/**
 * Asks the system to move [item] to its trash (purged after about 30 days).
 * The camera app owns the files, so Android shows its own confirmation
 * dialog; that dialog is the app's delete confirmation.
 */
fun trashRequest(resolver: ContentResolver, item: MediaItem): PendingIntent =
    MediaStore.createTrashRequest(resolver, listOf(contentUriOf(item)), true)
