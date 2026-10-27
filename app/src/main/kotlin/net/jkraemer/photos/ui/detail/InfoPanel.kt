package net.jkraemer.photos.ui.detail

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.InfoText
import net.jkraemer.photos.core.coordinates
import net.jkraemer.photos.core.findLinks

private val CARD_COLOR = Color(0xFF1C1C1E)
private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val SECONDARY = Color(0xFF98989F)

/** Where reading a photo's text stands; [Done] with an empty text means the photo has none. */
sealed interface TextRecognition {
    data object NotRun : TextRecognition
    data object Running : TextRecognition
    data class Done(val text: String) : TextRecognition
    data object Failed : TextRecognition
}

/**
 * What the file says about the item on screen, as dark cards in the style of
 * iOS Photos. A missing field leaves its line out; [info] of null (still
 * reading) draws nothing. The location needs ACCESS_MEDIA_LOCATION: without
 * [locationGranted] Android hides whether there is one, so the panel offers
 * to ask instead. [text] of null (a video) leaves out text recognition;
 * links in recognised text go to [onOpenLink] as URIs. The caller owns
 * scrolling.
 */
@Composable
fun InfoPanel(
    info: InfoText?,
    placeName: String?,
    locationGranted: Boolean,
    onShowOnMap: (GeoPoint) -> Unit,
    onShowLocation: () -> Unit,
    text: TextRecognition?,
    onRecognise: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (info == null) return
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Text(info.date, Modifier.testTag("info-date"), color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(info.fileName, Modifier.testTag("info-file"), color = SECONDARY, fontSize = 14.sp)
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info.camera.orEmpty(),
                    Modifier.weight(1f).then(if (info.camera != null) Modifier.testTag("info-camera") else Modifier),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    info.badge,
                    Modifier
                        .border(1.dp, SECONDARY, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .testTag("info-badge"),
                    color = SECONDARY,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            info.lens?.let { Text(it, Modifier.testTag("info-lens"), color = SECONDARY, fontSize = 14.sp) }
            info.dimensions?.let { Text(it, Modifier.testTag("info-dimensions"), color = SECONDARY, fontSize = 14.sp) }
            if (info.exposure.isNotEmpty()) ExposureRow(info.exposure)
        }
        val location = info.location
        when {
            !locationGranted -> ActionCard(Icons.Filled.LocationOn, "Show location", Modifier.testTag("info-show-location"), onShowLocation)
            location != null -> ActionCard(Icons.Filled.LocationOn, placeName ?: coordinates(location), Modifier.testTag("info-location")) {
                onShowOnMap(location)
            }
        }
        when (text) {
            null -> {}
            TextRecognition.NotRun -> ActionCard(Icons.Filled.Search, "Recognise text", Modifier.testTag("info-recognise-text"), onRecognise)
            TextRecognition.Running -> Card(Modifier.testTag("info-text-running")) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Color.White)
            }
            TextRecognition.Failed -> Card {
                Text("Couldn't recognise the text", Modifier.testTag("info-text-failed"), color = SECONDARY)
            }
            is TextRecognition.Done ->
                if (text.text.isEmpty()) Card { Text("No text found", Modifier.testTag("info-text-none"), color = SECONDARY) }
                else RecognisedTextCard(text.text, onOpenLink)
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(CARD_COLOR, CARD_SHAPE).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** ISO, focal length, compensation, aperture and shutter in equal cells divided by thin lines. */
@Composable
private fun ExposureRow(entries: List<String>) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min).testTag("info-exposure"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(SECONDARY.copy(alpha = 0.4f)))
            Text(entry, Modifier.weight(1f), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** The recognised text, selectable, with its phone numbers, emails and URLs as links. */
@Composable
private fun RecognisedTextCard(text: String, onOpenLink: (String) -> Unit) {
    val openLink by rememberUpdatedState(onOpenLink)
    val linked = remember(text) {
        buildAnnotatedString {
            append(text)
            for (link in findLinks(text)) {
                val annotation = LinkAnnotation.Clickable(
                    link.target,
                    TextLinkStyles(SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline)),
                ) { openLink(link.target) }
                addLink(annotation, link.range.first, link.range.last + 1)
            }
        }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Card {
        SelectionContainer { Text(linked, Modifier.testTag("info-text"), color = Color.White) }
        TextButton(
            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Text", text))) } },
            Modifier.align(Alignment.End).testTag("info-text-copy"),
        ) { Text("Copy", color = Color.White) }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(CARD_COLOR)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text(text, Modifier.padding(start = 12.dp), color = Color.White)
    }
}
