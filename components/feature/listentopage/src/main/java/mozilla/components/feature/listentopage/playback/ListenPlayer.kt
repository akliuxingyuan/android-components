/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Plays the audio files the speech engine produces.
 *
 * One instance owns one [ExoPlayer] for its whole life. The player is built in the constructor and [release] is
 * terminal, so nothing can swap the underlying player out from under a listening session. Constructing this class
 * therefore acquires a resource: whoever builds it must release it.
 *
 * [ExoPlayer] is not thread safe, so every call must happen on the thread the instance was constructed on.
 *
 * @param context Used to build the player.
 */
class ListenPlayer(context: Context) {

    private val exoPlayer = ExoPlayer.Builder(context).build()

    /** Plays [file], replacing anything already playing. */
    fun play(file: File) {
        exoPlayer.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    /** Releases the player. This instance cannot be used afterwards. */
    fun release() {
        exoPlayer.release()
    }
}
