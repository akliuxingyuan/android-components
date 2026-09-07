/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu.data

import androidx.annotation.DrawableRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.Role.Companion.Button
import mozilla.components.compose.base.text.Text
import mozilla.components.compose.menu.store.MenuEvent
import mozilla.components.compose.menu.ui.MenuItemIcon
import mozilla.components.compose.menu.ui.MenuItemState
import mozilla.components.compose.menu.ui.MenuItemState.DEFAULT

/**
 * Configuration of a menu item.
 *
 * @param title The title of the menu item.
 * @param contentDescription The content description of the menu item.
 * @param onClickEvent [MenuEvent] to dispatch when the menu item is clicked.
 * @param role The [Role] of the menu item.
 * @param summary An optional summary of the menu item.
 * @param icon An optional icon of the menu item.
 * @param showNewIndicator Whether to show a new indicator.
 * @param badge An optional badge to show.
 * @param actionButton An optional action button to show.
 * @param state The state of this menu item.
 */
data class MenuItem(
    val title: Text,
    val contentDescription: Text,
    val onClickEvent: MenuEvent,
    val role: Role = Button,
    val summary: MenuItemSummary? = null,
    val icon: MenuItemIcon? = null,
    val showNewIndicator: Boolean = false,
    val badge: MenuItemBadge? = null,
    val actionButton: MenuItemActionButton? = null,
    val state: MenuItemState = DEFAULT,
)

/**
 * Configuration of the summary text of a [MenuItem].
 *
 * @param text The text to show.
 * @param state The [MenuItemState] of this summary.
 */
data class MenuItemSummary(
    val text: Text,
    val state: MenuItemState = DEFAULT,
)

/**
 * Configuration of a badge to shown in a [MenuItem].
 *
 * @param text The text to show.
 * @param state The [MenuItemState] of this badge.
 */
data class MenuItemBadge(
    val text: Text,
    val state: MenuItemState = DEFAULT,
)

/**
 * Configuration of an action button to shown in a [MenuItem].
 *
 * @param icon The icon to show.
 * @param contentDescription The content description of this button.
 * @param onClickEvent The [MenuEvent] to dispatch when this button is clicked.
 * @param showDivider Whether to show a divider before this button.
 * @param state The [MenuItemState] of this action button.
 */
data class MenuItemActionButton(
    @DrawableRes val icon: Int,
    val contentDescription: Text,
    val onClickEvent: MenuEvent,
    val showDivider: Boolean = false,
    val state: MenuItemState = DEFAULT,
)
