/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.containers

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.paging.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.state.ContainerState
import mozilla.components.browser.state.state.ContainerState.Color
import mozilla.components.browser.state.state.ContainerState.Icon
import mozilla.components.feature.containers.db.ContainerDatabase
import mozilla.components.feature.containers.db.ContainerEntity
import mozilla.components.feature.containers.db.toContainerEntity

/**
 * A storage implementation for organizing containers (contextual identities).
 */
internal class ContainerStorage(context: Context) : ContainerMiddleware.Storage {

    @VisibleForTesting
    internal var database: Lazy<ContainerDatabase> =
        lazy { ContainerDatabase.get(context) }
    val containerDao by lazy { database.value.containerDao() }

    /**
     * Adds a new [ContainerState].
     */
    override suspend fun addContainer(
        contextId: String,
        name: String,
        color: Color,
        icon: Icon,
    ) {
        containerDao.insertContainer(
            ContainerEntity(
                contextId = contextId,
                name = name,
                color = color,
                icon = icon,
            ),
        )
    }

    /**
     * Returns a [Flow] list of all the [ContainerState] instances.
     */
    override fun getContainers(): Flow<List<ContainerState>> {
        return containerDao.getContainers().map { list ->
            list.map { entity -> entity.toContainer() }
        }
    }

    /**
     * Returns all saved [ContainerState] instances as a [DataSource.Factory].
     */
    fun getContainersPaged(): DataSource.Factory<Int, ContainerState> = containerDao
        .getContainersPaged()
        .map { entity ->
            entity.toContainer()
        }

    /**
     * Removes the given [ContainerState].
     */
    override suspend fun removeContainer(container: ContainerState) {
        containerDao.deleteContainer(container.toContainerEntity())
    }
}
