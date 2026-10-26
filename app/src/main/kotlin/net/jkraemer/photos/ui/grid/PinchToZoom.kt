package net.jkraemer.photos.ui.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

// How far fingers must spread (or close) before the grid steps one level.
private const val ZOOM_IN_FACTOR = 1.35f
private const val ZOOM_OUT_FACTOR = 0.74f

/**
 * Reports pinches as discrete steps: [onStep] gets `true` for spreading
 * (closer, fewer columns) and the fingers' centroid. A gesture steps at most
 * once, however far the fingers travel: a natural pinch covers more than one
 * threshold, and stepping again would skip a level unseen. Only two-finger
 * movement is consumed; a one-finger drag still scrolls the grid. The
 * detector lives for the whole composition, so [onStep] must read current
 * state itself (e.g. through rememberUpdatedState).
 */
fun Modifier.pinchToZoom(onStep: (zoomIn: Boolean, focal: Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var scale = 1f
        var stepped = false
        do {
            // The Initial pass sees the pointers before the grid can scroll with them.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                scale *= event.calculateZoom()
                if (!stepped && (scale > ZOOM_IN_FACTOR || scale < ZOOM_OUT_FACTOR)) {
                    onStep(scale > ZOOM_IN_FACTOR, event.calculateCentroid())
                    stepped = true
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
