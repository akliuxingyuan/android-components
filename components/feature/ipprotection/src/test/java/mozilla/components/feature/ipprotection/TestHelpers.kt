/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:OptIn(ExperimentalAndroidComponentsApi::class)

package mozilla.components.feature.ipprotection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.feature.ipprotection.store.IPProtectionAction
import mozilla.components.feature.ipprotection.store.IPProtectionStore
import mozilla.components.feature.ipprotection.store.state.EligibilityStatus
import mozilla.components.feature.ipprotection.store.state.IPProtectionState
import mozilla.components.support.test.middleware.CaptureActionsMiddleware

internal class FakeEligibilityStorage : IPProtectionEligibilityStorage {
    private val statusFlow = MutableSharedFlow<EligibilityStatus>(replay = 1)

    var initCalled = false
        private set

    override val eligibilityStatus: Flow<EligibilityStatus> = statusFlow

    override fun init() {
        initCalled = true
    }

    fun emit(status: EligibilityStatus) {
        statusFlow.tryEmit(status)
    }
}

internal typealias IPProtectionTestMiddleware = CaptureActionsMiddleware<IPProtectionState, IPProtectionAction>

internal fun buildStore(
    initialState: IPProtectionState = IPProtectionState(),
): Pair<IPProtectionStore, IPProtectionTestMiddleware> {
    val middleware = IPProtectionTestMiddleware()
    val store = IPProtectionStore(initialState = initialState, middleware = listOf(middleware))
    return store to middleware
}
