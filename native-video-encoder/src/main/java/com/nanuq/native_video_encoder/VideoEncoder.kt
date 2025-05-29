package com.nanuq.native_video_encoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
import android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
import android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import java.io.File


class VideoEncoder (
    private val imageFolderPath: String,
    private val outputVideoPath: String,
    private val width: Int,
    private val height: Int,
    private val frameRate: Int) {

    private val mimeType = "video/avc"
    private val bitRate = width * height * 4 * 8

    private lateinit var mediaCodec: MediaCodec
    private lateinit var mediaMuxer: MediaMuxer
    private var videoTrackIndex = -1
    private var muxerStarted = false

    private var targetColorFormat: Int = -1

    init {
        targetColorFormat = getSupportedColorFormat(mimeType)
        if (targetColorFormat == -1){
            throw IllegalStateException("No suitable color format found for video encoding.")
        }
    }

    fun encodeByPngFrames(){
        preparePngEncoder(targetColorFormat)

        val pngFiles = File(imageFolderPath).listFiles { f -> f.name.endsWith(".png") }?.sortedBy { it.name }
            ?: throw IllegalStateException("No PNG files found in folder")

        val frameDurationUs = 1_000_000L / frameRate
        val bufferInfo = MediaCodec.BufferInfo()

        for ((index, file) in pngFiles.withIndex()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalArgumentException("Failed to decode image: ${file.name}")

            //val rgbBitmap = convertToRGB(bitmap)
            //val yuv = convertBitmapToI420(rgbBitmap)
            //val yuv = convertBitmapToI420(bitmap)

            val yuvData: ByteArray = when (targetColorFormat) {
                COLOR_FormatYUV420SemiPlanar -> convertBitmapToNV12(bitmap)
                COLOR_FormatYUV420Planar -> convertBitmapToI420(bitmap)
                else -> throw UnsupportedOperationException("Unsupported or unknown color format: $targetColorFormat")
            }

            val inputIndex = mediaCodec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(yuvData)
                mediaCodec.queueInputBuffer(inputIndex, 0, yuvData.size, index * frameDurationUs, 0)
            }

            drainEncoder(bufferInfo)
        }

        val eosIndex = mediaCodec.dequeueInputBuffer(10000)
        if (eosIndex >= 0) {
            mediaCodec.queueInputBuffer(eosIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainEncoder(bufferInfo)

        mediaCodec.stop()
        mediaCodec.release()
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    private fun preparePngEncoder(colorFormat: Int){
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        mediaCodec = MediaCodec.createEncoderByType(mimeType)
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()

        mediaMuxer = MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun drainEncoder(bufferInfo: MediaCodec.BufferInfo) {
        while (true) {
            val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IllegalStateException("Format changed twice")
                    videoTrackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
                    mediaMuxer.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val encodedData = mediaCodec.getOutputBuffer(outputIndex)
                        ?: throw RuntimeException("Encoded buffer null")
                    if (bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    mediaCodec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun rgbaToNV21(rgba: ByteArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val nv21 = ByteArray(frameSize + frameSize / 2)

        var yIndex = 0
        var uvIndex = frameSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val index = (j * width + i) * 4
                val r = rgba[index].toInt() and 0xFF
                val g = rgba[index + 1].toInt() and 0xFF
                val b = rgba[index + 2].toInt() and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                nv21[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < nv21.size) {
                    nv21[uvIndex++] = v.coerceIn(0, 255).toByte()
                    nv21[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }

        return nv21
    }

//    private fun convertBitmapToI420(bitmap: Bitmap): ByteArray {
//        val argb = IntArray(width * height)
//        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
//
//        val yuv = ByteArray(width * height * 3 / 2)
//        var yIndex = 0
//        var uIndex = width * height
//        var vIndex = uIndex + (width * height / 4)
//
//        for (j in 0 until height) {
//            for (i in 0 until width) {
//                val color = argb[j * width + i]
//                val r = (color shr 16) and 0xFF
//                val g = (color shr 8) and 0xFF
//                val b = color and 0xFF
//
//                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
//                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
//                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
//
//                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
//
//                if (j % 2 == 0 && i % 2 == 0) {
//                    yuv[uIndex++] = u.coerceIn(0, 255).toByte()
//                    yuv[vIndex++] = v.coerceIn(0, 255).toByte()
//                }
//            }
//        }
//
//        return yuv
//    }

//    private fun convertToRGB(bitmap: Bitmap): Bitmap {
//        val rgbBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(rgbBitmap)
//        val paint = Paint().apply { isFilterBitmap = true }
//        canvas.drawColor(Color.BLACK)
//        canvas.drawBitmap(bitmap, 0f, 0f, paint)
//        return rgbBitmap
//    }

    private fun getSupportedColorFormat(mimeType: String): Int {
        val mediaCodecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
        for (codecInfo in mediaCodecList.codecInfos) {
            if (!codecInfo.isEncoder || !codecInfo.supportedTypes.contains(mimeType)) {
                continue
            }
            val capabilities = codecInfo.getCapabilitiesForType(mimeType)

            // 1. Prioritize Semi-Planar (NV12) first, it's very common for hardware encoders
            if (capabilities.colorFormats.contains(COLOR_FormatYUV420SemiPlanar)) {
                println("Found supported color format: COLOR_FormatYUV420SemiPlanar (NV12)")
                return COLOR_FormatYUV420SemiPlanar
            }

            // 2. Then check for Planar (I420)
            if (capabilities.colorFormats.contains(COLOR_FormatYUV420Planar)) {
                println("Found supported color format: COLOR_FormatYUV420Planar (I420)")
                return COLOR_FormatYUV420Planar
            }

            // 3. As a last resort for flexible formats, check if COLOR_FormatYUV420Flexible is supported.
            // If it is, we need to know what specific sub-format it implies.
            // Many encoders, when specifying Flexible, might still prefer NV12 or I420 internally.
            // You might want to inspect capabilities.encoderCapabilities.preferredColorFormats
            // for more specific guidance if Flexible is the only option.
            if (capabilities.colorFormats.contains(COLOR_FormatYUV420Flexible)) {
                println("Found supported color format: COLOR_FormatYUV420Flexible. Will try to infer specific sub-format or default to NV12.")
                // If Flexible is the only option, assume NV12 as a common flexible format.
                // You could further inspect capabilities.encoderCapabilities.preferredColorFormats here
                // if this default doesn't work consistently.
                return COLOR_FormatYUV420SemiPlanar // Defaulting to NV12 as a common flexible interpretation
            }
        }
        println("No suitable specific YUV420 color format found for $mimeType. Encoding might fail.")
        return -1 // Indicate failure to find a suitable format
    }

    private fun convertToNV12FromRGBA(rgba: ByteArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val nv12 = ByteArray(frameSize + frameSize / 2)

        var yIndex = 0
        var uvIndex = frameSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val index = (j * width + i) * 4
                val r = rgba[index].toInt() and 0xFF
                val g = rgba[index + 1].toInt() and 0xFF
                val b = rgba[index + 2].toInt() and 0xFF

                // Y (Crab)
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                nv12[yIndex++] = y.coerceIn(0, 255).toByte()

                // UV (only for even rows and columns)
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                    nv12[uvIndex++] = u.coerceIn(0, 255).toByte() // U byte
                    nv12[uvIndex++] = v.coerceIn(0, 255).toByte() // V byte
                }
            }
        }
        return nv12
    }

    private fun convertBitmapToNV12(bitmap: Bitmap): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val nv12 = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[j * width + i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                // Y (Crab)
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                nv12[yIndex++] = y.coerceIn(0, 255).toByte()

                // UV (only for even rows and columns)
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                    nv12[uvIndex++] = u.coerceIn(0, 255).toByte() // U byte
                    nv12[uvIndex++] = v.coerceIn(0, 255).toByte() // V byte
                }
            }
        }
        return nv12
    }

    private fun convertBitmapToI420(bitmap: Bitmap): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uIndex = width * height
        var vIndex = uIndex + (width * height / 4)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[j * width + i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                // Y (Crab)
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                // U and V (only for even rows and columns)
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                    yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}