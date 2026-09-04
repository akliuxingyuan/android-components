/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mozilla.components.feature.listentopage.content.Content
import mozilla.components.feature.listentopage.content.ContentProvider
import mozilla.components.feature.listentopage.synthesis.SpeechSynthesizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAB_ID = "tab-1"
private const val URL = "https://example.org/article"

@OptIn(ExperimentalCoroutinesApi::class)
class ListenMiddlewareTest {

    @Test
    fun `test that the article of the requested tab is extracted and its language recorded`() = runTest {
        var extractedTabId: String? = null
        val store = storeWith { tabId ->
            extractedTabId = tabId
            Result.success(Content(text = "Article text", languageTag = "de-DE"))
        }
        store.dispatch(ListenAction.Session.ListenRequested(TAB_ID, URL))
        advanceUntilIdle()

        assertEquals(TAB_ID, extractedTabId)
        assertEquals("de-DE", store.state.languageTag)
        assertNull(store.state.error)
    }

    @Test
    fun `test that a failed extraction reports the content as unavailable`() = runTest {
        val store = storeWith { Result.failure(RuntimeException("Content failed")) }
        store.dispatch(ListenAction.Session.ListenRequested(TAB_ID, URL))
        advanceUntilIdle()

        assertEquals(ListenError.ContentUnavailable, store.state.error)
        assertNull(store.state.languageTag)
    }

    @Test
    fun `test that a page with no usable text reports the content as unavailable`() = runTest {
        val store = storeWith { Result.success(Content(text = "   ", languageTag = "en-US")) }
        store.dispatch(ListenAction.Session.ListenRequested(TAB_ID, URL))
        advanceUntilIdle()

        assertEquals(ListenError.ContentUnavailable, store.state.error)
        assertNull(store.state.languageTag)
    }

    @Test
    fun `test that an article extracted after the session stopped is dropped`() = runTest {
        val extraction = CompletableDeferred<Result<Content>>()
        val store = storeWith { extraction.await() }
        store.dispatch(ListenAction.Session.ListenRequested(TAB_ID, URL))
        advanceUntilIdle()
        store.dispatch(ListenAction.Session.StopRequested)
        extraction.complete(Result.success(Content(text = "Article text", languageTag = "de-DE")))
        advanceUntilIdle()

        assertEquals(ListenState(), store.state)
    }

    @Test
    fun `test that the voices of the article language are loaded once the article is ready`() = runTest {
        var requestedLangTag: String? = null
        val voices = listOf(Voice(id = "de-de-female"), Voice(id = "de-de-male"))
        val store =
            storeWith(
                synthesizer = { langTag ->
                    requestedLangTag = langTag
                    voices
                }
            ) {
                Result.success(Content(text = "Article text", languageTag = "de-DE"))
            }
        store.dispatch(ListenAction.Session.ListenRequested(TAB_ID, URL))
        advanceUntilIdle()

        assertEquals("de-DE", requestedLangTag)
        assertEquals(voices, store.state.voiceState.availableVoices)
        assertNull(store.state.error)
    }

    @Test
    fun `test that an article language with no offline voice is reported as an error`() = runTest {
        val store =
            storeWith(synthesizer = { emptyList() }) {
                Result.success(Content(text = "Article text", languageTag = "ja-JP"))
            }
        store.dispatch(ListenAction.Session.ListenRequested(TAB_ID, URL))
        advanceUntilIdle()

        assertEquals(ListenError.NoOfflineVoice, store.state.error)
        assertTrue(store.state.voiceState.availableVoices.isEmpty())
    }

    @Test
    fun `test that no voices are loaded when the article could not be extracted`() = runTest {
        var voicesRequested = false
        val store =
            storeWith(
                synthesizer = {
                    voicesRequested = true
                    emptyList()
                }
            ) {
                Result.failure(RuntimeException("Content failed"))
            }
        store.dispatch(ListenAction.Session.ListenRequested(TAB_ID, URL))
        advanceUntilIdle()

        assertFalse(voicesRequested)
        assertEquals(ListenError.ContentUnavailable, store.state.error)
    }

    private fun TestScope.storeWith(
        synthesizer: SpeechSynthesizer = SpeechSynthesizer { listOf(Voice(id = "voice-1")) },
        contentProvider: ContentProvider,
    ) =
        ListenStore(
            initialState = ListenState(),
            reducer = ::listenReducer,
            middleware =
                listOf(
                    ListenMiddleware(
                        contentProvider = contentProvider,
                        synthesizer = synthesizer,
                        scope = this,
                        ioDispatcher = StandardTestDispatcher(testScheduler),
                    )
                ),
        )
}
