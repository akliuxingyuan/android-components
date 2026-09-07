/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu.data

/**
 * Group of menu items to be shown in a [Menu].
 *
 * @property id A unique identifier for this group.
 * @property presentationMode How the items in this group should be shown.
 * @property items The items to show in this group.
 */
data class MenuItemsGroup(
    val id: String,
    val presentationMode: PresentationMode,
    val items: List<MenuItem>,
)
