/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.playback

import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import mozilla.components.support.base.log.logger.Logger

/**
 * Owns the [ListenPlayer] that reads an article out loud.
 *
 * The service owns the player, and no caller does, because the audio has to keep playing when the app is not on screen.
 * A player held by a view or a fragment cannot outlive it.
 *
 * Media3 promotes this service to the foreground and publishes the playback notification whenever playback is commanded
 * through the session. That is why [ListenPlaybackController] is the only way in: driving the player directly loses
 * both the notification and the background audio, and it fails silently.
 */
internal class ListenMediaSessionService : MediaSessionService() {

    private val logger = Logger("ListenMediaSessionService")

    private var listenPlayer: ListenPlayer? = null
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val player = ListenPlayer(this)
        listenPlayer = player
        mediaSession = MediaSession.Builder(this, player.exoPlayer).build()

        setListener(
            object : Listener {
                @RequiresApi(Build.VERSION_CODES.S)
                override fun onForegroundServiceStartNotAllowedException() {
                    logger.warn("Refused the foreground, so playback cannot be started from the background")
                    endPlayback()
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = super.onStartCommand(intent, flags, startId)

        if (intent == null || intent.isNotificationDismissal()) {
            endPlayback()
            return START_NOT_STICKY
        }

        return started
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        endPlayback()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        releasePlayback()
        super.onDestroy()
    }

    /** Pauses the reading, takes the notification away and ends the service */
    @OptIn(UnstableApi::class)
    private fun endPlayback() {
        pauseAllPlayersAndStopSelf()
        releasePlayback()
    }

    private fun releasePlayback() {
        mediaSession?.release()
        mediaSession = null

        listenPlayer?.release()
        listenPlayer = null
    }
}

/**
 * Media3 records the dismissal and stops republishing the notification, but it leaves the service running, so the
 * service has to read the same intent to know that it should end.
 */
@OptIn(UnstableApi::class)
internal fun Intent.isNotificationDismissal(): Boolean =
    getBooleanExtra(MediaNotification.NOTIFICATION_DISMISSED_EVENT_KEY, false)
