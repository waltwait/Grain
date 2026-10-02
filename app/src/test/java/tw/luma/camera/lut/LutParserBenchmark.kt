package tw.luma.camera.lut

import org.junit.Assert.*
import org.junit.Test

/** Desktop JVM evidence only; timings are recorded, never used as a flaky pass/fail gate. */
class LutParserBenchmark {
    @Test fun measureRepresentativeAndMaximumGrid() {
        val management = Class.forName("java.lang.management.ManagementFactory")
        val bean = management.getMethod("getThreadMXBean").invoke(null)
        val allocationApi = Class.forName("com.sun.management.ThreadMXBean")
        val allocated = allocationApi.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val threadId = Thread::class.java.getMethod("getId").invoke(Thread.currentThread()) as Long
        for (size in listOf(33, 65)) {
            val source = "LUT_3D_SIZE $size\n" + "0.1 0.2 0.3\n".repeat(size * size * size)
            repeat(3) { CubeLut.parse(source.reader()) }
            val times = LongArray(5)
            val bytes = LongArray(5)
            repeat(5) { index ->
                val before = allocated.invoke(bean, threadId) as Long
                val start = System.nanoTime()
                val lut = CubeLut.parse(source.reader())
                times[index] = System.nanoTime() - start
                bytes[index] = (allocated.invoke(bean, threadId) as Long) - before
                assertEquals(size, lut.size)
                assertArrayEquals(floatArrayOf(.1f, .2f, .3f), lut.sample(.4f, .6f, .8f), .00001f)
            }
            println("GRAIN_LUT_BENCH size=$size median_ns=${times.sorted()[2]} median_allocated_bytes=${bytes.sorted()[2]} samples_ns=${times.joinToString(",")}")
        }
    }
}
