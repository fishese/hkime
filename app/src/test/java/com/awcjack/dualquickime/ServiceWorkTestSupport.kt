package com.awcjack.dualquickime

import android.os.Looper
import org.robolectric.Shadows
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Drain only this service's worker and ready callbacks, without advancing delayed save timers. */
internal fun settleServiceWork(service: HkInputMethodService) {
    val worker = ReflectionHelpers.getField<Any>(service, "candidateWorker")
    val executor = ReflectionHelpers.getField<ExecutorService>(worker, "executor")
    repeat(3) {
        executor.submit {}.get(10, TimeUnit.SECONDS)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
