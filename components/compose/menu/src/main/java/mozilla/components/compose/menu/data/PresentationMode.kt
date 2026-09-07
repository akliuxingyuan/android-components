/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu.data

/** How the [MenuItem] should be shown inside a [MenuItemsGroup]. */
sealed class PresentationMode {

    /** Show the items in a row. */
    data object Row : PresentationMode()

    /** Show the items in a grid. */
    data object Grid : PresentationMode()
}
