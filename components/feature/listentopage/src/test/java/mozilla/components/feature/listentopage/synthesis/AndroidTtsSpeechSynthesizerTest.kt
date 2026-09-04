/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.synthesis

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice as TtsVoice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import mozilla.components.feature.listentopage.Voice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowTextToSpeech

private const val NOT_INSTALLED = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED

@RunWith(AndroidJUnit4::class)
class AndroidTtsSpeechSynthesizerTest {

    private lateinit var synthesizer: AndroidTtsSpeechSynthesizer

    @Before
    fun setUp() {
        synthesizer = AndroidTtsSpeechSynthesizer(TextToSpeech(ApplicationProvider.getApplicationContext<Context>()) {})
    }

    @After
    fun tearDown() {
        ShadowTextToSpeech.reset()
    }

    @Test
    fun `test that a voice needing a network connection is not offered`() {
        installVoice("en-us-network", Locale.US, requiresNetwork = true)

        assertEquals(emptyList<Voice>(), synthesizer.loadAvailableVoices("en-US"))
    }

    @Test
    fun `test that a voice that is not installed on the device is not offered`() {
        installVoice("en-us-absent", Locale.US, features = setOf(NOT_INSTALLED))

        assertEquals(emptyList<Voice>(), synthesizer.loadAvailableVoices("en-US"))
    }

    @Test
    fun `test that only the offline voices are offered when the engine mixes them`() {
        installVoice("en-us-offline", Locale.US)
        installVoice("en-us-network", Locale.US, requiresNetwork = true)
        installVoice("en-us-absent", Locale.US, features = setOf(NOT_INSTALLED))

        assertEquals(listOf(Voice(id = "en-us-offline")), synthesizer.loadAvailableVoices("en-US"))
    }

    @Test
    fun `test that every offline voice of the matched language is offered`() {
        installVoice("en-us-gonzo", Locale.US)
        installVoice("en-us-animal", Locale.US)
        installVoice("de-de-gonzo", Locale.GERMANY)

        val voices = synthesizer.loadAvailableVoices("en-US")

        assertEquals(setOf(Voice(id = "en-us-gonzo"), Voice(id = "en-us-animal")), voices.toSet())
    }

    @Test
    fun `test that the exact region is preferred over another region of the same language`() {
        installVoice("en-gb-gonzo", Locale.UK)
        installVoice("en-us-gonzo", Locale.US)

        assertEquals(listOf(Voice(id = "en-gb-gonzo")), synthesizer.loadAvailableVoices("en-GB"))
    }

    @Test
    fun `test that another region of the same language is used when the exact region has none`() {
        installVoice("en-us-gonzo", Locale.US)

        assertEquals(listOf(Voice(id = "en-us-gonzo")), synthesizer.loadAvailableVoices("en-GB"))
    }

    @Test
    fun `test that a language with no offline voice is offered nothing`() {
        installVoice("de-de-gonzo", Locale.GERMANY)

        assertEquals(emptyList<Voice>(), synthesizer.loadAvailableVoices("ja-JP"))
    }

    @Test
    fun `test that an engine with no voices at all offers nothing`() {
        assertEquals(emptyList<Voice>(), synthesizer.loadAvailableVoices("en-US"))
    }

    @Test
    fun `test that a malformed language tag is offered nothing rather than throwing`() {
        installVoice("en-us-gonzo", Locale.US)

        assertEquals(emptyList<Voice>(), synthesizer.loadAvailableVoices("not a language tag"))
    }

    @Test
    fun `test that an empty language tag is offered nothing rather than throwing`() {
        installVoice("en-us-gonzo", Locale.US)

        assertEquals(emptyList<Voice>(), synthesizer.loadAvailableVoices(""))
    }

    @Test
    fun `test that voices are ranked by quality first and then by latency`() {
        installVoice("normal-fast", Locale.US, quality = TtsVoice.QUALITY_NORMAL, latency = TtsVoice.LATENCY_LOW)
        installVoice("high-slow", Locale.US, quality = TtsVoice.QUALITY_HIGH, latency = TtsVoice.LATENCY_HIGH)
        installVoice("high-fast", Locale.US, quality = TtsVoice.QUALITY_HIGH, latency = TtsVoice.LATENCY_LOW)
        installVoice("low-fast", Locale.US, quality = TtsVoice.QUALITY_LOW, latency = TtsVoice.LATENCY_VERY_LOW)

        assertEquals(
            listOf(Voice("high-fast"), Voice("high-slow"), Voice("normal-fast"), Voice("low-fast")),
            synthesizer.loadAvailableVoices("en-US"),
        )
    }

    @Test
    fun `test that voices of equal quality and latency are ranked by name`() {
        installVoice("en-us-zeta", Locale.US)
        installVoice("en-us-alpha", Locale.US)

        assertEquals(listOf(Voice("en-us-alpha"), Voice("en-us-zeta")), synthesizer.loadAvailableVoices("en-US"))
    }

    private fun installVoice(
        name: String,
        locale: Locale,
        quality: Int = TtsVoice.QUALITY_NORMAL,
        latency: Int = TtsVoice.LATENCY_NORMAL,
        requiresNetwork: Boolean = false,
        features: Set<String> = emptySet(),
    ) = ShadowTextToSpeech.addVoice(TtsVoice(name, locale, quality, latency, requiresNetwork, features))
}
