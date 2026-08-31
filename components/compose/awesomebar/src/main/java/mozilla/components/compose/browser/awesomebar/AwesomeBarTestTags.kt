/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.awesomebar

/** Test tags for the [AwesomeBar] composable. */
object AwesomeBarTestTags {
    private const val TAG = "mozac.awesomebar"

    /** Test tag of each individual suggestion shown in the AwesomeBar. */
    const val SUGGESTION = "$TAG.suggestion"

    /** Test tag for the list of all suggestions shown in the AwesomeBar. */
    const val SUGGESTIONS = "$TAG.suggestions"
}
