/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.fxa

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mozilla.appservices.fxaclient.FxaConfig
import mozilla.appservices.fxaclient.FxaServer
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.sync.AccessTokenInfo
import mozilla.components.concept.sync.AccountEventsObserver
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthFlowUrl
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.DeviceCapability
import mozilla.components.concept.sync.DeviceConfig
import mozilla.components.concept.sync.DeviceConstellation
import mozilla.components.concept.sync.DeviceType
import mozilla.components.concept.sync.FxAEntryPoint
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.concept.sync.OAuthScopedKey
import mozilla.components.concept.sync.Profile
import mozilla.components.concept.sync.ServiceResult
import mozilla.components.concept.sync.StatePersistenceCallback
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.manager.GlobalAccountManager
import mozilla.components.service.fxa.manager.SyncEnginesStorage
import mozilla.components.service.fxa.sync.SyncDispatcher
import mozilla.components.service.fxa.sync.SyncManager
import mozilla.components.service.fxa.sync.SyncReason
import mozilla.components.service.fxa.sync.SyncStatusObserver
import mozilla.components.support.base.observer.Observable
import mozilla.components.support.base.observer.ObserverRegistry
import mozilla.components.support.test.any
import mozilla.components.support.test.argumentCaptor
import mozilla.components.support.test.eq
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.support.test.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

internal fun testAuthFlowUrl(entrypoint: String = "test-entrypoint"): AuthFlowUrl {
    return AuthFlowUrl(EXPECTED_AUTH_STATE, "https://example.com/auth-flow-start?entrypiont=$entrypoint&state=$EXPECTED_AUTH_STATE")
}

internal class TestableStorageWrapper(
    manager: FxaAccountManager,
    accountEventObserverRegistry: ObserverRegistry<AccountEventsObserver>,
    serverConfig: FxaConfig,
    private val block: () -> FirefoxAccount = {
        val account: FirefoxAccount = mock()
        `when`(account.deviceConstellation()).thenReturn(mock())
        account
    },
) : StorageWrapper(manager, accountEventObserverRegistry, serverConfig) {
    override fun obtainAccount(): FirefoxAccount = block()
}

// Same as the actual account manager, except we get to control how FirefoxAccountShaped instances
// are created. This is necessary because due to some build issues (native dependencies not available
// within the test environment) we can't use fxaclient supplied implementation of FirefoxAccountShaped.
// Instead, we express all of our account-related operations over an interface.
internal open class TestableFxaAccountManager(
    context: Context,
    config: FxaConfig,
    private val storage: AccountStorage,
    capabilities: Set<DeviceCapability> = emptySet(),
    syncConfig: SyncConfig? = null,
    coroutineContext: CoroutineContext,
    crashReporter: CrashReporting? = null,
    block: () -> FirefoxAccount = {
        val account: FirefoxAccount = mock()
        `when`(account.deviceConstellation()).thenReturn(mock())
        account
    },
) : FxaAccountManager(context, config, DeviceConfig("test", DeviceType.UNKNOWN, capabilities), syncConfig, emptySet(), crashReporter, coroutineContext) {
    private val testableStorageWrapper = TestableStorageWrapper(this, accountEventObserverRegistry, serverConfig, block)

    override var syncStatusObserverRegistry = ObserverRegistry<SyncStatusObserver>()

    override fun getStorageWrapper(): StorageWrapper {
        return testableStorageWrapper
    }

    override fun getAccountStorage(): AccountStorage {
        return storage
    }

    override fun createSyncManager(config: SyncConfig): SyncManager = mock()
}

const val EXPECTED_AUTH_STATE = "goodAuthState"
const val UNEXPECTED_AUTH_STATE = "badAuthState"

@ExperimentalCoroutinesApi // for runTest
@RunWith(AndroidJUnit4::class)
class FxaAccountManagerTest {

    val entryPoint: FxAEntryPoint = mock<FxAEntryPoint>().apply {
        whenever(entryName).thenReturn("home-menu")
    }

    @After
    fun cleanup() {
        SyncAuthInfoCache(testContext).clear()
        SyncEnginesStorage(testContext).clear()
    }

    internal class TestSyncDispatcher(registry: ObserverRegistry<SyncStatusObserver>) : SyncDispatcher, Observable<SyncStatusObserver> by registry {
        val inner: SyncDispatcher = mock()
        override fun isSyncActive(): Boolean {
            return inner.isSyncActive()
        }

        override fun syncNow(
            reason: SyncReason,
            debounce: Boolean,
            customEngineSubset: List<SyncEngine>,
        ) {
            inner.syncNow(reason, debounce, customEngineSubset)
        }

        override fun startPeriodicSync(unit: TimeUnit, period: Long, initialDelay: Long) {
            inner.startPeriodicSync(unit, period, initialDelay)
        }

        override fun stopPeriodicSync() {
            inner.stopPeriodicSync()
        }

        override fun workersStateChanged(isRunning: Boolean) {
            inner.workersStateChanged(isRunning)
        }

        override fun close() {
            inner.close()
        }
    }

    internal class TestSyncManager(config: SyncConfig) : SyncManager(config) {
        val dispatcherRegistry = ObserverRegistry<SyncStatusObserver>()
        val dispatcher: TestSyncDispatcher = TestSyncDispatcher(dispatcherRegistry)

        private var dispatcherUpdatedCount = 0
        override fun createDispatcher(supportedEngines: Set<SyncEngine>): SyncDispatcher {
            return dispatcher
        }

