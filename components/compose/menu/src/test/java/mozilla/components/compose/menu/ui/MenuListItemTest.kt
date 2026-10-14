/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.compose.base.text.Text
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.menu.store.MenuEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuListItemTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val events = mutableListOf<MenuEvent>()

    @Test
    fun `GIVEN an item with a click event WHEN clicking it THEN dispatch that event`() {
        setItem(onClickEvent = Clicked)

        composeTestRule.onNodeWithText(TITLE).assertHasClickAction().performClick()

        assertEquals(listOf<MenuEvent>(Clicked), events)
    }

    @Test
    fun `GIVEN an item with a click event THEN announce it as a button`() {
        setItem(onClickEvent = Clicked)

        composeTestRule
            .onNodeWithText(TITLE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun `GIVEN an item without a click event THEN it cannot be clicked and is not announced as a button`() {
        setItem(onClickEvent = null)

        composeTestRule
            .onNodeWithText(TITLE)
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test
    fun `GIVEN an item without a click event WHEN clicking it THEN dispatch nothing`() {
        setItem(onClickEvent = null)

        composeTestRule.onNodeWithText(TITLE).performClick()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `GIVEN a disabled item with a click event WHEN clicking it THEN dispatch nothing`() {
        setItem(onClickEvent = Clicked, state = MenuItemState.DISABLED)

        composeTestRule.onNodeWithText(TITLE).assertHasNoClickAction().performClick()

        assertTrue(events.isEmpty())
    }

    private fun setItem(
        onClickEvent: MenuEvent?,
        state: MenuItemState = MenuItemState.DEFAULT,
    ) {
        composeTestRule.setContent {
            AcornTheme {
                MenuListItem(
                    title = Text.String(TITLE),
                    onClickEvent = onClickEvent,
                    onInteraction = { events += it },
                    contentDescription = null,
                    state = state,
                )
            }
        }
    }

    private data object Clicked : MenuEvent

    private companion object {
        const val TITLE = "Item"
    }
}
