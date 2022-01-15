/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.recentlyclosed

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.browser.state.state.recover.TabState
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.engine.EngineSessionState
import mozilla.components.concept.engine.EngineSessionStateStorage
import mozilla.components.feature.recentlyclosed.db.RecentlyClosedTabsDatabase
import mozilla.components.support.test.any
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import java.io.IOException
import java.lang.Exception
import java.lang.IllegalStateException

@RunWith(AndroidJUnit4::class)
class RecentlyClosedTabsStorageTest {
    private lateinit var storage: RecentlyClosedTabsStorage
    private lateinit var engineStateStorage: TestEngineSessionStateStorage
    private lateinit var database: RecentlyClosedTabsDatabase
    private lateinit var crashReporting: CrashReporting

    private class TestEngineSessionStateStorage : EngineSessionStateStorage {
        val data: MutableMap<String, EngineSessionState?> = mutableMapOf()

        override suspend fun write(uuid: String, state: EngineSessionState): Boolean {
            if (uuid.contains("fail")) {
                return false
            }
            if (uuid.contains("boom")) {
                throw IllegalStateException("boom!")
            }
            data[uuid] = state
            return true
        }

        override suspend fun read(uuid: String): EngineSessionState? {
            return data[uuid]
        }

        override suspend fun delete(uuid: String) {
            data.remove(uuid)
        }

        override suspend fun deleteAll() {
            data.clear()
        }
    }

