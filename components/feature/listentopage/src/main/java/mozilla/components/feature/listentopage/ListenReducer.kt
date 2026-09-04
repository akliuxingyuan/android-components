/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage

/**
 * Reduces the given [action] and current [state] into a new [ListenState].
 *
 * @param state The current [ListenState].
 * @param action The [ListenAction] to process.
 * @return The resulting [ListenState] after applying the action.
 */
fun listenReducer(state: ListenState, action: ListenAction): ListenState =
    when (action) {
        is ListenAction.Session -> reduceSession(state, action)
        is ListenAction.Content -> reduceContent(state, action)
        ListenAction.ErrorDismissed -> state.copy(error = null)
    }

private fun reduceSession(state: ListenState, action: ListenAction.Session): ListenState =
    when (action) {
        is ListenAction.Session.ListenRequested -> {
            ListenState(tabId = action.tabId, url = action.url, voiceState = VoiceState())
        }

        ListenAction.Session.StopRequested -> {
            ListenState(tabId = null, url = null, voiceState = state.voiceState.copy())
        }
    }

private fun reduceContent(state: ListenState, action: ListenAction.Content): ListenState =
    when (action) {
        is ListenAction.Content.ContentReady -> state.copy(languageTag = action.languageTag)

        ListenAction.Content.ContentUnavailable -> state.copy(error = ListenError.ContentUnavailable)
    }
