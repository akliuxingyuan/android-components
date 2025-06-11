/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.awesomebar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Contains the default values used by the AwesomeBar.
 */
object AwesomeBarDefaults {
    /**
     * Creates an [AwesomeBarColors] that represents the default colors used in an AwesomeBar.
     *
     * @param background The background of the AwesomeBar.
     * @param title The text color for the title of a suggestion.
     * @param description The text color for the description of a suggestion.
     */
    @Composable
    fun colors(
        background: Color = MaterialTheme.colorScheme.background,
        title: Color = MaterialTheme.colorScheme.onBackground,
        description: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        autocompleteIcon: Color = MaterialTheme.colorScheme.onSurface,
        groupTitle: Color = MaterialTheme.colorScheme.onBackground,
    ) = AwesomeBarColors(
        background,
        title,
        description,
        autocompleteIcon,
        groupTitle,
    )
}
