/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.feature.listentopage.ListenAction

/**
 * Media player with controls for the Listen To Page feature, shown either collapsed to a single row or expanded with
 * the full transport controls.
 *
 * @param title The article title.
 * @param url The article being read, shown as the source line.
 * @param elapsedTime How far playback has got, already formatted for display.
 * @param totalTime How long the audio is, already formatted for display.
 * @param progressState Fraction of the audio that has played, from `0` to `1`. Held as a [State] rather than a plain
 *   value so that the position is read while drawing the progress bar instead of while composing the player, which
 *   keeps a position update from recomposing the controls around it.
 * @param playing equals true if audio is playing, false if audio is paused.
 * @param expanded Whether to show the full player. `false` shows the compact one.
 * @param onAction Invoked to pass upwards a [ListenAction] in response to a UI event.
 * @param modifier Optional modifier for further customisation of this player.
 */
@Suppress("LongParameterList")
@Composable
fun ListenSheet(
    title: String?,
    url: String?,
    elapsedTime: String,
    totalTime: String,
    progressState: State<Float>,
    playing: Boolean,
    expanded: Boolean,
    onAction: (ListenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reading progressState here instead of inside the lambda would defeat the point of hoisting it as a State.
    val progress = { progressState.value }

    if (expanded) {
        PlayerExpanded(
            title = title.orEmpty(),
            source = url.orEmpty(),
            elapsedTime = elapsedTime,
            totalTime = totalTime,
            progress = progress,
            playing = playing,
            onAction = onAction,
            modifier = modifier,
        )
    } else {
        PlayerCompact(
            title = title.orEmpty(),
            source = url.orEmpty(),
            progress = progress,
            playing = playing,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewListenSheetCompact() {
    AcornTheme {
        ListenSheetExample(expanded = false)
    }
}

@PreviewLightDark
@Composable
private fun PreviewListenSheetExpanded() {
    AcornTheme {
        ListenSheetExample(expanded = true)
    }
}

@Composable
private fun ListenSheetExample(expanded: Boolean) {
    val progress = 0.4f
    ListenSheet(
        title = "Match Preview: Wrexham AFC vs Sunderland AFC",
        url = "bbc.co.uk",
        elapsedTime = "1:24",
        totalTime = "6:00",
        progressState = remember { mutableFloatStateOf(progress) },
        playing = true,
        expanded = expanded,
        onAction = {},
    )
}
