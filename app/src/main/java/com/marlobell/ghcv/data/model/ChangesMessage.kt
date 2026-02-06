package com.marlobell.ghcv.data.model

import androidx.health.connect.client.changes.Change

/**
 * Represents the messages that can be emitted by the Changes API Flow.
 * Used for differential sync to efficiently track Health Connect data changes.
 */
sealed class ChangesMessage {
    /**
     * Indicates no more changes are available.
     * Contains the next token to use for subsequent change queries.
     *
     * @property nextChangesToken Token to store and use for the next getChanges() call
     */
    data class NoMoreChanges(val nextChangesToken: String) : ChangesMessage()

    /**
     * Contains a list of changes from Health Connect.
     * Multiple ChangeList messages may be emitted if there are many changes.
     *
     * @property changes List of Change objects (insertions, updates, deletions)
     */
    data class ChangeList(val changes: List<Change>) : ChangesMessage()
}
