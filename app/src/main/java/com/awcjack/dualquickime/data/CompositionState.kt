package com.awcjack.dualquickime.data

import com.awcjack.dualquickime.util.KeyMapping

/**
 * Tracks the current composition state during input.
 * Supports pagination for candidate selection.
 *
 * @property rawKeys The English letters typed (e.g., "qrofvd")
 * @property candidates All matching Chinese characters (ordered by frequency)
 * @property currentPage Current page index (0-based)
 * @property pageSize Number of candidates per page (default: 9)
 * @property activeKeyLength How many chars from the start of rawKeys are used for the current candidate lookup (1 or 2)
 */
data class CompositionState(
    val rawKeys: String = "",
    val candidates: List<String> = emptyList(),
    val currentPage: Int = 0,
    val pageSize: Int = 9,
    val activeKeyLength: Int = 0,
    val displayOffset: Int = 0,  // Actual start index for display (handles overflow)
    val lastDisplayedCount: Int = 0,  // How many were displayed on current page
    val previousOffsets: List<Int> = emptyList()
) {
    /**
     * The radical display string (e.g., "手口" for "qr")
     */
    val radicalDisplay: String
        get() = KeyMapping.getRadicalSequence(rawKeys)

    /**
     * Total number of pages (estimate based on pageSize).
     * Actual pagination is dynamic based on what fits on screen.
     */
    val totalPages: Int
        get() = if (candidates.isEmpty()) 0 else (candidates.size + pageSize - 1) / pageSize

    /**
     * Whether there are more candidates after the current display offset.
     */
    val hasMoreCandidates: Boolean
        get() = displayOffset + lastDisplayedCount < candidates.size

    /**
     * Candidates for the current page only.
     * Uses displayOffset for dynamic pagination based on what fits on screen.
     */
    val currentPageCandidates: List<String>
        get() {
            if (candidates.isEmpty()) return emptyList()
            val start = displayOffset
            val end = minOf(start + pageSize, candidates.size)
            return if (start < candidates.size) candidates.subList(start, end) else emptyList()
        }

    /**
     * Whether there are any candidates
     */
    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()

    /**
     * Whether the composition is empty (no keys typed)
     */
    val isEmpty: Boolean
        get() = rawKeys.isEmpty()

    /**
     * Move to the next page of candidates using dynamic offset.
     * Uses lastDisplayedCount to determine the next offset.
     * Wraps around to start when reaching the end.
     */
    fun nextPage(): CompositionState {
        if (candidates.size <= 1) return this
        val nextOffset = displayOffset + lastDisplayedCount.coerceAtLeast(1)
        return if (nextOffset >= candidates.size) {
            // Wrap to beginning
            copy(currentPage = 0, displayOffset = 0, previousOffsets = emptyList())
        } else {
            copy(currentPage = currentPage + 1, displayOffset = nextOffset,
                previousOffsets = previousOffsets + displayOffset)
        }
    }

    fun previousPage(): CompositionState {
        if (previousOffsets.isEmpty()) return this
        return copy(currentPage = (currentPage - 1).coerceAtLeast(0),
            displayOffset = previousOffsets.last(),
            previousOffsets = previousOffsets.dropLast(1))
    }

    /**
     * Update the displayed count after rendering.
     * Call this after setCandidates returns to track how many were shown.
     */
    fun withDisplayedCount(count: Int): CompositionState {
        return copy(lastDisplayedCount = count)
    }

    /**
     * Reset to page 0
     */
    fun resetPage(): CompositionState = copy(currentPage = 0, displayOffset = 0,
        lastDisplayedCount = 0, previousOffsets = emptyList())

    companion object {
        val EMPTY = CompositionState()
    }
}
