package com.awcjack.dualquickime.data

import android.os.Trace

/** Stage names only; never include queries, editor metadata or clipboard contents. */
internal inline fun <T> workTrace(stage: String, action: () -> T): T {
    Trace.beginSection("HKIME:$stage")
    return try { action() } finally { Trace.endSection() }
}