    @Before
    fun setUp() {
        crashReporting = mock()
        database = Room
            .inMemoryDatabaseBuilder(testContext, RecentlyClosedTabsDatabase::class.java)
            .build()

        engineStateStorage = TestEngineSessionStateStorage()
        storage = RecentlyClosedTabsStorage(
            testContext,
            mock(),
            crashReporting,
            engineStateStorage = engineStateStorage,
        )
        storage.database = lazy { database }
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun testAddingTabsWithMax() {
        // Test tab
        val closedTab = RecoverableTab(
            engineSessionState = null,
            state = TabState(
                id = "first-tab",
                title = "Mozilla",
                url = "https://mozilla.org",
                lastAccess = System.currentTimeMillis()
            )
        )

        // Test tab
        val engineState2: EngineSessionState = mock()
        val secondClosedTab = RecoverableTab(
            engineSessionState = engineState2,
            TabState(
                id = "second-tab",
                title = "Pocket",
                url = "https://pocket.com",
                lastAccess = System.currentTimeMillis()
            )
        )

        val tabs = runBlocking(Dispatchers.IO) {
            storage.addTabsToCollectionWithMax(listOf(closedTab, secondClosedTab), 1)
            storage.getTabs().first()
        }

        assertEquals(1, engineStateStorage.data.size)
        assertEquals(engineState2, engineStateStorage.data["second-tab"])

        assertEquals(1, tabs.size)
        assertEquals(secondClosedTab.state.url, tabs[0].url)
        assertEquals(secondClosedTab.state.title, tabs[0].title)
        assertEquals(secondClosedTab.state.lastAccess, tabs[0].lastAccess)

        // Test tab
        val engineState3: EngineSessionState = mock()
        val thirdClosedTab = RecoverableTab(
            engineSessionState = engineState3,
            state = TabState(
                id = "third-tab",
                title = "Firefox",
                url = "https://firefox.com",
                lastAccess = System.currentTimeMillis()
            )
        )

        val newTabs = runBlocking(Dispatchers.IO) {
            storage.addTabsToCollectionWithMax(listOf(thirdClosedTab), 1)
            storage.getTabs().first()
        }

        assertEquals(1, engineStateStorage.data.size)
        assertEquals(engineState3, engineStateStorage.data["third-tab"])

        assertEquals(1, newTabs.size)
        assertEquals(thirdClosedTab.state.url, newTabs[0].url)
        assertEquals(thirdClosedTab.state.title, newTabs[0].title)
        assertEquals(thirdClosedTab.state.lastAccess, newTabs[0].lastAccess)
    }

    @Test
    fun testAllowAddingSameTabTwice() {
        // Test tab
        val engineState: EngineSessionState = mock()
        val closedTab = RecoverableTab(
            engineSessionState = engineState,
            state = TabState(
                id = "first-tab",
                title = "Mozilla",
                url = "https://mozilla.org",
                lastAccess = System.currentTimeMillis()
            )
        )

        val updatedTab = closedTab.copy(state = closedTab.state.copy(title = "updated"))
        val tabs = runBlocking(Dispatchers.IO) {
            storage.addTabsToCollectionWithMax(listOf(closedTab), 2)
            storage.addTabsToCollectionWithMax(listOf(updatedTab), 2)
            storage.getTabs().first()
        }

        assertEquals(1, engineStateStorage.data.size)
        assertEquals(engineState, engineStateStorage.data["first-tab"])

        assertEquals(1, tabs.size)
        assertEquals(updatedTab.state.url, tabs[0].url)
        assertEquals(updatedTab.state.title, tabs[0].title)
        assertEquals(updatedTab.state.lastAccess, tabs[0].lastAccess)
    }

    @Test
    fun testRemovingAllTabs() {
        // Test tab
        val t1 = System.currentTimeMillis()
        val closedTab = RecoverableTab(
            engineSessionState = mock(),
            state = TabState(
                id = "first-tab",
                title = "Mozilla",
                url = "https://mozilla.org",
                lastAccess = t1
            )
        )

        // Test tab
        val t2 = t1 - 1000
        val secondClosedTab = RecoverableTab(
            engineSessionState = mock(),
            state = TabState(
                id = "second-tab",
                title = "Pocket",
                url = "https://pocket.com",
                lastAccess = t2
            )
        )

        val tabs = runBlocking(Dispatchers.IO) {
            storage.addTabsToCollectionWithMax(listOf(closedTab, secondClosedTab), 2)
            storage.getTabs().first()
        }

        assertEquals(2, engineStateStorage.data.size)
        assertEquals(2, tabs.size)
        assertEquals(closedTab.state.url, tabs[0].url)
        assertEquals(closedTab.state.title, tabs[0].title)
        assertEquals(closedTab.state.lastAccess, tabs[0].lastAccess)
        assertEquals(secondClosedTab.state.url, tabs[1].url)
        assertEquals(secondClosedTab.state.title, tabs[1].title)
        assertEquals(secondClosedTab.state.lastAccess, tabs[1].lastAccess)

        val newTabs = runBlocking(Dispatchers.IO) {
            storage.removeAllTabs()
            storage.getTabs().first()
        }

        assertEquals(0, engineStateStorage.data.size)
        assertEquals(0, newTabs.size)
    }

    @Test
    fun testRemovingOneTab() {
        // Test tab
        val engineState1: EngineSessionState = mock()
        val closedTab = RecoverableTab(
            engineSessionState = engineState1,
            state = TabState(
                id = "first-tab",
                title = "Mozilla",
                url = "https://mozilla.org",
                lastAccess = System.currentTimeMillis()
            )
        )

        // Test tab
        val engineState2: EngineSessionState = mock()
        val secondClosedTab = RecoverableTab(
            engineSessionState = engineState2,
            state = TabState(
                id = "second-tab",
                title = "Pocket",
                url = "https://pocket.com",
                lastAccess = System.currentTimeMillis()
            )
        )

        val tabs = runBlocking(Dispatchers.IO) {
            storage.addTabState(closedTab)
            storage.addTabState(secondClosedTab)
            storage.getTabs().first()
        }

        assertEquals(2, engineStateStorage.data.size)
        assertEquals(2, tabs.size)
        assertEquals(closedTab.state.url, tabs[0].url)
        assertEquals(closedTab.state.title, tabs[0].title)
        assertEquals(closedTab.state.lastAccess, tabs[0].lastAccess)
        assertEquals(secondClosedTab.state.url, tabs[1].url)
        assertEquals(secondClosedTab.state.title, tabs[1].title)
        assertEquals(secondClosedTab.state.lastAccess, tabs[1].lastAccess)

        val newTabs = runBlocking(Dispatchers.IO) {
            storage.removeTab(tabs[0])
            storage.getTabs().first()
        }

        assertEquals(1, engineStateStorage.data.size)
        assertEquals(engineState2, engineStateStorage.data["second-tab"])
        assertEquals(1, newTabs.size)
        assertEquals(secondClosedTab.state.url, newTabs[0].url)
        assertEquals(secondClosedTab.state.title, newTabs[0].title)
        assertEquals(secondClosedTab.state.lastAccess, newTabs[0].lastAccess)
    }

    @Test
    fun testAddingTabWithEngineStateStorageFailure() {
        // 'fail' in tab's id will cause test engine session storage to fail on writing engineSessionState.
        val closedTab = RecoverableTab(
            engineSessionState = mock(),
            state = TabState(
                id = "second-tab-fail",
                title = "Pocket",
                url = "https://pocket.com",
                lastAccess = System.currentTimeMillis()
            )
        )

        val tabs = runBlocking(Dispatchers.IO) {
            storage.addTabState(closedTab)
            storage.getTabs().first()
        }
        // if it's empty, we know state write failed
        assertEquals(0, engineStateStorage.data.size)
        // but the tab was still written into the database.
        assertEquals(1, tabs.size)
    }

    @Test
    fun testEngineSessionStorageObtainable() {
        assertEquals(engineStateStorage, storage.engineStateStorage())
    }

    @Test
    fun testStorageFailuresAreCaught() {
        val engineState: EngineSessionState = mock()
        val closedTab = RecoverableTab(
            engineSessionState = engineState,
            state = TabState(
                id = "second-tab-boom", // boom will cause an exception to be thrown
                title = "Pocket",
                url = "https://pocket.com",
                lastAccess = System.currentTimeMillis()
            )
        )
        runBlocking(Dispatchers.IO) {
            try {
                storage.addTabsToCollectionWithMax(listOf(closedTab), 2)
                verify(crashReporting).submitCaughtException(any())
            } catch (e: Exception) {
                fail("Thrown exception was not caught")
            }
        }
    }
}
