/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.theme.AcornCorners
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.feature.listentopage.ListenAction
import mozilla.components.feature.listentopage.R
import mozilla.components.ui.icons.R as iconsR

/** Listen to page audio player in compact state */
@Composable
fun PlayerCompact(
    article: ArticleDetails,
    progress: () -> Float,
    playing: Boolean,
    onAction: (ListenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(AcornCorners.extraLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(AcornTheme.layout.elevation.level2),
            border = BorderStroke(AcornTheme.layout.border.default, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier.padding(
                                top = AcornTheme.layout.space.static100,
                                end = AcornTheme.layout.space.static100,
                            )
                            .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onAction(ListenAction.Session.StopRequested) },
                        contentDescription = stringResource(R.string.mozac_feature_listentopage_close),
                    ) {
                        Icon(
                            painter = painterResource(iconsR.drawable.mozac_ic_cross_24),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = null,
                        )
                    }
                    ArticleHeading(article = article, modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { onAction(ListenAction.Controls.RewindClicked) },
                        contentDescription = stringResource(R.string.mozac_feature_listentopage_back_10_sec),
                    ) {
                        Icon(
                            painter = painterResource(iconsR.drawable.mozac_ic_playback_rewind_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    PlayPauseButton(playing, { onAction(ListenAction.Controls.PlayPauseClicked) })
                }
                // Reserves the space the progress bar occupies, since the bar itself is drawn as
                // an overlay on top of the card's border.
                Spacer(modifier = Modifier.height(AcornTheme.layout.space.static50 + MiniAudioProgressBarHeight))
            }
        }
        // AudioProgressBar draws the progress bar on top of the card, covering border, according to UI requirements
        AudioProgressBar(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = AcornTheme.layout.space.static400),
            type = AudioProgressBarType.Mini,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewPlayerCompact() {
    AcornTheme {
        PlayerCompact(
            article = ArticleDetails(title = "Match Preview: Wrexham AFC vs Sunderland AFC", site = "source"),
            progress = { 0.4f },
            playing = true,
            onAction = {},
        )
    }
}
