/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.menu.DropdownMenu
import mozilla.components.compose.base.menu.MenuItem
import mozilla.components.compose.base.menu.MenuItem.TextItem
import mozilla.components.compose.base.text.Text
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.feature.listentopage.Voice

/** UI to allow the user to manage their selected voice for narration. */
@Composable
fun VoiceSelection(
    expanded: Boolean,
    availableVoices: List<Voice>,
    onVoiceClick: (Voice) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val menuItems = availableVoices.toMenuItems(onVoiceClick)
    DropdownMenu(expanded = expanded, menuItems = menuItems, onDismissRequest = onDismissRequest)
}

private fun List<Voice>.toMenuItems(onClick: (Voice) -> Unit): List<MenuItem> = map { voice ->
    TextItem(text = Text.String(voice.id), onClick = { onClick(voice) })
}

// Dropdown menus are currently only previewable in interactive mode - give it a shot if you don't see anything
@PreviewLightDark
@Composable
private fun PreviewVoiceSelection() {
    AcornTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            VoiceSelection(
                expanded = true,
                availableVoices = listOf("Darth Vader", "Smeagol", "Hulk").map { Voice(it) },
                onVoiceClick = {},
                onDismissRequest = {},
            )
        }
    }
}
