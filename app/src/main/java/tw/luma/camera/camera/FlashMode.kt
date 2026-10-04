package tw.luma.camera.camera

/** What the flash icon asks for. Tapping cycles off, auto, on. */
enum class FlashMode {
    OFF, AUTO, ON;

    fun next(): FlashMode = entries[(ordinal + 1) % entries.size]
}
