package tw.luma.camera.editor

/** What the plain conversion picker lists: containers Media3 reads but other players often cannot open. MP4 is left out on purpose. */
internal object ConvertibleVideos {
    val MIME_TYPES = arrayOf(
        "video/quicktime", "video/x-matroska", "video/webm",
        "video/avi", "video/x-msvideo", // the same .avi under the two names Android uses
        "video/3gpp", "video/3gpp2", "video/x-flv",
        "video/mp2ts", "video/mp2t", "video/mpeg",
    )
}
