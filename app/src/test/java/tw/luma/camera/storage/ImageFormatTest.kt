package tw.luma.camera.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFormatTest {
    @Test fun extensionFollowsTheRealImageType() {
        assertEquals("jpg", ImageFormat.extension("image/jpeg"))
        assertEquals("png", ImageFormat.extension("image/png"))
        assertEquals("webp", ImageFormat.extension("image/webp"))
        assertEquals("gif", ImageFormat.extension("image/gif"))
        assertEquals("heic", ImageFormat.extension("image/heif"))
        assertEquals("heic", ImageFormat.extension("image/heic"))
    }

    @Test fun unknownOrMissingTypesFallBackToJpeg() {
        assertEquals("jpg", ImageFormat.extension(null))
        assertEquals("jpg", ImageFormat.extension("application/octet-stream"))
        assertEquals("image/jpeg", ImageFormat.mimeType(null))
        assertEquals("image/jpeg", ImageFormat.mimeType("text/plain"))
    }

    @Test fun anImageTypeIsStoredWithItsOwnMimeType() {
        assertEquals("image/png", ImageFormat.mimeType("image/png"))
        assertEquals("image/heif", ImageFormat.mimeType("image/heif"))
    }
}
