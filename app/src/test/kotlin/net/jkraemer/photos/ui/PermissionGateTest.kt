package net.jkraemer.photos.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.jkraemer.photos.core.MediaAccess
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PermissionGateTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<String>()

    private fun show(access: MediaAccess, canRequest: Boolean = true) = compose.setContent {
        PermissionGate(
            access,
            canRequest,
            onRequest = { calls += "request" },
            onOpenSettings = { calls += "settings" },
        ) {
            Text("the grid")
        }
    }

    @Test fun `full access shows the content`() {
        show(MediaAccess.FULL)
        compose.onNodeWithText("the grid").assertExists()
        compose.onNodeWithText("Allow access").assertDoesNotExist()
    }

    @Test fun `no access asks for it`() {
        show(MediaAccess.NONE)
        compose.onNodeWithText("the grid").assertDoesNotExist()
        compose.onNodeWithText("Allow access").performClick()
        assertEquals(listOf("request"), calls)
    }

    @Test fun `partial access sends to settings`() {
        show(MediaAccess.PARTIAL)
        compose.onNodeWithText("the grid").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(listOf("settings"), calls)
    }

    @Test fun `no access after a permanent denial sends to settings`() {
        show(MediaAccess.NONE, canRequest = false)
        compose.onNodeWithText("Allow access").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()
        assertEquals(listOf("settings"), calls)
    }
}
