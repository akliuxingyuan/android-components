/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.prompts.emailmask

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import mozilla.components.compose.base.theme.AcornTheme

/**
 * The top-level view holder for the Email Mask Prompt Bar.
 */
class EmailMaskPromptBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {
    @Composable
    override fun Content() {
        AcornTheme {
            EmailMaskPromptBar(
                onMaskEmailClicked = {
                    // logic should be added in https://bugzilla.mozilla.org/show_bug.cgi?id=1996725
                },
            )
        }
    }
}
