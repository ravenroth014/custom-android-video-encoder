package com.nanuq.native_video_encoder

object VideoEncoderBridge {
    private var encoder: VideoEncoder? = null

    @JvmStatic
    fun encodeRgbaVideo(
        path: String,
        width: Int,
        height: Int,
        frameRate: Int,
        rgbaList: List<ByteArray>
    ) {
        encoder = VideoEncoder(path, width, height, frameRate)
        encoder?.encode(rgbaList)
    }
}