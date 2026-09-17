/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.listentopage.content.Content
import mozilla.components.feature.listentopage.content.ContentProvider
import mozilla.components.feature.listentopage.content.TextChunker
import mozilla.components.feature.listentopage.playback.AudioFileCache
import mozilla.components.feature.listentopage.playback.ChunkAudio
import mozilla.components.feature.listentopage.playback.PlaybackController
import mozilla.components.feature.listentopage.settings.ListenSettings
import mozilla.components.feature.listentopage.synthesis.NoOfflineVoiceAvailableException
import mozilla.components.feature.listentopage.synthesis.NothingToReadException
import mozilla.components.feature.listentopage.synthesis.SpeechSynthesizer
import mozilla.components.feature.listentopage.synthesis.SynthesisQueue
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import mozilla.components.support.base.log.logger.Logger

/**
 * [Middleware] that extracts the article a listening session reads out, synthesizes it and plays it.
 *
 * @property contentProvider Provides the article text and language for a tab.
 * @property synthesizerProvider Builds the speech engine. A provider rather than an instance for two reasons: building
 *   one binds the platform engine over IPC, which must not happen on the caller's thread, and [SpeechSynthesizer.close]
 *   is terminal, so a session that closes its engine needs a way to get another one.
 * @property audioCache Holds the audio files. It is emptied when the session stops.
 * @property playbackController Plays the audio file. It is used instead of the player directly, because only playback
 *   commanded through the media session keeps the audio alive in the background and shows the notification.
 * @property settings The module's own preferences, holding the voice the user picked for each article language.
 * @property scope The [CoroutineScope] the extraction, the synthesis and the playback commands run in. It has to
 *   dispatch on one thread: the session fields below are read from `invoke`, which the store runs on whichever thread
 *   dispatched, and written from this scope.
 * @property ioDispatcher The dispatcher for the work that must not run on the thread the store dispatched on.
 * @property chunker Splits article text into the chunks.
 */
