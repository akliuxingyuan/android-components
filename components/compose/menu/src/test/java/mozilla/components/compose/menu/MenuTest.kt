/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.compose.base.theme.AcornTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `WHEN the menu is shown THEN its entries are displayed`() {
        composeTestRule.setContent {
            AcornTheme {
                Menu(modifier = Modifier.testTag(MENU_TAG)) {
                    Text(text = "First entry")
                    Text(text = "Second entry")
                }
            }
        }

        assertTrue(composeTestRule.onNodeWithTag(MENU_TAG).isDisplayed())
        assertTrue(composeTestRule.onNodeWithText("First entry").isDisplayed())
        assertTrue(composeTestRule.onNodeWithText("Second entry").isDisplayed())
    }
}

private const val MENU_TAG = "menu"
