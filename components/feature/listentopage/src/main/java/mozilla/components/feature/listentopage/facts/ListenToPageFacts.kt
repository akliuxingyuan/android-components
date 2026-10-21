/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.listentopage.facts

import mozilla.components.support.base.Component
import mozilla.components.support.base.facts.Action
import mozilla.components.support.base.facts.Fact
import mozilla.components.support.base.facts.collect

/** Facts emitted to measure how the listen to page feature performs. */
class ListenToPageFacts {
    /** Specify which fact occurred. */
    object Items {
        const val CHUNK_SYNTHESIZED = "chunk_synthesized"
    }

    /** Keys of the metadata a fact carries. */
    object MetadataKeys {
        const val CHUNK_SYNTHESIS = "chunk_synthesis"
    }

    /**
     * The engine finished making the audio of one chunk.
     *
     * @property index Which chunk of the article it is.
     * @property length The characters in the chunk.
     * @property synthesisMs How long the engine took to make it.
     * @property audioMs How long its audio lasts, or `null` when it could not be measured.
     */
    data class ChunkSynthesis(
        val index: Int,
        val length: Int,
        val synthesisMs: Long,
        val audioMs: Long?,
    )
}

internal fun emitChunkSynthesizedFact(chunkSynthesis: ListenToPageFacts.ChunkSynthesis) {
    Fact(
            component = Component.FEATURE_LISTENTOPAGE,
            action = Action.IMPLEMENTATION_DETAIL,
            item = ListenToPageFacts.Items.CHUNK_SYNTHESIZED,
            metadata = mapOf(ListenToPageFacts.MetadataKeys.CHUNK_SYNTHESIS to chunkSynthesis),
        )
        .collect()
}
