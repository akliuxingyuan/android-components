/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.thumbnails

import android.graphics.Bitmap
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.support.test.ext.joinBlocking
import mozilla.components.support.test.middleware.CaptureActionsMiddleware
import mozilla.components.support.test.mock
import org.junit.Test
import org.mockito.Mockito.verify

class ThumbnailsMiddlewareTest {

    @Test
    fun `thumbnail storage stores the provided thumbnail on update thumbnail action`() {
        val request = "test-tab1"
        val tab = createTab("https://www.mozilla.org", id = "test-tab1")
        val thumbnailStorage: ThumbnailStorage = mock()
        val store = BrowserStore(
            initialState = BrowserState(tabs = listOf(tab)),
            middleware = listOf(ThumbnailsMiddleware(thumbnailStorage)),
        )

        val bitmap: Bitmap = mock()
        store.dispatch(ContentAction.UpdateThumbnailAction(request, bitmap)).joinBlocking()
        verify(thumbnailStorage).saveThumbnail(request, bitmap)
    }

    @Test
    fun `thumbnail storage deletes the thumbnail on remove thumbnail action`() {
        val request = "test-tab1"
        val tab = createTab("https://www.mozilla.org", id = "test-tab1")
        val thumbnailStorage: ThumbnailStorage = mock()
        val store = BrowserStore(
            initialState = BrowserState(tabs = listOf(tab)),
            middleware = listOf(ThumbnailsMiddleware(thumbnailStorage)),
        )

        store.dispatch(ContentAction.RemoveThumbnailAction(request)).joinBlocking()
        verify(thumbnailStorage).deleteThumbnail(request)
    }

    @Test
    fun `thumbnail storage removes the thumbnail on remove all normal tabs action`() {
        val thumbnailStorage: ThumbnailStorage = mock()
        val store = BrowserStore(
            initialState = BrowserState(
                tabs = listOf(
                    createTab("https://www.mozilla.org", id = "test-tab1"),
                    createTab("https://www.firefox.com", id = "test-tab2"),
                    createTab("https://www.wikipedia.com", id = "test-tab3"),
                    createTab("https://www.example.org", private = true, id = "test-ta4"),
                ),
            ),
            middleware = listOf(ThumbnailsMiddleware(thumbnailStorage)),
        )

        store.dispatch(TabListAction.RemoveAllNormalTabsAction).joinBlocking()
        verify(thumbnailStorage).deleteThumbnail("test-tab1")
        verify(thumbnailStorage).deleteThumbnail("test-tab2")
        verify(thumbnailStorage).deleteThumbnail("test-tab3")
    }

    @Test
    fun `thumbnail storage removes the thumbnail on remove all private tabs action`() {
        val thumbnailStorage: ThumbnailStorage = mock()
        val store = BrowserStore(
            initialState = BrowserState(
                tabs = listOf(
                    createTab("https://www.mozilla.org", id = "test-tab1"),
                    createTab("https://www.firefox.com", private = true, id = "test-tab2"),
                    createTab("https://www.wikipedia.com", private = true, id = "test-tab3"),
                    createTab("https://www.example.org", private = true, id = "test-tab4"),
                ),
            ),
            middleware = listOf(ThumbnailsMiddleware(thumbnailStorage)),
        )

        store.dispatch(TabListAction.RemoveAllPrivateTabsAction).joinBlocking()
        verify(thumbnailStorage).deleteThumbnail("test-tab2")
        verify(thumbnailStorage).deleteThumbnail("test-tab3")
        verify(thumbnailStorage).deleteThumbnail("test-tab4")
    }

    @Test
    fun `thumbnail storage removes the thumbnail on remove all tabs action`() {
        val thumbnailStorage: ThumbnailStorage = mock()
        val store = BrowserStore(
            initialState = BrowserState(
                tabs = listOf(
                    createTab("https://www.mozilla.org", id = "test-tab1"),
                    createTab("https://www.firefox.com", id = "test-tab2"),
                ),
            ),
            middleware = listOf(ThumbnailsMiddleware(thumbnailStorage)),
        )

        store.dispatch(TabListAction.RemoveAllTabsAction()).joinBlocking()
        verify(thumbnailStorage).clearThumbnails()
    }

    @Test
    fun `thumbnail storage removes the thumbnail on remove tab action`() {
        val sessionIdOrUrl = "test-tab1"
        val thumbnailStorage: ThumbnailStorage = mock()
        val store = BrowserStore(
            initialState = BrowserState(
                tabs = listOf(
                    createTab("https://www.mozilla.org", id = "test-tab1"),
                    createTab("https://www.firefox.com", id = "test-tab2"),
                ),
            ),
            middleware = listOf(ThumbnailsMiddleware(thumbnailStorage)),
        )

        store.dispatch(TabListAction.RemoveTabAction(sessionIdOrUrl)).joinBlocking()
        verify(thumbnailStorage).deleteThumbnail(sessionIdOrUrl)
    }

    @Test
    fun `thumbnail storage removes the thumbnail on remove tabs action`() {
        val sessionIdOrUrl = "test-tab1"
        val thumbnailStorage: ThumbnailStorage = mock()
        val store = BrowserStore(
            initialState = BrowserState(
                tabs = listOf(
                    createTab("https://www.mozilla.org", id = "test-tab1"),
                    createTab("https://www.firefox.com", id = "test-tab2"),
                ),
            ),
            middleware = listOf(ThumbnailsMiddleware(thumbnailStorage)),
        )

        store.dispatch(TabListAction.RemoveTabsAction(listOf(sessionIdOrUrl))).joinBlocking()
        verify(thumbnailStorage).deleteThumbnail(sessionIdOrUrl)
    }

    @Test
    fun `thumbnail actions are the only ones consumed by the middleware`() {
        val capture = CaptureActionsMiddleware<BrowserState, BrowserAction>()
        val store = BrowserStore(
            initialState = BrowserState(
                tabs = listOf(
                    createTab("https://www.mozilla.org", id = "test-tab1"),
                    createTab("https://www.firefox.com", id = "test-tab2"),
                ),
            ),
            middleware = listOf(
                ThumbnailsMiddleware(mock()),
                capture,
            ),
        )

        store.dispatch(ContentAction.UpdateThumbnailAction("test-tab1", mock())).joinBlocking()
        store.dispatch(TabListAction.RemoveTabAction("test-tab1")).joinBlocking()

        // We shouldn't allow thumbnail actions to continue being processed.
        capture.assertNotDispatched(ContentAction.UpdateThumbnailAction::class)
        // TabListActions that we also observe in the middleware _should_ continue being processed.
        capture.assertLastAction(TabListAction.RemoveTabAction::class) {}

        // All other actions should also continue being processed.
        store.dispatch(EngineAction.KillEngineSessionAction("test-tab1")).joinBlocking()
        capture.assertLastAction(EngineAction.KillEngineSessionAction::class) {}
    }
}
