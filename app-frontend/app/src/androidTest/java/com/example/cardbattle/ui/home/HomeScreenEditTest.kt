package com.example.cardbattle.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.cardbattle.ui.theme.CardBattleTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class HomeScreenEditTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(onSave: suspend (String) -> Unit = {}) {
        compose.setContent {
            var currentName by remember { mutableStateOf("Old Name") }
            CardBattleTheme {
                HomeScreen(
                    displayName = currentName,
                    onSaveDisplayName = { newName ->
                        onSave(newName)
                        currentName = newName
                    },
                )
            }
        }
    }

    private fun openNameEditor() {
        compose.onNodeWithText("Your profile").performClick()
        compose.onNodeWithText("Edit name").performClick()
        compose.onNodeWithText("Edit display name").assertIsDisplayed()
    }

    @Test
    fun saveTrimsNameAndOnlyShowsItAfterBackendSucceeds() {
        val pending = CompletableDeferred<Unit>()
        var attemptedName: String? = null
        show { name ->
            attemptedName = name
            pending.await()
        }

        openNameEditor()
        compose.onNode(hasSetTextAction()).performTextReplacement("  Captain  ")
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(5_000) { attemptedName != null }
        compose.runOnIdle { assertEquals("Captain", attemptedName) }
        compose.onNodeWithText("Old Name").assertIsDisplayed()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()

        compose.runOnIdle { pending.complete(Unit) }
        compose.onNodeWithText("Captain").assertIsDisplayed()
        compose.onNodeWithText("Edit display name").assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()

        // The menu is closed after saving; reopening the editor uses the saved profile name.
        openNameEditor()
        compose.onNode(hasSetTextAction()).assertTextContains("Captain")
    }

    @Test
    fun cancelKeepsOriginalNameWithoutSaving() {
        var attemptedName: String? = null
        show { attemptedName = it }

        openNameEditor()
        compose.onNode(hasSetTextAction()).performTextReplacement("Changed")
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("Old Name").assertIsDisplayed()
        compose.onNodeWithText("Edit display name").assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.runOnIdle { assertNull(attemptedName) }

        openNameEditor()
        compose.onNode(hasSetTextAction()).assertTextContains("Old Name")
    }

    @Test
    fun blankNameCannotBeSaved() {
        var attemptedName: String? = null
        show { attemptedName = it }

        openNameEditor()
        compose.onNode(hasSetTextAction()).performTextReplacement("   ")

        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.runOnIdle { assertNull(attemptedName) }
    }

    @Test
    fun failedSaveKeepsEditorOpenAndShowsError() {
        show { throw IOException("Backend unavailable") }

        openNameEditor()
        compose.onNode(hasSetTextAction()).performTextReplacement("Changed")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Could not save name. Please try again.").assertIsDisplayed()
        compose.onNodeWithText("Old Name").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }
}
