package com.awcjack.dualquickime.data

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** One disk owner; immutable snapshots can supersede saves, while clears are ordered barriers. */
internal class OrderedStoreWriter {
    private val revision = AtomicLong()
    private val pending = AtomicInteger()
    val isPending: Boolean get() = pending.get() > 0
    @Volatile var failed = false
        private set

    fun replace(save: () -> Unit) {
        val version = revision.incrementAndGet()
        enqueue { if (revision.get() == version) save() }
    }

    fun clear(clear: () -> Unit) {
        revision.incrementAndGet()
        enqueue(clear)
    }

    private fun enqueue(action: () -> Unit) {
        pending.incrementAndGet()
        worker.execute {
            try { workTrace("store-write", action); failed = false } catch (_: Exception) { failed = true }
            finally { pending.decrementAndGet() }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "HKIME-storage").apply { isDaemon = true }
        }
        internal fun awaitIdleForTests() { worker.submit {}.get(10, TimeUnit.SECONDS) }
    }
}
