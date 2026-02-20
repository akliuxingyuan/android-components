/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.support.ktx.android.view

import android.app.Activity
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.view.View
import android.view.WindowManager
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.onApplyWindowInsets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import mozilla.components.support.base.log.logger.Logger

private const val IMMERSIVE_MODE_WINDOW_INSETS_LISTENER = "IMMERSIVE_MODE_WINDOW_INSETS_LISTENER"

/**
 * Attempts to enter immersive mode - fullscreen with the status bar and navigation buttons hidden,
 * expanding itself into the notch area for devices running API 28+.
 *
 * This will automatically register and use an inset listener: [View.OnApplyWindowInsetsListener]
 * to restore immersive mode if interactions with various other widgets like the keyboard or dialogs
 * got the activity out of immersive mode without [exitImmersiveMode] being called.
 *
 * @param setListenerFunction is an optional function to setup an WindowInsets listener:
 * [View.OnApplyWindowInsetsListener] to allow having multiple listeners at the same time.
 */
fun Activity.enterImmersiveMode(
    insetsController: WindowInsetsControllerCompat = window.createWindowInsetsController(),
    setOnApplyWindowInsetsListener: (String, OnApplyWindowInsetsListener) ->
    Unit = { _, listener -> ViewCompat.setOnApplyWindowInsetsListener(window.decorView, listener) },
) {
    insetsController.hideInsets()

    val insetsListener = OnApplyWindowInsetsListener { view, insetsCompat ->
        if (insetsCompat.isVisible(WindowInsetsCompat.Type.statusBars())) {
            insetsController.hideInsets()
        }
        // Allow the decor view to have a chance to process the incoming WindowInsets.
        onApplyWindowInsets(view, insetsCompat)
    }

    setOnApplyWindowInsetsListener(IMMERSIVE_MODE_WINDOW_INSETS_LISTENER, insetsListener)

    tryEnableEnterEdgeToEdge()
}

private fun WindowInsetsControllerCompat.hideInsets() {
    apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/**
 * Shows the system UI windows that were hidden, thereby exiting the immersive experience.
 * For devices running API 28+, this function also restores the application's use
 * of the notch area of the phone to the default behavior.
 *
 * @param insetsController is an optional [WindowInsetsControllerCompat] object for controlling the
 * window insets.
 * @param removeListenerFunction is an optional function which was used for [enterImmersiveMode].
 */
fun Activity.exitImmersiveMode(
    insetsController: WindowInsetsControllerCompat = window.createWindowInsetsController(),
    unregisterOnApplyWindowInsetsListener: (String) ->
    Unit = { ViewCompat.setOnApplyWindowInsetsListener(window.decorView, null) },
) {
    insetsController.show(WindowInsetsCompat.Type.systemBars())

    unregisterOnApplyWindowInsetsListener(IMMERSIVE_MODE_WINDOW_INSETS_LISTENER)

    tryDisableEdgeToEdge()
}

/**
 * Calls [Activity.reportFullyDrawn] while also preventing crashes under some circumstances.
 *
 * @param errorLogger the logger to be used if errors are logged.
 */
fun Activity.reportFullyDrawnSafe(errorLogger: Logger) {
    try {
        reportFullyDrawn()
    } catch (e: SecurityException) {
        // This exception is throw on some Samsung devices. We were unable to identify the root
        // cause but suspect it's related to Samsung security features. See
        // https://github.com/mozilla-mobile/fenix/issues/12345#issuecomment-655058864 for details.
        //
        // We include "Fully drawn" in the log statement so that this error appears when grepping
        // for fully drawn time.
        errorLogger.error("Fully drawn - unable to call reportFullyDrawn", e)
    }
}

/**
 * For devices running Android 9 Pie, force the given activity to enter edge-to-edge mode.
 *
 * We're using a subset of what [WindowCompat.enableEdgeToEdge] does mixed with our historical
 * display cutout handling.
 *
 * To use [WindowCompat.enableEdgeToEdge] directly, we have to manually update
 * [tryDisableEdgeToEdge] to do the inverse of [WindowCompat.enableEdgeToEdge], because unfortunately
 * there is no function to disable edge-to-edge in [WindowCompat].
 */
fun Activity.tryEnableEnterEdgeToEdge() {
    if (SDK_INT >= VERSION_CODES.P) {
        // This triggers the initialization of the decor view here to prevent the attributes set by
        // this method from getting overwritten by the initialization later.
        window.decorView

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSystemBarsBackground(navigationBarColor = Color.TRANSPARENT)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    if (SDK_INT >= VERSION_CODES.Q) {
        window.setNavigationBarContrastEnforced(false)
    }
}

/**
 * For devices running Android 9 Pie, force the given activity to exit edge-to-edge mode.
 */
fun Activity.tryDisableEdgeToEdge() {
    if (SDK_INT >= VERSION_CODES.P) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    }
}
