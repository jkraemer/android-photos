package net.jkraemer.photos.core

/**
 * How much of the camera roll the app may read. Partial access ("Select
 * photos") is not enough: the app's point is the complete camera roll.
 */
enum class MediaAccess {
    FULL, PARTIAL, NONE;

    companion object {
        fun of(images: Boolean, videos: Boolean, userSelected: Boolean): MediaAccess = when {
            images && videos -> FULL
            images || videos || userSelected -> PARTIAL
            else -> NONE
        }
    }
}
