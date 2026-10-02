package com.awcjack.dualquickime.data

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Resource preparation is ordered; query work is coalesced to at most one waiting request. */
internal class CandidateWorker {
    internal val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HKIME-candidates").apply { isDaemon = true }
    }
    private val latest = AtomicReference<(() -> Unit)?>(null)
    private val draining = AtomicBoolean(false)
    fun submit(action: () -> Unit) {
        if (executor.isShutdown) return
        latest.set(action); startDrain()
    }
    private fun startDrain() {
        if (!draining.compareAndSet(false, true)) return
        try { executor.execute {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val action = latest.getAndSet(null) ?: break
                    workTrace("candidate-job", action)
                }
            } finally {
                draining.set(false)
                if (latest.get() != null && !executor.isShutdown) startDrain()
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { draining.set(false); latest.set(null) }
    }
    fun cancel() { latest.set(null) }
    fun close() { cancel(); executor.shutdownNow() }
}
