/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.samples.toolbar.middleware

import android.content.Context
import android.widget.Toast
import androidx.core.content.ContextCompat
import mozilla.components.compose.browser.toolbar.concept.Action
import mozilla.components.compose.browser.toolbar.concept.Action.DropdownAction
import mozilla.components.compose.browser.toolbar.concept.Action.TabCounterAction
import mozilla.components.compose.browser.toolbar.store.BrowserDisplayToolbarAction
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarAction
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarMenu
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarMenuItem
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarState
import mozilla.components.compose.browser.toolbar.store.DisplayState
import mozilla.components.compose.browser.toolbar.store.EditState
import mozilla.components.compose.browser.toolbar.store.Mode
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext
import org.mozilla.samples.toolbar.R
import org.mozilla.samples.toolbar.middleware.SearchSelectorInteractions.BookmarksClicked
import org.mozilla.samples.toolbar.middleware.SearchSelectorInteractions.HistoryClicked
import org.mozilla.samples.toolbar.middleware.SearchSelectorInteractions.SettingsClicked
import org.mozilla.samples.toolbar.middleware.SearchSelectorInteractions.TabsClicked
import org.mozilla.samples.toolbar.middleware.TabCounterInteractions.Add10TabsClicked
import org.mozilla.samples.toolbar.middleware.TabCounterInteractions.Remove10TabsClicked
import org.mozilla.samples.toolbar.middleware.TabCounterInteractions.TabCounterClicked
import mozilla.components.ui.icons.R as iconsR

private sealed class SearchSelectorInteractions : BrowserToolbarEvent {
    data object BookmarksClicked : SearchSelectorInteractions()
    data object TabsClicked : SearchSelectorInteractions()
    data object HistoryClicked : SearchSelectorInteractions()
    data object SettingsClicked : SearchSelectorInteractions()
}

private sealed class TabCounterInteractions : BrowserToolbarEvent {
    data object TabCounterClicked : TabCounterInteractions()
    data object Add10TabsClicked : TabCounterInteractions()
    data object Remove10TabsClicked : TabCounterInteractions()
}

private const val BATCH_TAB_COUNTER_UPDATES_NUMBER = 10

internal class BrowserToolbarMiddleware(
    initialDependencies: Dependencies,
) : Middleware<BrowserToolbarState, BrowserToolbarAction> {
    var dependencies = initialDependencies

    private var currentTabsNumber = 0
        set(value) { field = value.coerceAtLeast(0) }

    override fun invoke(
        context: MiddlewareContext<BrowserToolbarState, BrowserToolbarAction>,
        next: (BrowserToolbarAction) -> Unit,
        action: BrowserToolbarAction,
    ) {
        when (action) {
            is BrowserToolbarAction.Init -> {
                next(
                    BrowserToolbarAction.Init(
                        mode = Mode.DISPLAY,
                        displayState = DisplayState(
                            hint = "Search or enter address",
                            pageActions = listOf(
                                Action.ActionButton(
                                    icon = iconsR.drawable.mozac_ic_arrow_clockwise_24,
                                    contentDescription = R.string.page_action_refresh_description,
                                    tint = ContextCompat.getColor(
                                        dependencies.context,
                                        R.color.generic_button_tint,
                                    ),
                                    onClick = object : BrowserToolbarEvent {},
                                ),
                            ),
                            browserActions = buildDisplayBrowserActions(),
                        ),
                        editState = EditState(
                            editActionsStart = listOfNotNull(
                                buildSearchSelector(),
                            ),
                        ),
                    ),
                )
            }

            is SearchSelectorInteractions -> {
                Toast.makeText(dependencies.context, action.javaClass.simpleName, Toast.LENGTH_SHORT).show()
            }

            is TabCounterClicked -> {
                currentTabsNumber += 1
                next(BrowserDisplayToolbarAction.UpdateBrowserActions(buildDisplayBrowserActions()))
            }

            is Add10TabsClicked -> {
                currentTabsNumber += BATCH_TAB_COUNTER_UPDATES_NUMBER
                next(BrowserDisplayToolbarAction.UpdateBrowserActions(buildDisplayBrowserActions()))
            }

            is Remove10TabsClicked -> {
                currentTabsNumber -= BATCH_TAB_COUNTER_UPDATES_NUMBER
                next(BrowserDisplayToolbarAction.UpdateBrowserActions(buildDisplayBrowserActions()))
            }

            else -> {
                next(action)
            }
        }
    }

    private fun buildDisplayBrowserActions() = listOf(
        TabCounterAction(
            count = currentTabsNumber,
            contentDescription = "Tabs open: $currentTabsNumber",
            showPrivacyMask = false,
            onClick = TabCounterClicked,
            onLongClick = buildTabCounter(),
        ),
    )

    private fun buildSearchSelector(): Action = DropdownAction(
        icon = null,
        iconResource = iconsR.drawable.mozac_ic_search_24,
        contentDescription = R.string.clear_button_description,
        menu = {
            listOfNotNull(
                BrowserToolbarMenuItem(
                    icon = null,
                    iconResource = null,
                    text = R.string.search_selector_header,
                    contentDescription = R.string.search_selector_header,
                    onClick = null,
                ),
                BrowserToolbarMenuItem(
                    iconResource = iconsR.drawable.mozac_ic_bookmark_tray_24,
                    text = R.string.bookmarks_search_engine_name,
                    contentDescription = R.string.bookmarks_search_engine_description,
                    onClick = BookmarksClicked,
                ),
                BrowserToolbarMenuItem(
                    iconResource = iconsR.drawable.mozac_ic_tab_tray_24,
                    text = R.string.tabs_search_engine_name,
                    contentDescription = R.string.tabs_search_engine_description,
                    onClick = TabsClicked,
                ),
                BrowserToolbarMenuItem(
                    iconResource = iconsR.drawable.mozac_ic_history_24,
                    text = R.string.history_search_engine_name,
                    contentDescription = R.string.tabs_search_engine_description,
                    onClick = HistoryClicked,
                ),
                BrowserToolbarMenuItem(
                    iconResource = iconsR.drawable.mozac_ic_settings_24,
                    text = R.string.search_settings,
                    contentDescription = R.string.tabs_search_engine_description,
                    onClick = SettingsClicked,
                ),
            )
        },
    )

    private fun buildTabCounter() = BrowserToolbarMenu {
        listOfNotNull(
            BrowserToolbarMenuItem(
                iconResource = android.R.drawable.ic_menu_add,
                text = R.string.tab_counter_add_10_tabs,
                contentDescription = R.string.tab_counter_add_10_tabs,
                onClick = Add10TabsClicked,
            ),
            BrowserToolbarMenuItem(
                iconResource = android.R.drawable.ic_menu_delete,
                text = R.string.tab_counter_remove_10_tabs,
                contentDescription = R.string.tab_counter_remove_10_tabs,
                onClick = Remove10TabsClicked,
            ),
        )
    }

    companion object {
        data class Dependencies(
            val context: Context,
        )
    }
}
