package net.jkraemer.photos.media

import android.content.ContentResolver
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** A square thumbnail of [uri], about [sizePx] on each side. */
data class MediaThumbnail(val uri: Uri, val sizePx: Int)

/**
 * Rounds a cell's pixel width up to a multiple of 64, so neighbouring zoom
 * levels and orientations share cached thumbnails.
 */
fun thumbnailSizePx(cellPx: Int): Int = (cellPx + 63) / 64 * 64

/**
 * Loads thumbnails through MediaStore's own thumbnail cache, which covers
 * videos too and keeps a grid of tens of thousands of items from decoding
 * full-size files.
 */
class MediaThumbnailFetcher(
    private val thumbnail: MediaThumbnail,
    private val resolver: ContentResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult = ImageFetchResult(
        image = resolver.loadThumbnail(thumbnail.uri, Size(thumbnail.sizePx, thumbnail.sizePx), null).asImage(),
        isSampled = true,
        dataSource = DataSource.DISK,
    )

    class Factory : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options.context.contentResolver)
    }
}

/** Without a key Coil skips its memory cache for custom models. */
class MediaThumbnailKeyer : Keyer<MediaThumbnail> {
    override fun key(data: MediaThumbnail, options: Options): String = "${data.uri}#${data.sizePx}"
}
