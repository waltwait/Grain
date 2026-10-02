package tw.luma.camera.performance

import android.os.Trace

/** Synchronous sections stay on one thread. No logs, labels or counters are built per frame. */
internal inline fun <T> grainTrace(name: String, block: () -> T): T {
    if (!Trace.isEnabled()) return block()
    Trace.beginSection(name)
    return try { block() } finally { Trace.endSection() }
}
