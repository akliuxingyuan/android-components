/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.concept.storage

/**
 * Storage that allows to stop and clean in progress operations.
 */
interface Cancellable {
    /**
     * Cleans up all background work and operations queue.
     */
    fun cleanup() {
        // no-op
    }

    /**
     * Cleans up all pending write operations.
     */
    fun cancelWrites() {
        // no-op
    }

    /**
     * Cleans up all pending read operations.
     */
    fun cancelReads() {
        // no-op
    }

    /**
     * Cleans up pending read operations in preparation for a new query.
     * This is useful when the same storage is shared between multiple functionalities and will
     * allow preventing overlapped cancel requests.
     *
     * @param nextQuery Next query to cancel reads for.
     */
    fun cancelReads(nextQuery: String) {
        // no-op
    }
}
