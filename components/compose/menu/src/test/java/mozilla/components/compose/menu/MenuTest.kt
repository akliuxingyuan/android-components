/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.compose.base.text.Text
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.menu.data.MenuItemsGroup
import mozilla.components.compose.menu.data.StandardMenuItem
import mozilla.components.compose.menu.store.MenuAction
import mozilla.components.compose.menu.store.MenuEvent
import mozilla.components.compose.menu.store.MenuState
import mozilla.components.compose.menu.store.MenuStore
import mozilla.components.compose.menu.ui.MenuItemIconRes
import mozilla.components.ui.icons.R as iconsR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `GIVEN a short menu with a footer THEN wrap content instead of filling the maximum height`() {
        setMenu(listOf(row("Item"), stickyGroup("Footer")))

        composeTestRule.onNodeWithText("Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Footer").assertIsDisplayed()
        val menuBounds = composeTestRule.onNodeWithTag("menu").getUnclippedBoundsInRoot()
        val listBounds = composeTestRule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val footerBounds = composeTestRule.onNodeWithText("Footer").getUnclippedBoundsInRoot()
        assertTrue(menuBounds.height < 300.dp)
        assertTrue(listBounds.bottom <= footerBounds.top)
    }

    @Test
    fun `GIVEN a long menu WHEN scrolling to the end THEN keep header and footer visible without covering content`() {
        setMenu(listOf(stickyGroup("Header")) + List(20) { row("Item $it") } + stickyGroup("Footer"))
        val headerBefore = composeTestRule.onNodeWithText("Header").getUnclippedBoundsInRoot()
        val footerBefore = composeTestRule.onNodeWithText("Footer").getUnclippedBoundsInRoot()

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Item 19"))

        composeTestRule.onNodeWithText("Header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Footer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Item 19").assertIsDisplayed()
        val headerAfter = composeTestRule.onNodeWithText("Header").getUnclippedBoundsInRoot()
        val footerAfter = composeTestRule.onNodeWithText("Footer").getUnclippedBoundsInRoot()
        val listBounds = composeTestRule.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val lastItemBounds = composeTestRule.onNodeWithText("Item 19").getUnclippedBoundsInRoot()
        assertEquals(headerBefore.top, headerAfter.top)
        assertEquals(footerBefore.bottom, footerAfter.bottom)
        assertTrue(listBounds.bottom <= footerAfter.top)
        assertTrue(lastItemBounds.bottom <= listBounds.bottom)
    }

    @Test
    fun `WHEN a footer is added and removed THEN restore the original menu height`() {
        val groups = listOf(row("Item"))
        val store = setMenu(groups)
        val originalHeight = composeTestRule.onNodeWithTag("menu").getUnclippedBoundsInRoot().height

        composeTestRule.runOnIdle { store.dispatch(MenuAction.Update(groups + stickyGroup("Footer"))) }

        composeTestRule.onNodeWithText("Footer").assertIsDisplayed()
        assertTrue(composeTestRule.onNodeWithTag("menu").getUnclippedBoundsInRoot().height > originalHeight)

        composeTestRule.runOnIdle { store.dispatch(MenuAction.Update(groups)) }

        composeTestRule.onNodeWithText("Footer").assertDoesNotExist()
        assertEquals(originalHeight, composeTestRule.onNodeWithTag("menu").getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `GIVEN a single sticky group THEN show it only once`() {
        setMenu(listOf(stickyGroup("Header")))

        composeTestRule.onAllNodesWithText("Header").assertCountEquals(1)
        composeTestRule.onNodeWithText("Header").assertIsDisplayed()
        assertTrue(composeTestRule.onNodeWithTag("menu").getUnclippedBoundsInRoot().height < 300.dp)
    }

    private fun setMenu(groups: List<MenuItemsGroup>): MenuStore {
        val store = MenuStore(initialState = MenuState(groups))
        composeTestRule.setContent {
            AcornTheme {
                Box(Modifier.fillMaxSize()) {
                    Menu(store, Modifier.width(320.dp).heightIn(max = 300.dp).testTag("menu"))
                }
            }
        }
        return store
    }

    private fun row(title: String) = MenuItemsGroup.Row(id = title, items = listOf(item(title)))

    private fun stickyGroup(title: String) =
        MenuItemsGroup.Grid(id = title, items = listOf(item(title)), isSticky = true)

    private fun item(title: String) =
        StandardMenuItem(
            title = Text.String(title),
            icon = MenuItemIconRes(iconsR.drawable.mozac_ic_settings_24),
            onClickEvent = object : MenuEvent {},
        )
}
