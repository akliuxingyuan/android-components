/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.base.theme.acornPrivateColorScheme
import mozilla.components.compose.base.theme.privateColorPalette
import mozilla.components.ui.icons.R as iconsR

/**
 * Default layout of a selectable chip.
 *
 * @param text [String] displayed in this chip.
 * @param selected Whether this chip should be shown as selected.
 * @param modifier [Modifier] used to be applied to the layout of the chip.
 * @param enabled Whether this chip is enabled.
 * @param showIcon Whether to show an icon at the beginning of the chip.
 * @param colors The color set defined by [SelectableChipColors] used to style the chip.
 * @param border The border to draw around the container of this chip.
 * @param onClick Callback for when the user taps this chip.
 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showIcon: Boolean = true,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = text,
                style = if (selected) AcornTheme.typography.headline8 else AcornTheme.typography.body2,
            )
        },
        leadingIcon = if (showIcon && selected) {
            {
                Icon(
                    painter = painterResource(id = iconsR.drawable.mozac_ic_checkmark_16),
                    contentDescription = null,
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        border = border,
    )
}

@Composable
@PreviewLightDark
private fun SelectableChipPreview() {
    AcornTheme {
        Surface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectableChip(text = "ChirpOne", selected = false) {}
                SelectableChip(text = "ChirpTwo", selected = true) {}
            }
        }
    }
}

@Composable
@Preview
private fun SelectableChipPrivatePreview() {
    AcornTheme(
        colors = privateColorPalette,
        colorScheme = acornPrivateColorScheme(),
    ) {
        Surface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectableChip(text = "ChirpOne", selected = false) {}
                SelectableChip(text = "ChirpTwo", selected = true) {}
            }
        }
    }
}

@Composable
@PreviewLightDark
private fun SelectableChipWithCustomColorsPreview() {
    AcornTheme {
        Surface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectableChip(
                    text = "Yellow",
                    selected = false,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Yellow,
                        containerColor = Color.DarkGray,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.Gray,
                    ),
                    onClick = {},
                )

                SelectableChip(
                    text = "Cyan",
                    selected = true,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Cyan,
                        containerColor = Color.DarkGray,
                        selectedLabelColor = Color.Red,
                        selectedLeadingIconColor = Color.Red,
                        labelColor = Color.Gray,
                    ),
                    onClick = {},
                )
            }
        }
    }
}
