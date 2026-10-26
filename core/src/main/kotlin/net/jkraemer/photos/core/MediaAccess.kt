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

/**
 * What to do about ACCESS_MEDIA_LOCATION, which the info panel needs to show
 * where a photo was taken. Nothing is stored: Android's own flags say whether
 * the user said no before.
 */
enum class LocationAsk {
    NOTHING, REQUEST, SETTINGS;

    companion object {
        /**
         * Opening the panel asks by itself only while the user has never said
         * no ([deniedBefore] is Android's rationale flag) and only once a run.
         */
        fun onPanelOpen(granted: Boolean, deniedBefore: Boolean, askedThisRun: Boolean): LocationAsk =
            if (!granted && !deniedBefore && !askedThisRun) REQUEST else NOTHING

        /** The "Show location" row asks while Android still prompts, then sends to settings. */
        fun onShowLocation(granted: Boolean, canRequest: Boolean): LocationAsk = when {
            granted -> NOTHING
            canRequest -> REQUEST
            else -> SETTINGS
        }
    }
}
