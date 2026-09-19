/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.fxa.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mozilla.components.concept.sync.AccessTokenInfo
import mozilla.components.concept.sync.OAuthScopedKey
import mozilla.components.concept.sync.PeriodicSyncConfig
import mozilla.components.concept.sync.SyncConfig
import mozilla.components.concept.sync.SyncEngine
import mozilla.components.service.fxa.TestOAuthAccount
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.manager.GlobalAccountManager
import mozilla.components.service.fxa.manager.SCOPE_SYNC
import mozilla.components.service.fxa.sync.WorkManagerSyncWorker.Companion.SYNC_STAGGER_BUFFER_MS
import mozilla.components.service.fxa.sync.WorkManagerSyncWorker.Companion.engineSyncTimestamp
import mozilla.components.service.fxa.sync.helpers.TestSyncStateStorage
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.support.test.whenever
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class) // TestScope.runCurrent
@RunWith(AndroidJUnit4::class)
class WorkManagerSyncManagerTest {
    private lateinit var mockParam: WorkerParameters
    private lateinit var mockTags: Set<String>
    private lateinit var mockTaskExecutor: TaskExecutor

    private val defaultSyncConfig =
        SyncConfig(
            supportedEngines = setOf(SyncEngine.Tabs),
            periodicSyncConfig = null,
            syncDecouplingEnabled = false,
        )
    private val syncConfigWithDecoupling =
        SyncConfig(supportedEngines = setOf(SyncEngine.Tabs), periodicSyncConfig = null, syncDecouplingEnabled = true)
    private val testDispatcher = StandardTestDispatcher()
    private val accountManager = mock<FxaAccountManager>()