@Suppress("LongParameterList")
class ListenMiddleware(
    private val browserStore: BrowserStore,
    private val contentProvider: ContentProvider,
    private val synthesizerProvider: () -> SpeechSynthesizer,
    private val audioCache: AudioFileCache,
    private val playbackController: PlaybackController,
    private val settings: ListenSettings,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val chunker: TextChunker = TextChunker.android(),
) : Middleware<ListenState, ListenAction> {

    private val logger = Logger("ListenMiddleware")

    private var contentJob: Job? = null
    private var tabClosureJob: Job? = null
    private var voicesJob: Job? = null
    private var playbackStatusJob: Job? = null

    // The end of every session so far, chained.
    private var teardownJob: Job? = null

    // The article the session reads out.
    //
    // It carries the tab it was extracted for, so that an extraction finishing after its session ended cannot be read
    // by the session after it. Clearing it on stop is still not optional: the store is a singleton, so the middleware
    // is too, and an article left here would live until the process dies.
    private var article: Article? = null

    // The engine this session reads with, built on first use and closed when the session stops.
    private var synthesizer: SpeechSynthesizer? = null

    // What the session has made of the article, held so that the audio can be thrown away when the session ends.
    private var synthesisQueue: SynthesisQueue? = null

    // Which chunk of the article the player last reported reading out.
    private var playingChunk = NO_CHUNK

    // The last chunk that was enqueued to the player.
    private var appendedThrough = NO_CHUNK

    // The scope every piece of this session's synthesis runs in, so that ending the session cancels all of it at once,
    // including work that is still waiting its turn.
    private var sessionScope: CoroutineScope? = null

    // Orders the engine's requests. Work waits its turn rather than replacing what is running, because the engine
    // cannot cancel one request: its own stop() drops everything it holds.
    private val requestTurn = Mutex()

    // Guards building the engine, so that a request arriving while another is still binding cannot leave a second
    // engine bound with nothing to close it.
    private val synthesizerLock = Mutex()

    override fun invoke(
        store: Store<ListenState, ListenAction>,
        next: (ListenAction) -> Unit,
        action: ListenAction,
    ) {
        next(action)

        when (action) {
            is ListenAction.Session.ListenRequested -> {
                endSession(releasePlayback = false)
                observePlayback(store)
                requestContent(store, action.tabId)
                trackTabClosure(store, action.tabId)
            }

            ListenAction.Session.StopRequested -> endSession(releasePlayback = true)

            is ListenAction.Content.ContentReady -> {
                if (store.state.voiceState.availableVoices.isEmpty()) {
                    store.requestVoices(action.languageTag)
                }
                synthesizeAndPlay(store.state.tabId, store::dispatch)
            }
            is ListenAction.Voices.VoiceSelected -> store.state.languageTag?.let { action.voice.persistChoiceFor(it) }

            is ListenAction.Playback,
            ListenAction.Content.ContentUnavailable,
            is ListenAction.Voices.AvailableVoicesLoaded,
            ListenAction.Voices.NoOfflineVoicesAvailable,
            ListenAction.Synthesis.SynthesisFailed,
            ListenAction.ErrorDismissed -> Unit
        }
    }

    /**
     * Reports what the player is doing into the store, for as long as the session lasts. Since playback can be
     * controlled through the notification (or outside audio sources) we cannot create this state based on our commands.
     */
    private fun observePlayback(store: ListenStore) {
        playbackStatusJob?.cancel()
        playbackStatusJob = scope.launch {
            playbackController.status.collect { store.reportPlayback(it) }
        }
    }

    /**
     * Reports to the Store what [playback] represents in terms of the article. Will also manipulate the queue as
     * needed, since the player does not understand the context of the queue or full article.
     */
    private fun ListenStore.reportPlayback(playback: PlaybackState) {
        when (playback.phase) {
            PlaybackPhase.Failed -> dispatch(ListenAction.Playback.PlaybackFailed)
            PlaybackPhase.Ended -> refillOrEnd(this::dispatch)

            // If the player reaches a chunk that is not playing yet, it indicates the chunk is still loading
            PlaybackPhase.Playing if playback.chunk.index != playingChunk -> {
                playingChunk = playback.chunk.index
                dispatch(ListenAction.Playback.PlaybackStarted(playback.chunk, playback.positionMs))
                synthesizing(this::dispatch) {
                    synthesisQueue?.let {
                        it.workAheadOf(playingChunk)
                    }
                }
            }

            else -> dispatch(ListenAction.Playback.StateChangeObserved(playback))
        }
    }

    private fun trackTabClosure(store: Store<ListenState, ListenAction>, tabId: String) {
        tabClosureJob?.cancel()
        tabClosureJob = scope.launch {
            browserStore.stateFlow
                .first { it.findTab(tabId) == null }
                .let {
                    store.dispatch(ListenAction.Session.StopRequested)
                }
        }
    }

    private fun requestContent(store: Store<ListenState, ListenAction>, tabId: String) {
        contentJob?.cancel()
        contentJob = scope.launch {
            val content = contentProvider.getContent(tabId)

            // A session for another tab was started, or the session was stopped, while the article was extracted.
            if (store.state.tabId != tabId) {
                return@launch
            }

            content
                .onSuccess { store.readArticle(tabId, it) }
                .onFailure { store.dispatch(ListenAction.Content.ContentUnavailable) }
        }
    }

    /**
     * Takes [content] as the session's article, or reports that there is nothing to read out.
     *
     * The article is read in the language the page declares. Where it declares none this can use, it cannot be read at
     * all: the chunker needs a language to find the sentence boundaries in, and the voice lookup needs one to match a
     * voice against.
     *
     * @param tabId The tab [content] was extracted from.
     */
    private fun ListenStore.readArticle(tabId: String, content: Content) {
        if (content.text.isBlank()) {
            dispatch(ListenAction.Content.ContentUnavailable)
            return
        }

        val language = content.languageTag.asLanguageTagOrNull()
        if (language == null) {
            logger.debug("The page declares no usable language (\"${content.languageTag}\")")
            dispatch(ListenAction.Content.ContentUnavailable)
            return
        }

        article = Article(tabId = tabId, text = content.text, languageTag = language)
        dispatch(ListenAction.Content.ContentReady(languageTag = language))
    }

    private fun ListenStore.requestVoices(langTag: String) {
        voicesJob?.cancel()
        voicesJob =
            scope.launch(ioDispatcher) {
                val engine = synthesizer()
                settings.clearSavedVoicesOnEngineChange(engine.enginePackageName)
                val voices = engine.loadAvailableVoices(langTag)

                dispatch(
                    if (voices.isEmpty()) {
                        ListenAction.Voices.NoOfflineVoicesAvailable
                    } else {
                        ListenAction.Voices.AvailableVoicesLoaded(voices, voices.loadSavedVoiceFor(langTag))
                    }
                )
            }
    }

    private suspend fun List<Voice>.loadSavedVoiceFor(langTag: String): Voice {
        val savedId = settings.getSelectedVoiceId(langTag)
        return firstOrNull { it.id == savedId } ?: first()
    }

    private fun Voice.persistChoiceFor(langTag: String) {
        scope.launch(ioDispatcher) { settings.setSelectedVoiceId(langTag, id) }
    }

    /**
     * Synthesizes the opening of the article, plays it, and works ahead on what follows it.
     *
     * @param tabId The tab the live session is reading. A session that has already ended may still have written its
     *   article to the field, so anything extracted for a different tab is ignored.
     * @param dispatch Dispatch an action to the store.
     */
    private fun synthesizeAndPlay(tabId: String?, dispatch: (ListenAction) -> Unit) {
        val article = article?.takeIf { it.tabId == tabId } ?: return
        val firstChunkIndex = 0

        playingChunk = NO_CHUNK
        appendedThrough = NO_CHUNK

        synthesizing(dispatch) {
            val queue = SynthesisQueue(synthesizer(), ChunkAudio(audioCache), chunker, ioDispatcher)
            synthesisQueue = queue

            val opening = queue.startReading(article.text, article.languageTag)
            playbackController.play(opening)
            appendedThrough = firstChunkIndex

            // Runs ahead of the opening while it plays, so the chunk after it is queued behind it rather than started
            // when the opening ends. A chunk that arrives after the opening has finished cannot be joined onto it.
            queue.workAheadOf(playingChunk = firstChunkIndex)
        }
    }

    /**
     * The end of playback indicates either that queue synthesis is lagging or the article is complete. This determines
     * which case has been reached and reacts accordingly.
     *
     * @param dispatch Dispatch an action to the store.
     */
    private fun refillOrEnd(dispatch: (ListenAction) -> Unit) {
        val queue = synthesisQueue ?: return

        // Exit early if there are not additional chunks to synthesize
        val missing = appendedThrough + 1
        if (missing >= queue.chunkCount) {
            dispatch(ListenAction.Playback.PlaybackEnded)
            return
        }

        dispatch(ListenAction.Playback.PlaybackWaiting)

        synthesizing(dispatch) {
            // In case we receive a repeated report of playback ending we check to see if the next chunk has already
            // been queued
            if (appendedThrough >= missing) {
                return@synthesizing
            }

            val file = queue.audioFor(missing) ?: return@synthesizing
            playbackController.enqueue(file)
            appendedThrough = missing

            // Restart the player once there is synthesized content
            playbackController.resume()

            queue.workAheadOf(missing)
        }
    }

    /**
     * Fills the window around [playingChunk] without reporting what it fails with.
     *
     * The chunk a reader is waiting on is made by [SynthesisQueue.audioFor], which does report a failure. A chunk
     * further ahead than that is made again when playback reaches it, so failing to work ahead is not the session's
     * failure and must not put an error over a player that is still reading the article out.
     */
    private suspend fun SynthesisQueue.workAheadOf(playingChunk: Int) {
        try {
            moveWindowTo(playingChunk)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Could not work ahead on the article", e)
        }

        // now that the window has been synthesized, enqueue what was successfully synthesized with the player
        while (appendedThrough + 1 < chunkCount) {
            val next = appendedThrough + 1
            val file = fileFor(next) ?: return

            playbackController.enqueue(file)
            appendedThrough = next
        }
    }

    /** Runs [work] after whatever synthesis is already in flight, and reports whatever it fails with. */
    private fun synthesizing(dispatch: (ListenAction) -> Unit, work: suspend () -> Unit) {
        sessionScope().launch {
            try {
                teardownJob?.join()

                requestTurn.withLock { work() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: NothingToReadException) {
                dispatch(ListenAction.Content.ContentUnavailable)
            } catch (_: NoOfflineVoiceAvailableException) {
                // The engine only reaches for the network when it has no offline voice for the language, so a network
                // failure during synthesis means the same thing to the user as an empty voice list.
                dispatch(ListenAction.Voices.NoOfflineVoicesAvailable)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.error("Could not read the article out loud", e)
                dispatch(ListenAction.Synthesis.SynthesisFailed)
            }
        }
    }

    /** The scope this session's synthesis runs in, made on first use and cancelled when the session ends. */
    private fun sessionScope(): CoroutineScope =
        sessionScope
            ?: CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext.job)).also {
                sessionScope = it
            }

    /** The engine for this session, bound on first use because binding it is IPC. */
    private suspend fun synthesizer(): SpeechSynthesizer = synthesizerLock.withLock {
        synthesizer ?: withContext(ioDispatcher) { synthesizerProvider() }.also { synthesizer = it }
    }

    /**
     * Stops everything the session has in flight and throws away the audio it made.
     *
     * @param releasePlayback Whether to give up the playback and close the engine, which takes the notification away
     *   with the service. A session starting in place of this one keeps both: it replaces what is playing, and it reads
     *   with the same engine rather than paying to bind another.
     */
    private fun endSession(releasePlayback: Boolean) {
        contentJob?.cancel()
        voicesJob?.cancel()
        tabClosureJob?.cancel()
        article = null
        playingChunk = NO_CHUNK
        appendedThrough = NO_CHUNK

        val ending = sessionScope
        val emptying = synthesisQueue
        sessionScope = null
        synthesisQueue = null

        val closing = synthesizer.takeIf { releasePlayback }
        if (releasePlayback) {
            playbackStatusJob?.cancel()
            synthesizer = null
        }

        // Chained rather than replaced, so that waiting for the teardown waits for every teardown still to finish.
        val earlier = teardownJob
        teardownJob = scope.launch {
            earlier?.join()

            // Our cancellation asks the engine to stop but cannot un-write a file it has already produced, so nothing
            // below may delete the audio before the request in flight has finished with it.
            ending?.coroutineContext?.job?.cancelAndJoin()

            if (releasePlayback) {
                playbackController.release()

                // The engine is an IPC binding into the text to speech app, which stays alive for the rest of this
                // app's life unless it is shut down. Closing is terminal, which is why the next session builds its own.
                withContext(ioDispatcher) { closing?.close() }

                // Nothing else deletes the audio, so without this a session leaves its files behind for as long as the
                // system keeps the cache directory. Only safe once the playback above has given up the files.
                audioCache.clear()
            } else {
                // The playback carries on into the session replacing this one, so only this article's own audio goes.
                emptying?.clear()
            }
        }
    }
}

private const val NO_CHUNK = -1

/** The article of one listening session, the tab it was extracted from, and the language it is being read as. */
private class Article(val tabId: String, val text: String, val languageTag: String)

/** This language tag in the form everything downstream works from, or `null` when it names no language. */
private fun String.asLanguageTagOrNull(): String? =
    trim().replace('_', '-').takeIf { Locale.forLanguageTag(it).language.isNotEmpty() }
