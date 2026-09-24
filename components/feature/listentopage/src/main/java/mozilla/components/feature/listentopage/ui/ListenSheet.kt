/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.feature.listentopage.ListenAction
import mozilla.components.feature.listentopage.VoiceState

/**
 * Media player with controls for the Listen To Page feature, shown either collapsed to a single row or expanded with
 * the full transport controls.
 *
 * @param article What the player says about the article it reads.
 * @param elapsedTime How far playback has got, already formatted for display.
 * @param totalTime How long the audio is, already formatted for display.
 * @param progressState Fraction of the audio that has played, from `0` to `1`. Held as a [State] rather than a plain
 *   value so that the position is read while drawing the progress bar instead of while composing the player, which
 *   keeps a position update from recomposing the controls around it.
 * @param playing equals true if audio is playing, false if audio is paused.
 * @param voiceState The voices the expanded player offers to read the article in, and the one it is read in.
 * @param expanded Whether to show the full player. `false` shows the compact one.
 * @param onAction Invoked to pass upwards a [ListenAction] in response to a UI event.
 * @param modifier Optional modifier for further customisation of this player.
 */
@Composable
fun ListenSheet(
    article: ArticleDetails,
    elapsedTime: String,
    totalTime: String,
    progressState: State<Float>,
    playing: Boolean,
    voiceState: VoiceState,
    onAction: (ListenAction) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    // Reading progressState here instead of inside the lambda would defeat the point of hoisting it as a State.
    val progress = { progressState.value }

    if (expanded) {
        PlayerExpanded(
            article = article,
            elapsedTime = elapsedTime,
            totalTime = totalTime,
            progress = progress,
            playing = playing,
            voiceState = voiceState,
            onAction = onAction,
            modifier = modifier,
        )
    } else {
        PlayerCompact(
            article = article,
            progress = progress,
            playing = playing,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

/**
 * What the player says about the article it reads.
 *
 * @property title The article title, or `null` when the page has none.
 * @property site The site the article is from, shown under the title.
 * @property url The article's url, shown in full in place of the title when there is no title.
 */
data class ArticleDetails(val title: String? = null, val site: String? = null, val url: String? = null) {
    internal val hasTitle: Boolean
        get() = !title.isNullOrBlank()

    internal val heading: String?
        get() = if (hasTitle) title else url
}

@Composable
internal fun ArticleHeading(article: ArticleDetails, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = article.heading.orEmpty(),
            color = MaterialTheme.colorScheme.onSurface,
            style = AcornTheme.typography.headline8,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (article.hasTitle) {
            Text(
                text = article.site.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = AcornTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

@PreviewLightDark
@Composable
private fun PreviewListenSheetCompactUntitled() {
    AcornTheme {
        ListenSheetExample(expanded = false, title = null)
    }
}

@PreviewLightDark
@Composable
private fun PreviewListenSheetExpandedUntitled() {
    AcornTheme {
        ListenSheetExample(expanded = true, title = null)
    }
}

@Composable
private fun ListenSheetExample(
    expanded: Boolean,
    title: String? = "Match Preview: Wrexham AFC vs Sunderland AFC",
) {
    val progress = 0.4f
    ListenSheet(
        article =
            ArticleDetails(
                title = title,
                site = "bbc.co.uk",
                url = "https://www.bbc.co.uk/sport/football/articles/c0l8m2y4kxpo",
            ),
        elapsedTime = "1:24",
        totalTime = "6:00",
        progressState = remember { mutableFloatStateOf(progress) },
        playing = true,
        voiceState = VoiceState(),
        onAction = {},
        expanded = expanded,
    )
}
