/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.theme.AcornTheme

/**
 * Themed container laying out the entries of a menu vertically.
 *
 * @param modifier [Modifier] to be applied to the menu container.
 * @param content The entries of this menu, laid out vertically in the order they are declared.
 */
@Composable
fun Menu(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = modifier.padding(vertical = AcornTheme.layout.space.static100),
            content = content,
        )
    }
}

@PreviewLightDark
@Composable
private fun MenuPreview() {
    AcornTheme {
        Menu {
            Text(
                modifier = Modifier.padding(all = AcornTheme.layout.space.static200),
                text = "Menu entry",
                style = AcornTheme.typography.body1,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
