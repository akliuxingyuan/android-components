/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.feature.listentopage.ArticleProgress
import mozilla.components.feature.listentopage.ListenAction
import mozilla.components.feature.listentopage.PlaybackSpeed
import mozilla.components.feature.listentopage.R
import mozilla.components.feature.listentopage.VoiceState
import mozilla.components.ui.icons.R as iconsR

private const val MS_PER_SECOND = 1000L

/** Listen to page audio player in expanded state */
@Composable
internal fun PlayerExpanded(
    article: ArticleDetails,
    articleProgressState: State<ArticleProgress>,
    playbackUiState: PlaybackUiState,
    speed: PlaybackSpeed,
    voiceState: VoiceState,
    onAction: (ListenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
                onClick = { onAction(ListenAction.Session.StopRequested()) },
                contentDescription = stringResource(R.string.mozac_feature_listentopage_close),
            ) {
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_cross_24),
                    contentDescription = null,
                )
            }
            ArticleHeading(
                article = article,
                playbackUiState = playbackUiState,
                shouldScrollTitle = playbackUiState is PlaybackUiState.Playing,
            )
        }

        AudioProgress(articleProgressState = articleProgressState, onAction = onAction)

        val locale = LocalConfiguration.current.locales[0]
        val labelPattern = stringResource(R.string.mozac_feature_listentopage_playback_speed_option)
        val speedState =
            remember(speed, locale, labelPattern, onAction) {
                speed.toSpeedState(locale, labelPattern) {
                    onAction(ListenAction.Controls.PlaybackSpeedSelected(it))
                }
            }
        PlaybackControls(
            articleProgressState = articleProgressState,
            playbackUiState = playbackUiState,
            voiceState = voiceState,
            speedState = speedState,
            onAction = onAction,
        )
    }
}

@Composable
private fun AudioProgress(articleProgressState: State<ArticleProgress>, onAction: (ListenAction) -> Unit) {
    // Reading articleProgressState here instead of inside the lambda would defeat the point of hoisting it as a State.
    val progress = { articleProgressState.value.fraction }
    val elapsedTime by remember {
        derivedStateOf { DateUtils.formatElapsedTime(articleProgressState.value.positionMs / MS_PER_SECOND) }
    }
    val totalTime by remember {
        derivedStateOf { DateUtils.formatElapsedTime(articleProgressState.value.durationMs / MS_PER_SECOND) }
    }

    // Media player controls are always LTR:
    // https://m3.material.io/foundations/layout/bidirectionality-rtl
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier =
                Modifier.padding(
                        start = AcornTheme.layout.space.static200,
                        end = AcornTheme.layout.space.static200,
                        top = AcornTheme.layout.space.static100,
                    )
                    .fillMaxWidth()
        ) {
            AudioProgressBar(
                progress = progress,
                type = AudioProgressBarType.Full,
                onSeek = {
                    onAction(ListenAction.Playback.SeekRequested(it))
                },
                modifier = Modifier.extendTrackLength(thumbWidth = AcornTheme.layout.size.static200),
            )

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
}

/**
 * Extends the component's width by the Thumb's width, and then places it in the center. This allows the track to run up
 * to the edge of the component. Note that this causes the Thumb to hang off the edge of the component's reported width.
 *
 * @param thumbWidth Used to extend the track width, must match the resting Slider thumb width.
 */
private fun Modifier.extendTrackLength(thumbWidth: Dp): Modifier {
    return this.layout { measurable, constraints ->
        val thumbPx = thumbWidth.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = thumbPx))
        layout((placeable.width - thumbPx).coerceAtLeast(0), placeable.height) {
            placeable.placeRelative(-thumbPx / 2, 0)
        }
    }
}

@PreviewLightDark
@Preview(name = "RTL - Arabic", locale = "ar")
@Composable
private fun PlayerExpandedPreview(@PreviewParameter(PlaybackUIStatePreviewProvider::class) state: PlaybackUiState) {
    AcornTheme {
        Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.surface)) {
            PlayerExpanded(
                article = ArticleDetails(title = "Match Preview: Wrexham AFC vs Sunderland AFC", site = "source"),
                articleProgressState =
                    remember {
                        mutableStateOf(ArticleProgress(positionMs = 84_000, durationMs = 360_000))
                    },
                playbackUiState = state,
                speed = PlaybackSpeed.Default,
                voiceState = VoiceState(),
                onAction = {},
            )
        }
    }
}
