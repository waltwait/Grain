package tw.luma.camera.gl

import org.junit.Assert.*
import org.junit.Test

class RgbaReadbackTest {
    @Test fun decodesChannelsAndKeepsRowsOpaque() {
        val staging = RgbaReadback(4)
        staging.pixels.put(byteArrayOf(-1, 0, 0, 7, 0, -1, 0, 0, 0, 0, -1, -1, 18, 52, 86, 99))
        assertArrayEquals(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), 0xff123456.toInt()), staging.decode(4))
    }

    @Test fun reusesBuffersAndReadsOnlyTheEdgeTile() {
        val staging = RgbaReadback(2)
        staging.pixels.put(byteArrayOf(-1, 0, 0, 0, 0, -1, 0, 0))
        val previous = staging.decode(2)
        staging.pixels.clear()
        staging.pixels.put(byteArrayOf(0, 0, -1, 0))
        val edge = staging.decode(1)
        assertSame(previous, edge)
        assertEquals(0xff0000ff.toInt(), edge[0])
        assertEquals(0xff00ff00.toInt(), edge[1])
    }

    @Test fun repeatedReadResetsTheViewPosition() {
        val staging = RgbaReadback(1)
        staging.pixels.put(byteArrayOf(18, 52, 86, 0))
        assertEquals(0xff123456.toInt(), staging.decode(1)[0])
        assertEquals(0xff123456.toInt(), staging.decode(1)[0])
    }

    @Test fun rejectsCountsOutsideCapacity() {
        val staging = RgbaReadback(2)
        for (count in listOf(-1, 3)) {
            assertThrows(IllegalArgumentException::class.java) { staging.decode(count) }
        }
        assertSame(staging.colors, staging.decode(0))
    }
}