        override fun dispatcherUpdated(dispatcher: SyncDispatcher) {
            dispatcherUpdatedCount++
        }
    }

    class TestSyncStatusObserver : SyncStatusObserver {
        var onStartedCount = 0
        var onIdleCount = 0
        var onErrorCount = 0

        override fun onStarted() {
            onStartedCount++
        }

        override fun onIdle() {
            onIdleCount++
        }

        override fun onError(error: Exception?) {
            onErrorCount++
        }
    }

    @Test
    fun `restored account state persistence`() = runTest {
        val accountStorage: AccountStorage = mock()
        val profile = Profile("testUid", "test@example.com", null, "Test Profile")
        val constellation: DeviceConstellation = mockDeviceConstellation()
        val account = statePersistenceTestableAccount(profile, constellation)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "http://auth-url/redirect"),
            accountStorage,
            setOf(DeviceCapability.SEND_TAB),
            null,
            this.coroutineContext,
        ) {
            account
        }

        `when`(constellation.finalizeDevice(eq(AuthType.Existing), any())).thenReturn(ServiceResult.Ok)
        // We have an account at the start.
        `when`(accountStorage.read()).thenReturn(account)

        verify(account, never()).registerPersistenceCallback(any())
        manager.start()

        // Assert that persistence callback is set.
        val captor = argumentCaptor<StatePersistenceCallback>()
        verify(account).registerPersistenceCallback(captor.capture())

        // Assert that ensureCapabilities fired, but not the device initialization (since we're restoring).
        verify(constellation).finalizeDevice(eq(AuthType.Existing), any())

        // Assert that persistence callback is interacting with the storage layer.
        captor.value.persist("test")
        verify(accountStorage).write("test")
    }

    @Test
    fun `restored account state persistence, finalizeDevice hit an intermittent error`() = runTest {
        val accountStorage: AccountStorage = mock()
        val profile = Profile("testUid", "test@example.com", null, "Test Profile")
        val constellation: DeviceConstellation = mockDeviceConstellation()
        val account = statePersistenceTestableAccount(profile, constellation)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "http://auth-url/redirect"),
            accountStorage,
            setOf(DeviceCapability.SEND_TAB),
            null,
            this.coroutineContext,
        ) {
            account
        }

        `when`(constellation.finalizeDevice(eq(AuthType.Existing), any())).thenReturn(ServiceResult.OtherError)
        // We have an account at the start.
        `when`(accountStorage.read()).thenReturn(account)

        verify(account, never()).registerPersistenceCallback(any())
        manager.start()

        // Assert that persistence callback is set.
        val captor = argumentCaptor<StatePersistenceCallback>()
        verify(account).registerPersistenceCallback(captor.capture())

        // Assert that finalizeDevice fired with a correct auth type. 3 times since we re-try.
        verify(constellation, times(3)).finalizeDevice(eq(AuthType.Existing), any())

        // Assert that persistence callback is interacting with the storage layer.
        captor.value.persist("test")
        verify(accountStorage).write("test")

        // Since we weren't able to finalize the account state, we're no longer authenticated.
        assertNull(manager.authenticatedAccount())
    }

    @Test
    fun `restored account state persistence, hit an auth error`() = runTest {
        val accountStorage: AccountStorage = mock()
        val profile = Profile("testUid", "test@example.com", null, "Test Profile")
        val constellation: DeviceConstellation = mockDeviceConstellation()
        val account = statePersistenceTestableAccount(profile, constellation, ableToRecoverFromAuthError = false)

        val accountObserver: AccountObserver = mock()
        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "http://auth-url/redirect"),
            accountStorage,
            setOf(DeviceCapability.SEND_TAB),
            null,
            this.coroutineContext,
        ) {
            account
        }

        manager.register(accountObserver)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.AuthError)
        // We have an account at the start.
        `when`(accountStorage.read()).thenReturn(account)

        verify(account, never()).registerPersistenceCallback(any())

        assertFalse(manager.accountNeedsReauth())
        verify(account, never()).authErrorDetected()
        verify(account, never()).checkAuthorizationStatus(any())
        verify(accountObserver, never()).onAuthenticationProblems()

        manager.start()

        assertTrue(manager.accountNeedsReauth())
        verify(accountObserver, times(1)).onAuthenticationProblems()
        verify(account).authErrorDetected()
        verify(account).checkAuthorizationStatus(any())
    }

    @Test(expected = FxaPanicException::class)
    fun `restored account state persistence, hit an fxa panic which is re-thrown`() = runTest {
        val accountStorage: AccountStorage = mock()
        val profile = Profile("testUid", "test@example.com", null, "Test Profile")
        val constellation: DeviceConstellation = mock()
        val account = statePersistenceTestableAccount(profile, constellation)

        val accountObserver: AccountObserver = mock()
        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "http://auth-url/redirect"),
            accountStorage,
            setOf(DeviceCapability.SEND_TAB),
            null,
            this.coroutineContext,
        ) {
            account
        }

        manager.register(accountObserver)

        // Hit a panic while we're restoring account.
        doAnswer {
            throw FxaPanicException("don't panic!")
        }.`when`(constellation).finalizeDevice(any(), any())

        // We have an account at the start.
        `when`(accountStorage.read()).thenReturn(account)

        verify(account, never()).registerPersistenceCallback(any())

        assertFalse(manager.accountNeedsReauth())
        verify(accountObserver, never()).onAuthenticationProblems()

        manager.start()
    }

    @Test
    fun `newly authenticated account state persistence`() = runTest {
        val accountStorage: AccountStorage = mock()
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val constellation: DeviceConstellation = mockDeviceConstellation()
        val account = statePersistenceTestableAccount(profile, constellation)
        val accountObserver: AccountObserver = mock()
        // We are not using the "prepareHappy..." helper method here, because our account isn't a mock,
        // but an actual implementation of the interface.
        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            setOf(DeviceCapability.SEND_TAB),
            null,
            this.coroutineContext,
        ) {
            account
        }

        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        manager.register(accountObserver)

        // Kick it off, we'll get into a "NotAuthenticated" state.
        manager.start()

        // Perform authentication.

        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        // Assert that initDevice fired, but not ensureCapabilities (since we're initing a new account).
        verify(constellation).finalizeDevice(eq(AuthType.Signin), any())

        // Assert that persistence callback is interacting with the storage layer.
        val captor = argumentCaptor<StatePersistenceCallback>()
        verify(account).registerPersistenceCallback(captor.capture())
        captor.value.persist("test")
        verify(accountStorage).write("test")
    }

    @Test
    fun `auth state verification while finishing authentication`() = runTest {
        val accountStorage: AccountStorage = mock()
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val constellation: DeviceConstellation = mockDeviceConstellation()
        val account = statePersistenceTestableAccount(profile, constellation)
        val accountObserver: AccountObserver = mock()
        // We are not using the "prepareHappy..." helper method here, because our account isn't a mock,
        // but an actual implementation of the interface.
        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            setOf(DeviceCapability.SEND_TAB),
            null,
            this.coroutineContext,
        ) {
            account
        }

        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        manager.register(accountObserver)
        // Kick it off, we'll get into a "NotAuthenticated" state.
        manager.start()

        // Attempt to finish authentication without starting it first.
        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", UNEXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() == null)

        // Start authentication. statePersistenceTestableAccount will produce state=EXPECTED_AUTH_STATE.
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))

        // Now attempt to finish it with a correct state.
        `when`(constellation.finalizeDevice(eq(AuthType.Signin), any())).thenReturn(ServiceResult.Ok)
        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        // Assert that manager is authenticated.
        assertEquals(account, manager.authenticatedAccount())
    }

    suspend fun statePersistenceTestableAccount(profile: Profile, constellation: DeviceConstellation, ableToRecoverFromAuthError: Boolean = false): FirefoxAccount {
        val account = mock<FirefoxAccount>()
        `when`(account.getProfile(anyBoolean())).thenReturn(profile)
        `when`(account.deviceConstellation()).thenReturn(constellation)
        `when`(account.checkAuthorizationStatus(any())).thenReturn(ableToRecoverFromAuthError)
        `when`(account.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl(entrypoint = "home-menu"))
        `when`(account.beginPairingFlow(any(), any(), any())).thenReturn(testAuthFlowUrl(entrypoint = "home-menu"))
        `when`(account.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        `when`(account.getCurrentDeviceId()).thenReturn("testFxaDeviceId")

        return account
    }

    @Test
    fun `error reading persisted account`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val readException = FxaNetworkException("pretend we failed to fetch the account")
        `when`(accountStorage.read()).thenThrow(readException)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = this.coroutineContext,
        )

        val accountObserver = object : AccountObserver {
            override fun onLoggedOut() {
                fail()
            }

            override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
                fail()
            }

            override fun onAuthenticationProblems() {
                fail()
            }

            override fun onProfileUpdated(profile: Profile) {
                fail()
            }
        }

        manager.register(accountObserver)
        manager.start()
    }

    @Test
    fun `no persisted account`() = runTest {
        val accountStorage = mock<AccountStorage>()
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = this.coroutineContext,
        )

        val accountObserver: AccountObserver = mock()

        manager.register(accountObserver)
        manager.start()

        verify(accountObserver, never()).onAuthenticated(any(), any())
        verify(accountObserver, never()).onProfileUpdated(any())
        verify(accountObserver, never()).onLoggedOut()

        verify(accountStorage, times(1)).read()
        verify(accountStorage, never()).write(any())
        verify(accountStorage, never()).clear()

        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())
    }

    @Test
    fun `with persisted account and profile`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        val profile = Profile(
            "testUid",
            "test@example.com",
            null,
            "Test Profile",
        )
        `when`(mockAccount.getProfile(ignoreCache = false)).thenReturn(profile)
        // We have an account at the start.
        `when`(accountStorage.read()).thenReturn(mockAccount)
        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(eq(AuthType.Existing), any())).thenReturn(ServiceResult.Ok)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            emptySet(),
            null,
            this.coroutineContext,
        )

        val accountObserver: AccountObserver = mock()

        manager.register(accountObserver)

        manager.start()

        // Make sure that account and profile observers are fired exactly once.
        verify(accountObserver, times(1)).onAuthenticated(mockAccount, AuthType.Existing)
        verify(accountObserver, times(1)).onProfileUpdated(profile)
        verify(accountObserver, never()).onLoggedOut()

        verify(accountStorage, times(1)).read()
        verify(accountStorage, never()).write(any())
        verify(accountStorage, never()).clear()

        assertEquals(mockAccount, manager.authenticatedAccount())
        assertEquals(profile, manager.accountProfile())

        // Make sure 'logoutAsync' clears out state and fires correct observers.
        reset(accountObserver)
        reset(accountStorage)
        `when`(mockAccount.disconnect()).thenReturn(true)

        // Simulate SyncManager populating SyncEnginesStorage with some state.
        SyncEnginesStorage(testContext).setStatus(SyncEngine.History, true)
        SyncEnginesStorage(testContext).setStatus(SyncEngine.Passwords, false)
        assertTrue(SyncEnginesStorage(testContext).getStatus().isNotEmpty())

        verify(mockAccount, never()).disconnect()
        manager.logout()

        assertTrue(SyncEnginesStorage(testContext).getStatus().isEmpty())
        verify(accountObserver, never()).onAuthenticated(any(), any())
        verify(accountObserver, never()).onProfileUpdated(any())
        verify(accountObserver, times(1)).onLoggedOut()
        verify(mockAccount, times(1)).disconnect()

        verify(accountStorage, never()).read()
        verify(accountStorage, never()).write(any())
        verify(accountStorage, times(1)).clear()

        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())
    }

    @Test
    fun `happy authentication and profile flow`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        val accountObserver: AccountObserver = mock()
        val manager = prepareHappyAuthenticationFlow(mockAccount, profile, accountStorage, accountObserver, this.coroutineContext)

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountStorage, times(1)).read()
        verify(accountStorage, never()).clear()

        verify(accountObserver, times(1)).onAuthenticated(mockAccount, AuthType.Signin)
        verify(accountObserver, times(1)).onProfileUpdated(profile)
        verify(accountObserver, never()).onLoggedOut()

        assertEquals(mockAccount, manager.authenticatedAccount())
        assertEquals(profile, manager.accountProfile())

        val cachedAuthInfo = SyncAuthInfoCache(testContext).getCached()
        assertNotNull(cachedAuthInfo)
        assertEquals("kid", cachedAuthInfo!!.kid)
        assertEquals("someToken", cachedAuthInfo.fxaAccessToken)
        assertEquals("k", cachedAuthInfo.syncKey)
        assertEquals("some://url", cachedAuthInfo.tokenServerUrl)
        assertTrue(cachedAuthInfo.fxaAccessTokenExpiresAt > 0)
    }

    @Test(expected = FxaPanicException::class)
    fun `fxa panic during initDevice flow`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        val accountObserver: AccountObserver = mock()
        val manager = prepareHappyAuthenticationFlow(mockAccount, profile, accountStorage, accountObserver, this.coroutineContext)

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        doAnswer {
            throw FxaPanicException("Don't panic!")
        }.`when`(constellation).finalizeDevice(any(), any())

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)
    }

    @Test(expected = FxaPanicException::class)
    fun `fxa panic during pairing flow`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(mock())
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        `when`(mockAccount.getProfile(ignoreCache = false)).thenReturn(profile)

        doAnswer {
            throw FxaPanicException("Don't panic!")
        }.`when`(mockAccount).beginPairingFlow(any(), any(), any())
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = coroutineContext,
        ) {
            mockAccount
        }

        manager.start()
        manager.beginAuthentication("http://pairing.com", entryPoint)
        fail()
    }

    @Test
    fun `happy pairing authentication and profile flow`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        val accountObserver: AccountObserver = mock()
        val manager = prepareHappyAuthenticationFlow(mockAccount, profile, accountStorage, accountObserver, this.coroutineContext)

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(pairingUrl = "auth://pairing", entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountStorage, times(1)).read()
        verify(accountStorage, never()).clear()

        verify(accountObserver, times(1)).onAuthenticated(mockAccount, AuthType.Signin)
        verify(accountObserver, times(1)).onProfileUpdated(profile)
        verify(accountObserver, never()).onLoggedOut()

        assertEquals(mockAccount, manager.authenticatedAccount())
        assertEquals(profile, manager.accountProfile())
    }

    @Test
    fun `repeated unfinished authentication attempts succeed`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        val accountObserver: AccountObserver = mock()
        val manager = prepareHappyAuthenticationFlow(mockAccount, profile, accountStorage, accountObserver, this.coroutineContext)

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        // Begin auth for the first time.
        reset(accountObserver)
        assertEquals(
            testAuthFlowUrl(entrypoint = "home-menu").url,
            manager.beginAuthentication(
                pairingUrl = "auth://pairing",
                entrypoint = entryPoint,
            ),
        )
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        // Now, try to begin again before finishing the first one.
        assertEquals(
            testAuthFlowUrl(entrypoint = "home-menu").url,
            manager.beginAuthentication(
                pairingUrl = "auth://pairing",
                entrypoint = entryPoint,
            ),
        )
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        // The rest should "just work".
        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountStorage, times(1)).read()
        verify(accountStorage, never()).clear()

        verify(accountObserver, times(1)).onAuthenticated(mockAccount, AuthType.Signin)
        verify(accountObserver, times(1)).onProfileUpdated(profile)
        verify(accountObserver, never()).onLoggedOut()

        assertEquals(mockAccount, manager.authenticatedAccount())
        assertEquals(profile, manager.accountProfile())
    }

    @Test
    fun `unhappy authentication flow`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountObserver: AccountObserver = mock()
        val manager = prepareUnhappyAuthenticationFlow(mockAccount, profile, accountStorage, accountObserver, this.coroutineContext)

        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)

        assertNull(manager.beginAuthentication(entrypoint = entryPoint))

        // Confirm that account state observable doesn't receive authentication errors.
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        // Try again, without any network problems this time.
        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl())
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        assertEquals(testAuthFlowUrl().url, manager.beginAuthentication(entrypoint = entryPoint))

        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())
        verify(accountStorage, times(1)).clear()

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountStorage, times(1)).read()
        verify(accountStorage, times(1)).clear()

        verify(accountObserver, times(1)).onAuthenticated(mockAccount, AuthType.Signin)
        verify(accountObserver, times(1)).onProfileUpdated(profile)
        verify(accountObserver, never()).onLoggedOut()

        assertEquals(mockAccount, manager.authenticatedAccount())
        assertEquals(profile, manager.accountProfile())
    }

    @Test
    fun `unhappy pairing authentication flow`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountObserver: AccountObserver = mock()
        val manager = prepareUnhappyAuthenticationFlow(mockAccount, profile, accountStorage, accountObserver, this.coroutineContext)

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)

        assertNull(manager.beginAuthentication(pairingUrl = "auth://pairing", entrypoint = entryPoint))

        // Confirm that account state observable doesn't receive authentication errors.
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        // Try again, without any network problems this time.
        `when`(
            mockAccount.beginPairingFlow(
                anyString(),
                any(),
                any(),
            ),
        ).thenReturn(testAuthFlowUrl())
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        assertEquals(
            testAuthFlowUrl().url,
            manager.beginAuthentication(
                pairingUrl = "auth://pairing",
                entrypoint = entryPoint,
            ),
        )

        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())
        verify(accountStorage, times(1)).clear()

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountStorage, times(1)).read()
        verify(accountStorage, times(1)).clear()

        verify(accountObserver, times(1)).onAuthenticated(mockAccount, AuthType.Signin)
        verify(accountObserver, times(1)).onProfileUpdated(profile)
        verify(accountObserver, never()).onLoggedOut()

        assertEquals(mockAccount, manager.authenticatedAccount())
        assertEquals(profile, manager.accountProfile())
    }

    @Test
    fun `authentication issues are propagated via AccountObserver`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        val accountObserver: AccountObserver = mock()
        val manager = prepareHappyAuthenticationFlow(mockAccount, profile, accountStorage, accountObserver, this.coroutineContext)

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountObserver, never()).onAuthenticationProblems()
        assertFalse(manager.accountNeedsReauth())

        // Our recovery flow should attempt to hit this API. Model the "can't recover" condition by returning 'false'.
        `when`(mockAccount.checkAuthorizationStatus(eq("profile"))).thenReturn(false)

        // At this point, we're logged in. Trigger a 401.
        manager.encounteredAuthError("a test")

        verify(accountObserver, times(1)).onAuthenticationProblems()
        assertTrue(manager.accountNeedsReauth())
        assertEquals(mockAccount, manager.authenticatedAccount())

        // Make sure profile is still available.
        assertEquals(profile, manager.accountProfile())

        // Able to re-authenticate.
        reset(accountObserver)
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))

        manager.finishAuthentication(FxaAuthData(AuthType.Pairing, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountObserver).onAuthenticated(mockAccount, AuthType.Pairing)
        verify(accountObserver, never()).onAuthenticationProblems()
        assertFalse(manager.accountNeedsReauth())
        assertEquals(profile, manager.accountProfile())
    }

    @Test
    fun `authentication issues are recoverable via checkAuthorizationState`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        val accountObserver: AccountObserver = mock()
        val crashReporter: CrashReporting = mock()
        val manager = prepareHappyAuthenticationFlow(
            mockAccount,
            profile,
            accountStorage,
            accountObserver,
            this.coroutineContext,
            setOf(DeviceCapability.SEND_TAB),
            crashReporter,
        )

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)
        `when`(constellation.refreshDevices()).thenReturn(true)

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountObserver, never()).onAuthenticationProblems()
        assertFalse(manager.accountNeedsReauth())

        // Recovery flow will hit this API, and will recover if it returns 'true'.
        `when`(mockAccount.checkAuthorizationStatus(eq("profile"))).thenReturn(true)

        // At this point, we're logged in. Trigger a 401.
        manager.encounteredAuthError("a test")
        assertRecovered(true, "a test", constellation, accountObserver, manager, mockAccount, crashReporter)
    }

    @Test
    fun `authentication recovery flow has a circuit breaker`() = runTest {
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        val profile = Profile(uid = "testUID", avatar = null, email = "test@example.com", displayName = "test profile")
        val accountStorage = mock<AccountStorage>()
        val accountObserver: AccountObserver = mock()
        val crashReporter: CrashReporting = mock()
        val manager = prepareHappyAuthenticationFlow(
            mockAccount,
            profile,
            accountStorage,
            accountObserver,
            this.coroutineContext,
            setOf(DeviceCapability.SEND_TAB),
            crashReporter,
        )
        GlobalAccountManager.setInstance(manager)

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl(entrypoint = "home-menu").url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)
        `when`(constellation.refreshDevices()).thenReturn(true)

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountObserver, never()).onAuthenticationProblems()
        assertFalse(manager.accountNeedsReauth())

        // Recovery flow will hit this API, and will recover if it returns 'true'.
        `when`(mockAccount.checkAuthorizationStatus(eq("profile"))).thenReturn(true)

        // At this point, we're logged in. Trigger a 401 for the first time.
        manager.encounteredAuthError("a test")
        // ... and just for good measure, trigger another 401 to simulate overlapping API calls legitimately hitting 401s.
        manager.encounteredAuthError("a test", errorCountWithinTheTimeWindow = 3)
        assertRecovered(true, "a test", constellation, accountObserver, manager, mockAccount, crashReporter)

        // We've fully recovered by now, let's hit another 401 sometime later (count has been reset).
        manager.encounteredAuthError("a test")
        assertRecovered(true, "a test", constellation, accountObserver, manager, mockAccount, crashReporter)

        // Suddenly, we're in a bad loop, expect to hit our circuit-breaker here.
        manager.encounteredAuthError("another test", errorCountWithinTheTimeWindow = 50)
        assertRecovered(false, "another test", constellation, accountObserver, manager, mockAccount, crashReporter)
    }

    private suspend fun assertRecovered(
        success: Boolean,
        operation: String,
        constellation: DeviceConstellation,
        accountObserver: AccountObserver,
        manager: FxaAccountManager,
        mockAccount: OAuthAccount,
        crashReporter: CrashReporting,
    ) {
        // During recovery, only 'sign-in' finalize device call should have been made.
        verify(constellation, times(1)).finalizeDevice(eq(AuthType.Signin), any())
        verify(constellation, never()).finalizeDevice(eq(AuthType.Recovered), any())

        assertEquals(mockAccount, manager.authenticatedAccount())

        if (success) {
            // Since we've recovered, outside observers should not have witnessed the momentary problem state.
            verify(accountObserver, never()).onAuthenticationProblems()
            assertFalse(manager.accountNeedsReauth())
            verify(crashReporter, never()).submitCaughtException(any())
        } else {
            // We were unable to recover, outside observers should have been told.
            verify(accountObserver, times(1)).onAuthenticationProblems()
            assertTrue(manager.accountNeedsReauth())

            val captor = argumentCaptor<Throwable>()
            verify(crashReporter).submitCaughtException(captor.capture())
            assertEquals("Auth recovery circuit breaker triggered by: $operation", captor.value.message)
            assertTrue(captor.value is AccountManagerException.AuthRecoveryCircuitBreakerException)
        }
    }

    @Test
    fun `unhappy profile fetching flow`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()

        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)
        `when`(mockAccount.getProfile(ignoreCache = false)).thenReturn(null)
        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl())
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = this.coroutineContext,
        ) {
            mockAccount
        }

        val accountObserver: AccountObserver = mock()

        manager.register(accountObserver)
        manager.start()

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl().url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        verify(accountStorage, times(1)).read()
        verify(accountStorage, never()).clear()

        verify(accountObserver, times(1)).onAuthenticated(mockAccount, AuthType.Signin)
        verify(accountObserver, never()).onProfileUpdated(any())
        verify(accountObserver, never()).onLoggedOut()

        assertEquals(mockAccount, manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        // Make sure we can re-try fetching a profile. This time, let's have it succeed.
        reset(accountObserver)
        val profile = Profile(
            uid = "testUID",
            avatar = null,
            email = "test@example.com",
            displayName = "test profile",
        )

        `when`(mockAccount.getProfile(ignoreCache = true)).thenReturn(profile)
        assertNull(manager.accountProfile())
        assertEquals(profile, manager.refreshProfile(true))

        verify(accountObserver, times(1)).onProfileUpdated(profile)
        verify(accountObserver, never()).onAuthenticated(any(), any())
        verify(accountObserver, never()).onLoggedOut()
    }

    @Test
    fun `profile fetching flow hit an unrecoverable auth problem`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        // Our recovery flow should attempt to hit this API. Model the "can't recover" condition by returning false.
        `when`(mockAccount.checkAuthorizationStatus(eq("profile"))).thenReturn(false)

        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl())
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = this.coroutineContext,
        ) {
            mockAccount
        }

        lateinit var waitFor: Job
        `when`(mockAccount.getProfile(ignoreCache = anyBoolean())).then {
            // Hit an auth error.
            waitFor = CoroutineScope(coroutineContext).launch { manager.encounteredAuthError("a test") }
            null
        }

        val accountObserver: AccountObserver = mock()

        manager.register(accountObserver)
        manager.start()

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())
        verify(accountObserver, never()).onAuthenticationProblems()
        verify(mockAccount, never()).checkAuthorizationStatus(any())
        assertFalse(manager.accountNeedsReauth())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl().url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))

        waitFor.join()
        assertTrue(manager.accountNeedsReauth())
        verify(accountObserver, times(1)).onAuthenticationProblems()
        verify(mockAccount, times(1)).checkAuthorizationStatus(eq("profile"))
        Unit
    }

    @Test
    fun `profile fetching flow hit an unrecoverable auth problem for which we can't determine a recovery state`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()

        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        // Our recovery flow should attempt to hit this API. Model the "don't know what's up" condition by returning null.
        `when`(mockAccount.checkAuthorizationStatus(eq("profile"))).thenReturn(null)

        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl())
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = this.coroutineContext,
        ) {
            mockAccount
        }

        lateinit var waitFor: Job
        `when`(mockAccount.getProfile(ignoreCache = anyBoolean())).then {
            // Hit an auth error.
            waitFor = CoroutineScope(coroutineContext).launch { manager.encounteredAuthError("a test") }
            null
        }

        val accountObserver: AccountObserver = mock()

        manager.register(accountObserver)
        manager.start()

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())
        verify(accountObserver, never()).onAuthenticationProblems()
        verify(mockAccount, never()).checkAuthorizationStatus(any())
        assertFalse(manager.accountNeedsReauth())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl().url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)

        waitFor.join()
        assertTrue(manager.accountNeedsReauth())
        verify(accountObserver, times(1)).onAuthenticationProblems()
        verify(mockAccount, times(1)).checkAuthorizationStatus(eq("profile"))
        Unit
    }

    @Test
    fun `profile fetching flow hit a recoverable auth problem`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()
        val captor = argumentCaptor<AuthType>()

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)

        val profile = Profile(
            uid = "testUID",
            avatar = null,
            email = "test@example.com",
            displayName = "test profile",
        )

        // Recovery flow will hit this API, return a success.
        `when`(mockAccount.checkAuthorizationStatus(eq("profile"))).thenReturn(true)

        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl())
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = this.coroutineContext,
        ) {
            mockAccount
        }

        var didFailProfileFetch = false
        lateinit var waitFor: Job
        `when`(mockAccount.getProfile(ignoreCache = false)).then {
            // Hit an auth error, but only once. As we recover from it, we'll attempt to fetch a profile
            // again. At that point, we'd like to succeed.
            if (!didFailProfileFetch) {
                didFailProfileFetch = true
                waitFor = CoroutineScope(coroutineContext).launch { manager.encounteredAuthError("a test") }
                null
            } else {
                profile
            }
        }
        // Upon recovery, we'll hit an 'ignore cache' path.
        `when`(mockAccount.getProfile(ignoreCache = anyBoolean())).thenReturn(profile)

        val accountObserver: AccountObserver = mock()

        manager.register(accountObserver)
        manager.start()

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())
        verify(accountObserver, never()).onAuthenticationProblems()
        verify(mockAccount, never()).checkAuthorizationStatus(any())
        assertFalse(manager.accountNeedsReauth())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl().url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        manager.finishAuthentication(FxaAuthData(AuthType.Signup, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)
        waitFor.join()
        assertFalse(manager.accountNeedsReauth())
        assertEquals(mockAccount, manager.authenticatedAccount())
        assertEquals(profile, manager.accountProfile())
        verify(accountObserver, never()).onAuthenticationProblems()
        // Once for the initial auth success, then once again after we recover from an auth problem.
        verify(accountObserver, times(2)).onAuthenticated(eq(mockAccount), captor.capture())
        assertEquals(AuthType.Signup, captor.allValues[0])
        assertEquals(AuthType.Recovered, captor.allValues[1])
        // Verify that we went through the recovery flow.
        verify(mockAccount, times(1)).checkAuthorizationStatus(eq("profile"))
        Unit
    }

    @Test(expected = FxaPanicException::class)
    fun `profile fetching flow hit an fxa panic, which is re-thrown`() = runTest {
        val accountStorage = mock<AccountStorage>()
        val mockAccount: FirefoxAccount = mock()
        val constellation: DeviceConstellation = mock()

        `when`(mockAccount.getCurrentDeviceId()).thenReturn("testDeviceId")
        `when`(mockAccount.deviceConstellation()).thenReturn(constellation)
        `when`(constellation.finalizeDevice(any(), any())).thenReturn(ServiceResult.Ok)
        doAnswer {
            throw FxaPanicException("500")
        }.`when`(mockAccount).getProfile(ignoreCache = anyBoolean())
        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl())
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = this.coroutineContext,
        ) {
            mockAccount
        }

        val accountObserver: AccountObserver = mock()

        manager.register(accountObserver)
        manager.start()

        // We start off as logged-out, but the event won't be called (initial default state is assumed).
        verify(accountObserver, never()).onLoggedOut()
        verify(accountObserver, never()).onAuthenticated(any(), any())
        verify(accountObserver, never()).onAuthenticationProblems()
        assertFalse(manager.accountNeedsReauth())

        reset(accountObserver)
        assertEquals(testAuthFlowUrl().url, manager.beginAuthentication(entrypoint = entryPoint))
        assertNull(manager.authenticatedAccount())
        assertNull(manager.accountProfile())

        manager.finishAuthentication(FxaAuthData(AuthType.Signin, "dummyCode", EXPECTED_AUTH_STATE))
        assertTrue(manager.authenticatedAccount() != null)
    }

    @Test
    fun `accounts to sync integration`() {
        val syncManager: SyncManager = mock()
        val integration = FxaAccountManager.AccountsToSyncIntegration(syncManager)

        // onAuthenticated - mapping of AuthType to SyncReason
        integration.onAuthenticated(mock(), AuthType.Signin)
        verify(syncManager, times(1)).start()
        verify(syncManager, times(1)).now(eq(SyncReason.FirstSync), anyBoolean(), eq(listOf()))
        integration.onAuthenticated(mock(), AuthType.Signup)
        verify(syncManager, times(2)).start()
        verify(syncManager, times(2)).now(eq(SyncReason.FirstSync), anyBoolean(), eq(listOf()))
        integration.onAuthenticated(mock(), AuthType.Pairing)
        verify(syncManager, times(3)).start()
        verify(syncManager, times(3)).now(eq(SyncReason.FirstSync), anyBoolean(), eq(listOf()))
        integration.onAuthenticated(mock(), AuthType.MigratedReuse)
        verify(syncManager, times(4)).start()
        verify(syncManager, times(4)).now(eq(SyncReason.FirstSync), anyBoolean(), eq(listOf()))
        integration.onAuthenticated(mock(), AuthType.OtherExternal("test"))
        verify(syncManager, times(5)).start()
        verify(syncManager, times(5)).now(eq(SyncReason.FirstSync), anyBoolean(), eq(listOf()))
        integration.onAuthenticated(mock(), AuthType.Existing)
        verify(syncManager, times(6)).start()
        verify(syncManager, times(1)).now(eq(SyncReason.Startup), anyBoolean(), eq(listOf()))
        integration.onAuthenticated(mock(), AuthType.Recovered)
        verify(syncManager, times(7)).start()
        verify(syncManager, times(2)).now(eq(SyncReason.Startup), anyBoolean(), eq(listOf()))
        verifyNoMoreInteractions(syncManager)

        // onProfileUpdated - no-op
        integration.onProfileUpdated(mock())
        verifyNoMoreInteractions(syncManager)

        // onAuthenticationProblems
        integration.onAuthenticationProblems()
        verify(syncManager).stop()
        verifyNoMoreInteractions(syncManager)

        // onLoggedOut
        integration.onLoggedOut()
        verify(syncManager, times(2)).stop()
        verifyNoMoreInteractions(syncManager)
    }

    @Test
    fun `GIVEN a sync observer WHEN registering it THEN add it to the sync observer registry`() {
        val fxaManager = TestableFxaAccountManager(
            context = testContext,
            config = mock(),
            storage = mock(),
            capabilities = setOf(DeviceCapability.SEND_TAB),
            syncConfig = null,
            coroutineContext = mock(),
        )
        fxaManager.syncStatusObserverRegistry = mock()
        val observer: SyncStatusObserver = mock()
        val lifecycleOwner: LifecycleOwner = mock()

        fxaManager.registerForSyncEvents(observer, lifecycleOwner, false)

        verify(fxaManager.syncStatusObserverRegistry).register(observer, lifecycleOwner, false)
        verifyNoMoreInteractions(fxaManager.syncStatusObserverRegistry)
    }

    @Test
    fun `GIVEN a sync observer WHEN unregistering it THEN remove it from the sync observer registry`() {
        val fxaManager = TestableFxaAccountManager(
            context = testContext,
            config = mock(),
            storage = mock(),
            capabilities = setOf(DeviceCapability.SEND_TAB),
            syncConfig = null,
            coroutineContext = mock(),
        )
        fxaManager.syncStatusObserverRegistry = mock()
        val observer: SyncStatusObserver = mock()

        fxaManager.unregisterForSyncEvents(observer)

        verify(fxaManager.syncStatusObserverRegistry).unregister(observer)
        verifyNoMoreInteractions(fxaManager.syncStatusObserverRegistry)
    }

    private suspend fun prepareHappyAuthenticationFlow(
        mockAccount: FirefoxAccount,
        profile: Profile,
        accountStorage: AccountStorage,
        accountObserver: AccountObserver,
        coroutineContext: CoroutineContext,
        capabilities: Set<DeviceCapability> = emptySet(),
        crashReporter: CrashReporting? = null,
    ): FxaAccountManager {
        val accessTokenInfo = AccessTokenInfo(
            "testSc0pe",
            "someToken",
            OAuthScopedKey("kty", "testSc0pe", "kid", "k"),
            System.currentTimeMillis() + 1000 * 10,
        )

        `when`(mockAccount.getProfile(ignoreCache = anyBoolean())).thenReturn(profile)
        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(testAuthFlowUrl(entrypoint = "home-menu"))
        `when`(mockAccount.beginPairingFlow(anyString(), any(), any())).thenReturn(testAuthFlowUrl(entrypoint = "home-menu"))
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        `when`(mockAccount.getAccessToken(anyString())).thenReturn(accessTokenInfo)
        `when`(mockAccount.getTokenServerEndpointURL()).thenReturn("some://url")

        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            capabilities,
            SyncConfig(setOf(SyncEngine.History, SyncEngine.Bookmarks), PeriodicSyncConfig()),
            coroutineContext = coroutineContext,
            crashReporter = crashReporter,
        ) {
            mockAccount
        }

        manager.register(accountObserver)
        manager.start()

        return manager
    }

    private suspend fun prepareUnhappyAuthenticationFlow(
        mockAccount: FirefoxAccount,
        profile: Profile,
        accountStorage: AccountStorage,
        accountObserver: AccountObserver,
        coroutineContext: CoroutineContext,
    ): FxaAccountManager {
        `when`(mockAccount.getProfile(ignoreCache = anyBoolean())).thenReturn(profile)
        `when`(mockAccount.deviceConstellation()).thenReturn(mock())
        `when`(mockAccount.beginOAuthFlow(any(), any())).thenReturn(null)
        `when`(mockAccount.beginPairingFlow(anyString(), any(), any())).thenReturn(null)
        `when`(mockAccount.completeOAuthFlow(anyString(), anyString())).thenReturn(true)
        // There's no account at the start.
        `when`(accountStorage.read()).thenReturn(null)

        val manager = TestableFxaAccountManager(
            testContext,
            FxaConfig(FxaServer.Release, "dummyId", "bad://url"),
            accountStorage,
            coroutineContext = coroutineContext,
        ) {
            mockAccount
        }

        manager.register(accountObserver)

        manager.start()

        return manager
    }

    private suspend fun mockDeviceConstellation(): DeviceConstellation {
        val c: DeviceConstellation = mock()
        `when`(c.refreshDevices()).thenReturn(true)
        return c
    }
}
