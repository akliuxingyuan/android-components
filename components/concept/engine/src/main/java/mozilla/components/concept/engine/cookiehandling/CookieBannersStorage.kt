/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.concept.engine.cookiehandling

import mozilla.components.concept.engine.EngineSession.CookieBannerHandlingMode

/**
 * Represents a storage to manage [CookieBannerHandlingMode] exceptions.
 */
interface CookieBannersStorage {
    /**
     * Set the [CookieBannerHandlingMode.DISABLED] mode for the given [uri] and [privateBrowsing].
     * @param uri the [uri] for the site to be updated.
     * @param privateBrowsing Indicates if given [uri] should be in private browsing or not.
     */
    suspend fun addException(
        uri: String,
        privateBrowsing: Boolean,
    )

    /**
     * Check if the given site's domain url is saved locally.
     * @param siteDomain the [siteDomain] that will be checked.
     */
    suspend fun isSiteDomainReported(siteDomain: String): Boolean

    /**
     * Save the given site's domain url in datastore to keep it persistent locally.
     * This method gets called after the site domain was reported with Nimbus.
     * @param siteDomain the [siteDomain] that will be saved.
     */
    suspend fun saveSiteDomain(siteDomain: String)

    /**
     * Set persistently the [CookieBannerHandlingMode.DISABLED] mode for the given [uri] in
     * private browsing.
     * @param uri the [uri] for the site to be updated.
     */
    suspend fun addPersistentExceptionInPrivateMode(uri: String)

    /**
     * Find a [CookieBannerHandlingMode] that matches the given [uri] and browsing mode.
     * @param uri the [uri] to be used as filter in the search.
     * @param privateBrowsing Indicates if given [uri] should be in private browsing or not.
     * @return the [CookieBannerHandlingMode] for the provided [uri] and browsing mode,
     * if an error occurs null will be returned.
     */
    suspend fun findExceptionFor(uri: String, privateBrowsing: Boolean): CookieBannerHandlingMode?

    /**
     * Indicates if the given [uri] and browsing mode has the [CookieBannerHandlingMode.DISABLED] mode.
     * @param uri the [uri] to be used as filter in the search.
     * @param privateBrowsing Indicates if given [uri] should be in private browsing or not.
     * @return A [Boolean] indicating if the [CookieBannerHandlingMode] has been updated, from the
     * default value, if an error occurs null will be returned.
     */
    suspend fun hasException(uri: String, privateBrowsing: Boolean): Boolean?

    /**
     * Remove any [CookieBannerHandlingMode] exception that has been applied to the given [uri] and
     * browsing mode.
     * @param uri the [uri] to be used as filter in the search.
     * @param privateBrowsing Indicates if given [uri] should be in private browsing or not.
     */
    suspend fun removeException(uri: String, privateBrowsing: Boolean)
}
