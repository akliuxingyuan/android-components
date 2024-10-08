/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.prompts.ext

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditTextTest {

    @Test
    fun `GIVEN a callback, WHEN action done is performed - IME_ACTION_DONE -, THEN onDonePress should be called`() {
        var donePressed = false
        val onDonePressed: () -> Unit = { donePressed = true }
        val view = EditText(testContext)
        val editorInfo = EditorInfo()
        val inputConnection = view.onCreateInputConnection(editorInfo)

        view.onDone(false, onDonePressed)
        inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)

        assertTrue(donePressed)
    }

    @Test
    fun `GIVEN a callback, WHEN a different action is performed - IME_ACTION_SEARCH -, THEN onDonePress shouldn't be called `() {
        var donePressed = false
        val onDonePressed: () -> Unit = { donePressed = true }

        val view = EditText(testContext)
        val editorInfo = EditorInfo()
        val inputConnection = view.onCreateInputConnection(editorInfo)

        view.onDone(false, onDonePressed)
        inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEARCH)

        assertFalse(donePressed)
    }
}
