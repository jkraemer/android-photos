package net.jkraemer.photos.core

import kotlin.math.roundToInt

/**
 * Timeline indices shown in the film strip: [radius] older items, [current]
 * in the centre slot, [radius] newer ones. Slots past either end of the
 * timeline are null, so the current item always stays centred.
 */
fun filmStripWindow(current: Int, count: Int, radius: Int = 5): List<Int?> =
    (current - radius..current + radius).map { if (it in 0 until count) it else null }

/**
 * The item a strip drag of [dragPx] lands on, one item per [slotWidthPx].
 * Dragging left (negative) pulls newer items into the centre, as on iOS.
 */
fun scrubTarget(startIndex: Int, dragPx: Float, slotWidthPx: Float, count: Int): Int =
    (startIndex - (dragPx / slotWidthPx).roundToInt()).coerceIn(0, count - 1)
