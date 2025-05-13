package com.nanuq.native_video_encoder

object VideoEncoderBridge {
    private var encoder: VideoEncoder? = null

    @JvmStatic
    fun encodeRgbaVideo(
        cachePath: String,
        savePath: String,
        width: Int,
        height: Int,
        frameRate: Int,
        rgbaList: List<ByteArray>
    ) {
        encoder = VideoEncoder(cachePath, savePath, width, height, frameRate)
        encoder?.encode(rgbaList)
    }

    @JvmStatic
    fun encodeRgbaImageVideo(
        cachePath: String,
        savePath: String,
        width: Int,
        height: Int,
        frameRate: Int
    ){
        encoder = VideoEncoder(cachePath, savePath, width, height, frameRate)
        encoder?.encodeFromPngFrames()
    }
}