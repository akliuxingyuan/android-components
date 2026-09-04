/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.feature.listentopage.content.ContentProvider
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * [Middleware] that extracts the article a listening session reads out.
 *
 * @property contentProvider Provides the article text and language for a tab.
 * @property scope The [CoroutineScope] the extraction runs in.
 */
class ListenMiddleware(
    private val contentProvider: ContentProvider,
    private val scope: CoroutineScope,
) : Middleware<ListenState, ListenAction> {

    private var contentJob: Job? = null

    override fun invoke(
        store: Store<ListenState, ListenAction>,
        next: (ListenAction) -> Unit,
        action: ListenAction,
    ) {
        next(action)

        when (action) {
            is ListenAction.Session.ListenRequested -> requestContent(store, action.tabId)

            ListenAction.Session.StopRequested -> contentJob?.cancel()

            is ListenAction.Content.ContentReady,
            ListenAction.Content.ContentUnavailable,
            is ListenAction.Voices.VoiceSelected,
            ListenAction.ErrorDismissed -> Unit
        }
    }

    private fun requestContent(store: Store<ListenState, ListenAction>, tabId: String) {
        contentJob?.cancel()
        contentJob = scope.launch {
            val content = contentProvider.getContent(tabId)

            // A session for another tab was started, or the session was stopped, while the article was extracted.
            if (store.state.tabId != tabId) {
                return@launch
            }

            content
                .onSuccess {
                    val resultAction =
                        if (it.text.isBlank()) {
                            ListenAction.Content.ContentUnavailable
                        } else {
                            ListenAction.Content.ContentReady(text = it.text, languageTag = it.languageTag)
                        }

                    store.dispatch(resultAction)
                }
                .onFailure { store.dispatch(ListenAction.Content.ContentUnavailable) }
        }
    }
}
