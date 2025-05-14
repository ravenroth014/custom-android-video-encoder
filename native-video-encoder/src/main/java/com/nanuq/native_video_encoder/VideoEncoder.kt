package com.nanuq.native_video_encoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
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

    fun encodeByByteArrayData(videoFrames: List<ByteArray>) {
        prepareEncoder()

        val frameDurationUs = 1_000_000L / frameRate
        val bufferInfo = MediaCodec.BufferInfo()

        for ((index, rgba) in videoFrames.withIndex()) {
            val nv21 = rgbaToNV21(rgba, width, height)

            val inputIndex = mediaCodec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(nv21)
                mediaCodec.queueInputBuffer(inputIndex, 0, nv21.size, index * frameDurationUs, 0)
            }

            drainEncoder(bufferInfo)
        }

        // Send EOS
        val eosInputIndex = mediaCodec.dequeueInputBuffer(10000)
        if (eosInputIndex >= 0) {
            mediaCodec.queueInputBuffer(eosInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainEncoder(bufferInfo)

        mediaCodec.stop()
        mediaCodec.release()
        mediaMuxer.stop()
        mediaMuxer.release()
    }

    fun encodeByPngFrames(){
        preparePngEncoder()

        val pngFiles = File(imageFolderPath).listFiles { f -> f.name.endsWith(".png") }?.sortedBy { it.name }
            ?: throw IllegalStateException("No PNG files found in folder")

        val frameDurationUs = 1_000_000L / frameRate
        val bufferInfo = MediaCodec.BufferInfo()

        for ((index, file) in pngFiles.withIndex()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalArgumentException("Failed to decode image: ${file.name}")

            val yuv = convertBitmapToI420(bitmap)
            val inputIndex = mediaCodec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(yuv)
                mediaCodec.queueInputBuffer(inputIndex, 0, yuv.size, index * frameDurationUs, 0)
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

    private fun prepareEncoder() {
        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        mediaCodec = MediaCodec.createEncoderByType(mimeType)
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()

//        val fileName = "Video_${System.currentTimeMillis()}.mp4"
//        val publicPath = File(
//            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
//            fileName
//        )

        mediaMuxer = MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun preparePngEncoder(){
        val fileName = "Video_${System.currentTimeMillis()}.mp4"
        val outputPath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            fileName
        )

        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUV420Flexible)
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

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }

        return yuv
    }
}