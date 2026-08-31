/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.awesomebar.internal

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.browser.awesomebar.AwesomeBarTestTags
import mozilla.components.support.ktx.util.URLStringUtils
import mozilla.components.ui.icons.R as iconsR

/**
 * A composable that displays the current tab details in the AwesomeBar.
 *
 * @param currentTabData The current tab details to be displayed.
 * @param modifier The modifier to be applied to the composable.
 */
@Composable
internal fun CurrentTabDetails(
    currentTabData: CurrentTabData,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraLarge.copy(CornerSize(56.dp)),
                )
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clearAndSetSemantics {
                    contentDescription =
                        when (currentTabData.title.isNotBlank()) {
                            true -> currentTabData.title
                            else -> currentTabData.url
                        }
                    testTag = AwesomeBarTestTags.CURRENT_SITE_DETAILS
                }
                .padding(
                    horizontal = AcornTheme.layout.space.static50,
                    vertical = AcornTheme.layout.space.static100,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = currentTabData.icon

        Image(
            painter =
                when (icon) {
                    null -> painterResource(iconsR.drawable.mozac_ic_globe_24)
                    else -> remember(icon) { BitmapPainter(icon.asImageBitmap()) }
                },
            contentDescription = null,
            colorFilter =
                when (icon) {
                    null -> ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> null
                },
            modifier =
                Modifier.padding(AcornTheme.layout.space.static100)
                    .size(AcornTheme.layout.space.static400)
                    .padding(AcornTheme.layout.space.static50),
        )

        Column {
            if (currentTabData.title.isNotBlank()) {
                Text(
                    text = currentTabData.title,
                    style = AcornTheme.typography.body1,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Text(
                text = URLStringUtils.toDisplayUrl(currentTabData.url).toString(),
                style = AcornTheme.typography.body2,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun CurrentTabDetailsPreview() {
    AcornTheme {
        Surface(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            CurrentTabDetails(
                CurrentTabData(
                    title = "Headline",
                    url = "https://www.mozilla.org/firefox",
                    icon = null,
                )
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun CurrentTabDetailsWithUrlOnlyPreview() {
    AcornTheme {
        Surface(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            CurrentTabDetails(
                CurrentTabData(
                    title = "",
                    url = "https://www.mozilla.org/firefox",
                    icon = null,
                )
            )
        }
    }
}

/**
 * Current tab details to be shown in the AwesomeBar.
 *
 * @property title The title of the current tab.
 * @property url The URL of the current tab.
 * @property icon The icon of the current tab.
 */
data class CurrentTabData(
    val title: String,
    val url: String,
    val icon: Bitmap?,
)
