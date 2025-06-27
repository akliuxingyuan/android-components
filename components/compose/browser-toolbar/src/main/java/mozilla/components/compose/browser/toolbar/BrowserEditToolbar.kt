/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.compose.browser.toolbar.concept.Action
import mozilla.components.compose.browser.toolbar.concept.Action.ActionButtonRes
import mozilla.components.compose.browser.toolbar.concept.Action.SearchSelectorAction
import mozilla.components.compose.browser.toolbar.concept.Action.SearchSelectorAction.ContentDescription.StringResContentDescription
import mozilla.components.compose.browser.toolbar.concept.Action.SearchSelectorAction.Icon.DrawableIcon
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent
import mozilla.components.compose.browser.toolbar.ui.InlineAutocompleteTextField
import mozilla.components.concept.toolbar.AutocompleteProvider
import mozilla.components.ui.icons.R as iconsR

private val ROUNDED_CORNER_SHAPE = RoundedCornerShape(8.dp)

/**
 * Sub-component of the [BrowserToolbar] responsible for allowing the user to edit the current
 * URL ("edit mode").
 *
 * @param query The current query.
 * @param showQueryAsPreselected Whether or not to show the query as preselected.
 * @param autocompleteProviders Optional list of [AutocompleteProvider]s to be used for
 * inline autocompleting the current query.
 * @param useComposeTextField Whether or not to use the Compose [TextField] or a view-based
 * inline autocomplete text field.
 * @param editActionsStart List of [Action]s to be displayed at the start of the URL of
 * the edit toolbar.
 * @param editActionsEnd List of [Action]s to be displayed at the end of the URL of
 * the edit toolbar.
 * @param onUrlEdit Will be called when the URL value changes. An updated text value comes as a
 * parameter of the callback.
 * @param onUrlCommitted Will be called when the user has finished editing and wants to initiate
 * loading the entered URL. The committed text value comes as a parameter of the callback.
 * @param onInteraction Callback for handling [BrowserToolbarEvent]s on user interactions.
 */
@Composable
@Suppress("LongMethod")
fun BrowserEditToolbar(
    query: String,
    showQueryAsPreselected: Boolean = false,
    autocompleteProviders: List<AutocompleteProvider> = emptyList(),
    useComposeTextField: Boolean = false,
    editActionsStart: List<Action> = emptyList(),
    editActionsEnd: List<Action> = emptyList(),
    onUrlEdit: (String) -> Unit = {},
    onUrlCommitted: (String) -> Unit = {},
    onUrlSuggestionAutocompleted: (String) -> Unit = {},
    onInteraction: (BrowserToolbarEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .background(color = AcornTheme.colors.layer1)
            .padding(all = 8.dp)
            .height(40.dp)
            .clip(shape = ROUNDED_CORNER_SHAPE)
            .background(color = AcornTheme.colors.layer3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (useComposeTextField) {
            TextField(
                value = query,
                onValueChange = { value ->
                    onUrlEdit(value)
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = AcornTheme.colors.textPrimary,
                    unfocusedTextColor = AcornTheme.colors.textPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = AcornTheme.colors.layer3,
                    focusedContainerColor = AcornTheme.colors.layer3,
                    disabledContainerColor = AcornTheme.colors.layer3,
                    errorContainerColor = AcornTheme.colors.layer3,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { onUrlCommitted(query) },
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = ROUNDED_CORNER_SHAPE,
                leadingIcon = {
                    ActionContainer(
                        actions = editActionsStart,
                        onInteraction = onInteraction,
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionContainer(
                            actions = editActionsEnd,
                            onInteraction = onInteraction,
                        )

                        if (query.isNotEmpty()) {
                            ClearButton(
                                tint = AcornTheme.colors.iconPrimary,
                                onButtonClicked = { onUrlEdit("") },
                            )
                        }
                    }
                },
            )
        } else {
            ActionContainer(
                actions = editActionsStart,
                onInteraction = onInteraction,
            )

            InlineAutocompleteTextField(
                query = query,
                showQueryAsPreselected = showQueryAsPreselected,
                autocompleteProviders = autocompleteProviders,
                modifier = Modifier.weight(1f),
                onUrlEdit = onUrlEdit,
                onUrlCommitted = onUrlCommitted,
                onUrlSuggestionAutocompleted = onUrlSuggestionAutocompleted,
            )

            ActionContainer(
                actions = editActionsEnd,
                onInteraction = onInteraction,
            )

            if (query.isNotEmpty()) {
                ClearButton(
                    tint = AcornTheme.colors.iconPrimary,
                    onButtonClicked = { onUrlEdit("") },
                )
            }
        }
    }
}

/**
 * Sub-component of the [BrowserEditToolbar] responsible for displaying a clear icon button.
 *
 * @param tint Color tint of the clear button.
 * @param onButtonClicked Will be called when the user clicks on the button.
 */
@Composable
private fun ClearButton(
    tint: Color,
    onButtonClicked: () -> Unit = {},
) {
    IconButton(
        modifier = Modifier.requiredSize(40.dp),
        onClick = { onButtonClicked() },
    ) {
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_cross_circle_fill_24),
            contentDescription = stringResource(R.string.mozac_clear_button_description),
            tint = tint,
        )
    }
}

@PreviewLightDark
@Composable
private fun BrowserEditToolbarPreview() {
    AcornTheme {
        BrowserEditToolbar(
            query = "http://www.mozilla.org",
            autocompleteProviders = emptyList(),
            useComposeTextField = true,
            editActionsStart = listOf(
                SearchSelectorAction(
                    icon = DrawableIcon(
                        AppCompatResources.getDrawable(LocalContext.current, iconsR.drawable.mozac_ic_search_24)!!,
                    ),
                    contentDescription = StringResContentDescription(android.R.string.untitled),
                    menu = { emptyList() },
                    onClick = null,
                ),
            ),
            editActionsEnd = listOf(
                ActionButtonRes(
                    drawableResId = iconsR.drawable.mozac_ic_microphone_24,
                    contentDescription = android.R.string.untitled,
                    onClick = object : BrowserToolbarEvent {},
                ),
                ActionButtonRes(
                    drawableResId = iconsR.drawable.mozac_ic_qr_code_24,
                    contentDescription = android.R.string.untitled,
                    onClick = object : BrowserToolbarEvent {},
                ),
            ),
            onInteraction = {},
        )
    }
}
