package net.jkraemer.photos

import coil3.ImageLoader
import coil3.ColorImage
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import org.robolectric.RuntimeEnvironment

/**
 * Makes every image request resolve to a solid [width]x[height] image, so
 * screens lay out like on a device without decoding real media.
 */
@OptIn(DelicateCoilApi::class)
fun useFakeImages(width: Int = 400, height: Int = 300) {
    val engine = FakeImageLoaderEngine.Builder()
        .default(ColorImage(color = 0xFF808080.toInt(), width = width, height = height))
        .build()
    SingletonImageLoader.setUnsafe(
        ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(engine) }.build(),
    )
}
