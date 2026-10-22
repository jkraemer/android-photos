package net.jkraemer.photos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.jkraemer.photos.core.MediaAccess

/**
 * Shows [content] only with full media access. Without any access the button
 * asks for it while Android still shows the prompt ([canRequest]). With
 * partial access ("Select photos"), or after the user denied it for good,
 * Android no longer shows the full prompt, so the button goes to the app's
 * settings instead.
 */
@Composable
fun PermissionGate(
    access: MediaAccess,
    canRequest: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (access == MediaAccess.FULL) {
        content()
        return
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Photos shows your whole camera roll and needs access to all photos and videos.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (access == MediaAccess.NONE && canRequest) {
            Button(onClick = onRequest) { Text("Allow access") }
        } else {
            Button(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}
