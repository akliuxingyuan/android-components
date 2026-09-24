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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.theme.AcornCorners
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.feature.listentopage.ListenAction
import mozilla.components.feature.listentopage.R
import mozilla.components.feature.listentopage.VoiceState
import mozilla.components.ui.icons.R as iconsR

/** Listen to page audio player in expanded state */
@Composable
fun PlayerExpanded(
    article: ArticleDetails,
    elapsedTime: String,
    totalTime: String,
    progress: () -> Float,
    playing: Boolean,
    voiceState: VoiceState,
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
            ArticleHeading(article = article)
        }

        AudioProgress(elapsedTime = elapsedTime, totalTime = totalTime, progress = progress)

        PlaybackControls(playing = playing, voiceState = voiceState, onAction = onAction)
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

@PreviewLightDark
@Composable
private fun PlayerExpandedPreview() {
    AcornTheme {
        PlayerExpanded(
            article = ArticleDetails(title = "Match Preview: Wrexham AFC vs Sunderland AFC", site = "source"),
            elapsedTime = "1:24",
            totalTime = "6:00",
            progress = { 0.54f },
            playing = false,
            voiceState = VoiceState(),
            onAction = {},
        )
    }
}
