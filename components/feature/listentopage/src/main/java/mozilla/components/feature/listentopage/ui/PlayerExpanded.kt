/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.theme.AcornCorners
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.feature.listentopage.ListenAction
import mozilla.components.feature.listentopage.R
import mozilla.components.ui.icons.R as iconsR

/** Listen to page audio player in expanded state */
@Composable
fun PlayerExpanded(
    title: String,
    source: String,
    elapsedTime: String,
    totalTime: String,
    progress: () -> Float,
    playing: Boolean,
    onAction: (ListenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(AcornCorners.extraLarge),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(AcornTheme.layout.elevation.level2),
        border = BorderStroke(AcornTheme.layout.border.default, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier.padding(
                        top = AcornTheme.layout.space.static50,
                        start = AcornTheme.layout.space.static25,
                        end = AcornTheme.layout.space.static200,
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
                    contentDescription = null,
                )
            }
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = AcornTheme.typography.headline8,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = source,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = AcornTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AudioProgress(elapsedTime, totalTime, progress)

        PlaybackControls(
            playing = playing,
            onAction = onAction,
        )
    }
}

@Composable
private fun AudioProgress(elapsedTime: String, totalTime: String, progress: () -> Float) {
    Column(
        modifier =
            Modifier.padding(
                    start = AcornTheme.layout.space.static200,
                    end = AcornTheme.layout.space.static200,
                    top = AcornTheme.layout.space.static100,
                )
                .fillMaxWidth()
    ) {
        AudioProgressBar(progress = progress, type = AudioProgressBarType.Full)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = elapsedTime,
                style = AcornTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = totalTime,
                style = AcornTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    playing: Boolean,
    onAction: (ListenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(
                    start = AcornTheme.layout.space.static100,
                    end = AcornTheme.layout.space.static100,
                    bottom = AcornTheme.layout.space.static150,
                )
                .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Voices
        IconButton(
            onClick = { onAction(ListenAction.Controls.VoicesClicked) },
            contentDescription = stringResource(R.string.mozac_feature_listentopage_audio),
        ) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_audio_wave_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Rewind
            IconButton(
                onClick = { onAction(ListenAction.Controls.RewindClicked) },
                contentDescription = stringResource(R.string.mozac_feature_listentopage_back_10_sec),
            ) {
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_playback_rewind_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Play/Pause
            PlayPauseButton(
                playing,
                { onAction(ListenAction.Controls.PlayPauseClicked) },
                Modifier.size(AcornTheme.layout.size.static800),
            )
            // Forward
            IconButton(
                onClick = { onAction(ListenAction.Controls.ForwardClicked) },
                contentDescription = stringResource(R.string.mozac_feature_listentopage_forward_30_sec),
            ) {
                // Speed icon hardcoded as of now
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_playback_forward_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Speed
        IconButton(
            onClick = { onAction(ListenAction.Controls.PlaybackSpeedClicked) },
            contentDescription = stringResource(R.string.mozac_feature_listentopage_playback_speed_1),
        ) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_playback_speed_1x_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PlayerExpandedPreview() {
    AcornTheme {
        PlayerExpanded(
            "Match Preview: Wrexham AFC vs Sunderland AFC",
            "source",
            elapsedTime = "1:24",
            totalTime = "6:00",
            progress = { 0.54f },
            playing = false,
            onAction = {},
        )
    }
}