    @Before
    fun setUp() {
        mockParam = mock()
        mockTags = mock()
        mockTaskExecutor = mock()
        `when`(mockParam.taskExecutor).thenReturn(mockTaskExecutor)
        `when`(mockTaskExecutor.serialTaskExecutor).thenReturn(mock())
        `when`(mockParam.tags).thenReturn(mockTags)

        GlobalAccountManager.setInstance(accountManager)

        WorkManagerTestInitHelper.initializeTestWorkManager(
            testContext,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `GIVEN work is not set to be debounced THEN it is not considered to be synced within the buffer`() {
        `when`(mockTags.contains(SyncWorkerTag.Debounce.name)).thenReturn(false)

        engineSyncTimestamp["test"] = System.currentTimeMillis() - SYNC_STAGGER_BUFFER_MS - 100L
        engineSyncTimestamp["test2"] = System.currentTimeMillis()

        val workerManagerSyncWorker = WorkManagerSyncWorker(testContext, mockParam)

        assertFalse(workerManagerSyncWorker.isDebounced())
        assertFalse(workerManagerSyncWorker.lastSyncedWithinStaggerBuffer("test"))
        assertFalse(workerManagerSyncWorker.lastSyncedWithinStaggerBuffer("test2"))
    }

    @Test
    fun `GIVEN work is set to be debounced THEN last synced timestamp is compared to buffer`() {
        `when`(mockTags.contains(SyncWorkerTag.Debounce.name)).thenReturn(true)

        engineSyncTimestamp["test"] = System.currentTimeMillis() - SYNC_STAGGER_BUFFER_MS - 100L
        engineSyncTimestamp["test2"] = System.currentTimeMillis()

        val workerManagerSyncWorker = WorkManagerSyncWorker(testContext, mockParam)

        assert(workerManagerSyncWorker.isDebounced())
        assertFalse(workerManagerSyncWorker.lastSyncedWithinStaggerBuffer("test"))
        assert(workerManagerSyncWorker.lastSyncedWithinStaggerBuffer("test2"))
    }

    @Test
    fun `isWithinStaggerBuffer is true just inside the buffer and false at or beyond it`() {
        val now = 1_000_000L
        assertTrue(isWithinStaggerBuffer(lastSyncedMs = now - (SYNC_STAGGER_BUFFER_MS - 1), now = now))
        assertFalse(isWithinStaggerBuffer(lastSyncedMs = now - SYNC_STAGGER_BUFFER_MS, now = now))
    }

    @Test
    fun `GIVEN work is set to be debounced WHEN there is not a saved time stamp THEN work will not be debounced`() {
        `when`(mockTags.contains(SyncWorkerTag.Debounce.name)).thenReturn(true)

        val workerManagerSyncWorker = WorkManagerSyncWorker(testContext, mockParam)

        assert(workerManagerSyncWorker.isDebounced())
        assertFalse(workerManagerSyncWorker.lastSyncedWithinStaggerBuffer("test"))
    }

    @Test
    fun `WHEN workerStateChanged receives null state THEN nothing happens`() =
        runTest(testDispatcher) {
            val observer = FakeSyncStatusObserver()
            val syncManager = createSyncManager()

            syncManager.syncDispatcher?.workersStateChanged(null)

            assertTrue(observer.events.isEmpty())
            assertFalse(syncManager.isSyncActive())
        }

    @Test
    fun `WHEN workerStateChanged receives empty list THEN nothing happens`() =
        runTest(testDispatcher) {
            val observer = FakeSyncStatusObserver()
            val syncManager = createSyncManager(observer = observer)

            syncManager.syncDispatcher?.workersStateChanged(emptyList())

            assertTrue(observer.events.isEmpty())
            assertFalse(syncManager.isSyncActive())
        }

    @Test
    fun `WHEN workerStateChanged receives ENQUEUED state THEN nothing happens`() =
        runTest(testDispatcher) {
            val observer = FakeSyncStatusObserver()
            val syncManager = createSyncManager(observer = observer)

            syncManager.syncDispatcher?.workersStateChanged(listOf(WorkInfo.State.ENQUEUED))

            assertTrue(observer.events.isEmpty())
            assertFalse(syncManager.isSyncActive())
        }

    @Test
    fun `GIVEN sync decoupling is off, and an authenticated account exists, THEN connection state reports connected`() =
        runTest(testDispatcher) {
            whenever(accountManager.authenticatedAccount()).thenReturn(TestAccount())
            val syncManager = createSyncManager(syncConfig = defaultSyncConfig)

            syncManager.initialize()
            runCurrent()

            assertEquals(SyncConnectionState.Connected, syncManager.syncConnectionState.value)
        }

    @Test
    fun `GIVEN sync decoupling is off, and no authenticated account exists, THEN connection state reports disconnected`() =
        runTest(testDispatcher) {
            whenever(accountManager.authenticatedAccount()).thenReturn(null)
            val syncManager = createSyncManager(syncConfig = defaultSyncConfig)

            syncManager.initialize()
            runCurrent()

            assertEquals(SyncConnectionState.Disconnected, syncManager.syncConnectionState.value)
        }

    @Test
    fun `GIVEN sync decoupling is off, an authenticated account exists, and sync was disconnected, THEN the connection state still reports connected`() =
        runTest(testDispatcher) {
            whenever(accountManager.authenticatedAccount()).thenReturn(TestAccount())

            val syncStateStorage = TestSyncStateStorage()
            syncStateStorage.storeSyncConnected(connected = false)
            val syncManager = createSyncManager(syncConfig = defaultSyncConfig, syncStateStorage = syncStateStorage)

            syncManager.initialize()
            runCurrent()

            assertEquals(
                SyncConnectionState.Connected,
                syncManager.syncConnectionState.value,
                "Sync should be connected without sync decoupling when there is an authenticated account",
            )
        }

    @Test
    fun `GIVEN sync decoupling is on, and no authenticated account exists, THEN connection state reports disconnected`() =
        runTest(testDispatcher) {
            whenever(accountManager.connectedAccount()).thenReturn(null)

            val syncManager = createSyncManager(syncConfig = syncConfigWithDecoupling)

            syncManager.initialize()
            runCurrent()

            assertEquals(SyncConnectionState.Disconnected, syncManager.syncConnectionState.value)
        }

    @Test
    fun `GIVEN sync decoupling is on, an authenticated account exists, and sync state was never set, THEN connection state reports connected`() =
        runTest(testDispatcher) {
            whenever(accountManager.authenticatedAccount()).thenReturn(TestAccount())
            val syncManager = createSyncManager(syncConfig = syncConfigWithDecoupling)

            syncManager.initialize()
            runCurrent()

            assertEquals(SyncConnectionState.Connected, syncManager.syncConnectionState.value)
        }

    @Test
    fun `GIVEN sync decoupling is on, an authenticated account exists, and sync was disconnected, THEN connection state reports disconnected`() =
        runTest(testDispatcher) {
            whenever(accountManager.authenticatedAccount()).thenReturn(TestAccount())

            val syncStateStorage = TestSyncStateStorage()
            syncStateStorage.storeSyncConnected(connected = false)
            val syncManager =
                createSyncManager(syncConfig = syncConfigWithDecoupling, syncStateStorage = syncStateStorage)

            syncManager.initialize()
            runCurrent()

            assertEquals(SyncConnectionState.Disconnected, syncManager.syncConnectionState.value)
        }

    @Test
    fun `GIVEN sync decoupling is on, an authenticated account exists, and sync was connected, THEN connection state reports connected`() =
        runTest(testDispatcher) {
            whenever(accountManager.authenticatedAccount()).thenReturn(TestAccount())

            val syncStateStorage = TestSyncStateStorage()
            syncStateStorage.storeSyncConnected(connected = true)
            val syncManager =
                createSyncManager(syncConfig = syncConfigWithDecoupling, syncStateStorage = syncStateStorage)

            syncManager.initialize()
            runCurrent()

            assertEquals(SyncConnectionState.Connected, syncManager.syncConnectionState.value)
        }

    @Test
    fun `GIVEN the manager has not been initialized, THEN connection state reports uninitialized`() =
        runTest(testDispatcher) {
            whenever(accountManager.authenticatedAccount()).thenReturn(TestAccount())

            val syncManager = createSyncManager(syncConfig = syncConfigWithDecoupling)
            runCurrent()

            assertEquals(SyncConnectionState.Uninitialized, syncManager.syncConnectionState.value)
        }

    @Test
    fun `GIVEN no connected account exists, WHEN connect is called, THEN the ConnectResult is NeedsAuthentication`() =
        runTest(testDispatcher) {
            whenever(accountManager.connectedAccount()).thenReturn(null)

            val syncManager = createSyncManager()
            syncManager.initialize()
            runCurrent()

            val result = syncManager.connect(params = ConnectParams())

            assertEquals(ConnectResult.Failure.NeedsAuthentication, result)
        }

    @Test
    fun `GIVEN sync decoupling is on, and connected account does not have sync scope, WHEN connect is called, THEN ConnectResult is NeedsSyncAuthorization`() =
        runTest(testDispatcher) {
            whenever(accountManager.connectedAccount()).thenReturn(TestAccount(initialScopes = setOf("random_scope")))

            val syncManager = createSyncManager(syncConfig = syncConfigWithDecoupling)
            syncManager.initialize()
            runCurrent()

            val result = syncManager.connect(ConnectParams())

            assertEquals(ConnectResult.Failure.NeedsSyncAuthorization, result)
        }

    @Test
    fun `GIVEN sync decoupling is on, and connected account has sync scope, WHEN connect succeeds, THEN storage reflects that sync is connected`() =
        runTest(testDispatcher) {
            val account = TestAccount(initialScopes = setOf(SCOPE_SYNC))
            whenever(accountManager.connectedAccount()).thenReturn(account)
            whenever(accountManager.authenticatedAccount()).thenReturn(account)

            val syncStateStorage = TestSyncStateStorage()
            val syncManager =
                createSyncManager(
                    syncConfig = syncConfigWithDecoupling,
                    syncStateStorage = syncStateStorage,
                )
            syncManager.initialize()
            runCurrent()

            syncManager.connect(ConnectParams())

            val syncConnectedInStorage = syncStateStorage.syncConnected
            assertNotNull(syncConnectedInStorage, "Sync connected state in storage should not be null")
            assertTrue(syncConnectedInStorage, "Sync connected should be true in storage")
        }

    @Test
    fun `GIVEN sync decoupling is on, WHEN connect succeeds, THEN connection state reports connected ok`() =
        runTest(testDispatcher) {
            // connected account with sync scope exists to give a successful `connect()`
            val account = TestAccount(initialScopes = setOf(SCOPE_SYNC))
            whenever(accountManager.connectedAccount()).thenReturn(account)
            whenever(accountManager.authenticatedAccount()).thenReturn(account)

            val syncManager = createSyncManager(syncConfig = syncConfigWithDecoupling)
            syncManager.initialize()
            syncManager.connect(ConnectParams())
            runCurrent()

            assertEquals(
                SyncConnectionState.Connected,
                syncManager.syncConnectionState.value,
                "Sync should be connected",
            )
        }

    @Test
    fun `GIVEN sync decoupling is on, and sync previously disconnected, WHEN connect succeeds, THEN connection state reports connected`() =
        runTest(testDispatcher) {
            val account = TestAccount()
            whenever(accountManager.connectedAccount()).thenReturn(account)
            whenever(accountManager.authenticatedAccount()).thenReturn(account)

            val syncStateStorage = TestSyncStateStorage()
            syncStateStorage.storeSyncConnected(connected = false)

            val syncManager =
                createSyncManager(
                    syncStateStorage = syncStateStorage,
                    syncConfig = syncConfigWithDecoupling,
                )
            syncManager.initialize()
            syncManager.connect(ConnectParams())
            runCurrent()

            assertEquals(
                SyncConnectionState.Connected,
                syncManager.syncConnectionState.value,
                "Sync should be connected",
            )
        }

    @Test
    fun `GIVEN sync decoupling is on, sync is disconnected, WHEN connect is called with unsupported engines, THEN an IllegalStateException is thrown and sync remains disconnected`() =
        runTest(testDispatcher) {
            val account = TestAccount(initialScopes = setOf(SCOPE_SYNC))
            whenever(accountManager.connectedAccount()).thenReturn(account)
            val syncStateStorage = TestSyncStateStorage()
            syncStateStorage.storeSyncConnected(connected = false)

            val syncManager =
                createSyncManager(
                    syncConfig = syncConfigWithDecoupling,
                    syncStateStorage = syncStateStorage,
                    start = false,
                )
            syncManager.initialize()
            runCurrent()

            assertFailsWith<IllegalStateException> {
                syncManager.connect(ConnectParams(engines = setOf(SyncEngine.Passwords), initiateSync = true))
            }

            assertEquals(
                SyncConnectionState.Disconnected,
                syncManager.syncConnectionState.value,
                "Sync should not be connected",
            )
        }

    @Test
    fun `GIVEN connected account with sync scope, WHEN connect is called with initiateSync=true, THEN immediate sync is initiated`() =
        runTest(testDispatcher) {
            whenever(accountManager.connectedAccount()).thenReturn(TestAccount(initialScopes = setOf(SCOPE_SYNC)))

            val syncManager =
                createSyncManager(
                    syncConfig = defaultSyncConfig,
                    start = false,
                )
            syncManager.initialize()
            runCurrent()

            syncManager.connect(ConnectParams(initiateSync = true))

            val workManager = WorkManager.getInstance(testContext)
            val immediateWork =
                workManager.getWorkInfos(WorkQuery.fromUniqueWorkNames(SyncWorkerName.Immediate.name)).get()
            assertEquals(1, immediateWork.size, "Unexpected count of immediate sync work")
        }

    @Test
    fun `GIVEN connected account with sync scope, WHEN connect is called with initiateSync=false, THEN no immediate sync is initiated`() =
        runTest(testDispatcher) {
            whenever(accountManager.connectedAccount()).thenReturn(TestAccount(initialScopes = setOf(SCOPE_SYNC)))

            val syncManager =
                createSyncManager(
                    syncConfig = defaultSyncConfig.copy(periodicSyncConfig = PeriodicSyncConfig()),
                    start = false,
                )
            syncManager.initialize()
            runCurrent()

            syncManager.connect(ConnectParams(initiateSync = false))

            val workManager = WorkManager.getInstance(testContext)
            val immediateWork =
                workManager.getWorkInfos(WorkQuery.fromUniqueWorkNames(SyncWorkerName.Immediate.name)).get()
            assertEquals(0, immediateWork.size, "Unexpected count of immediate sync work")
        }

    @Test
    fun `GIVEN connected account with sync scope and no periodic sync configured, WHEN connect succeeds, THEN no periodic sync is initiated`() =
        runTest(testDispatcher) {
            whenever(accountManager.connectedAccount()).thenReturn(TestAccount(initialScopes = setOf(SCOPE_SYNC)))

            val syncManager =
                createSyncManager(
                    syncConfig = syncConfigWithDecoupling.copy(periodicSyncConfig = null),
                    start = true,
                )
            syncManager.initialize()
            runCurrent()

            syncManager.connect(ConnectParams(initiateSync = false))

            val periodicWork =
                WorkManager.getInstance(testContext)
                    .getWorkInfos(WorkQuery.fromUniqueWorkNames(SyncWorkerName.Periodic.name))
                    .get()
            assertTrue(periodicWork.isEmpty(), "Expected no periodic sync work")
        }

    private fun createSyncManager(
        observer: FakeSyncStatusObserver = FakeSyncStatusObserver(),
        syncStateStorage: SyncStateStorage = TestSyncStateStorage(),
        syncConfig: SyncConfig = defaultSyncConfig,
        syncStateStorageProvider: SyncStateStorage.Provider = SyncStateStorage.Provider { syncStateStorage },
        start: Boolean = true,
    ): WorkManagerSyncManager =
        WorkManagerSyncManager(
                context = testContext,
                syncConfig = syncConfig,
                rustSyncManager = TestRustSyncManager(),
                syncStateStorageProvider = syncStateStorageProvider,
                coroutineContext = testDispatcher,
            )
            .apply {
                registerSyncStatusObserver(observer)
                if (start) start()
            }

    private class TestAccount(initialScopes: Set<String> = setOf(SCOPE_SYNC)) : TestOAuthAccount() {
        private val grantedScopes: MutableSet<String> = initialScopes.toMutableSet()

        override fun hasScope(scope: String): Boolean = scope in grantedScopes

        override suspend fun getAccessToken(singleScope: String): AccessTokenInfo {
            return AccessTokenInfo(
                scope = SCOPE_SYNC,
                token = "token",
                key =
                    OAuthScopedKey(
                        kty = "kty",
                        scope = SCOPE_SYNC,
                        kid = "kid",
                        k = "k",
                    ),
                expiresAt = 15L,
            )
        }

        override suspend fun getTokenServerEndpointURL(): String {
            return "token server url"
        }
    }
}
