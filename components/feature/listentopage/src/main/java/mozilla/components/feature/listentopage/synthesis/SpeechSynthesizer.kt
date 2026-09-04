/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.synthesis

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import mozilla.components.feature.listentopage.Voice

/** Type that is used to manage the synthesis of speech from text. */
fun interface SpeechSynthesizer {
    /** Load the list of voices the engine currently has available. */
    fun loadAvailableVoices(langTag: String): List<Voice>

    companion object {
        /** Construct a SpeechSynthesizer using the standard Android TTS engine. */
        fun android(context: Context): SpeechSynthesizer =
            AndroidTtsSpeechSynthesizer(
                TextToSpeech(context) {
                    // no need to listen to init yet
                }
            )
    }
}

internal class AndroidTtsSpeechSynthesizer(private val tts: TextToSpeech) : SpeechSynthesizer {

    /*
    We fetch offline voices by ensuring that the voice both does not require a network connection and is already
    installed. We then match this to language tags, using ranges to construct best matches in the case of malformed
    tags.
     */
    override fun loadAvailableVoices(langTag: String): List<Voice> {
        val offlineVoices = runCatching {
            tts.voices.filter { it.isAvailableOffline() }
        }
            .getOrDefault(listOf())
        val ranges =
            listOfNotNull(
                    languageRangeOrNull(langTag), // "zh-TW" — exact first
                    languageRangeOrNull(Locale.forLanguageTag(langTag).language), // "zh" — then any region
                )
                .flatten()
        val bestMatch = Locale.filter(ranges, offlineVoices.map { it.locale }).firstOrNull()
        return bestMatch?.let {
            offlineVoices.filter { it.locale == bestMatch }.map { Voice(id = it.name) }
        } ?: emptyList()
    }

    private fun languageRangeOrNull(langTag: String) =
        Result.runCatching {
                Locale.LanguageRange.parse(langTag)
            }
            .getOrNull()

    private fun android.speech.tts.Voice.isAvailableOffline() =
        !isNetworkConnectionRequired && features.orEmpty().none { it == TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED }
}
