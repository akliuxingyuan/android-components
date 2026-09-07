/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.menu.data.MenuItemsGroup
import mozilla.components.compose.menu.data.PresentationMode.Row
import mozilla.components.compose.menu.store.MenuEvent
import mozilla.components.compose.menu.store.MenuState
import mozilla.components.compose.menu.store.MenuStore
import mozilla.components.compose.menu.ui.ListMenuItemsGroup
import mozilla.components.compose.menu.ui.utils.MenuPreviewParameterProvider

/**
 * A vertically scrollable container for menu items.
 *
 * @param store The [MenuStore] backing this menu.
 * @param modifier [Modifier] to be applied to the menu container.
 */
@Composable
fun Menu(
    store: MenuStore,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val onInteraction: (MenuEvent) -> Unit = remember(store) { { store.dispatch(it) } }

        LazyColumn(
            modifier = Modifier.padding(AcornTheme.layout.space.static100),
            verticalArrangement = Arrangement.spacedBy(AcornTheme.layout.space.static150),
        ) {
            items(store.state.menuGroups) {
                if (it.presentationMode == Row) {
                    ListMenuItemsGroup(it.items, onInteraction)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MenuPreview(@PreviewParameter(MenuPreviewParameterProvider::class) menuGroups: List<MenuItemsGroup>) {
    AcornTheme {
        Menu(store = MenuStore(MenuState(menuGroups)))
    }
}
