/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu.ui.utils

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import mozilla.components.compose.menu.Menu
import mozilla.components.compose.menu.data.MenuItemsGroup
import mozilla.components.compose.menu.data.PresentationMode.Row

/** [MenuItemsGroup]s to be used in the preview of the [Menu] composable. */
internal class MenuPreviewParameterProvider : PreviewParameterProvider<List<MenuItemsGroup>> {
    override val values =
        sequenceOf(
            listOf(
                MenuItemsGroup(
                    id = "1",
                    presentationMode = Row,
                    items = mainSectionMenuItemsPreviewData,
                )
            )
        )
}
