package com.awcjack.dualquickime.data

/**
 * Represents a single clipboard history item.
 */
data class ClipboardHistoryItem(
    val id: Long,                    // Unique identifier (timestamp-based)
    val text: String,                // The actual clipboard content
    val timestamp: Long,             // When it was added
    val isPinned: Boolean = false    // Whether this item is pinned
) {
    companion object {
        private val lastId = java.util.concurrent.atomic.AtomicLong()
        internal fun observeId(id: Long) { lastId.updateAndGet { maxOf(it, id) } }
        internal fun nextId(): Long = lastId.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }
        fun create(text: String, isPinned: Boolean = false): ClipboardHistoryItem {
            val now = System.currentTimeMillis()
            return ClipboardHistoryItem(
                id = nextId(),
                text = text,
                timestamp = now,
                isPinned = isPinned
            )
        }
    }
}
