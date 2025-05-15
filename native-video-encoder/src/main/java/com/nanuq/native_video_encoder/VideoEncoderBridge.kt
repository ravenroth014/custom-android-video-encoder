package com.nanuq.native_video_encoder

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoEncoderBridge {
    private var encoder: VideoEncoder? = null

    @JvmStatic
    fun encodeRgbaVideo(
        imageFolderPath: String,
        audioFilePath: String,
        savePath: String,
        width: Int,
        height: Int,
        frameRate: Int,
        rgbaList: List<ByteArray>
    ) {
//        encoder = VideoEncoder(imageFolderPath, audioFilePath, savePath, width, height, frameRate)
//        encoder?.encode(rgbaList)
    }

    @JvmStatic
    fun encodeRgbaImageVideo(
        imageFolderPath: String,
        audioFilePath: String,
        savePath: String,
        width: Int,
        height: Int,
        frameRate: Int
    ){
//        encoder = VideoEncoder(imageFolderPath, audioFilePath, savePath, width, height, frameRate)
//        encoder?.encodeFromPngFrames()
    }

    @JvmStatic
    fun encodeRgbaImageVideo(
        imageFolderPath: String,
        audioPcmData: ByteArray,
        savePath: String?,
        width: Int,
        height: Int,
        frameRate: Int,
        sampleRate: Int,
        channels: Int,
        isDebug: Boolean
    ){
        try {
            val pcmFile = File.createTempFile("audio_pcm_",".pcm")
            pcmFile.writeBytes(audioPcmData)

            val aacFile = File.createTempFile("audio_aac_",".aac")
            PcmToAacEncoder.fromByteArray(
                pcmData = audioPcmData,
                sampleRate = sampleRate,
                channelCount = channels,
                outputAacPath = aacFile.absolutePath
            ).encode()

            val videoOnlyFile = File.createTempFile("video_only_", ".mp4")
            VideoEncoder(
                imageFolderPath = imageFolderPath,
                outputVideoPath = videoOnlyFile.absolutePath,
                width = width,
                height = height,
                frameRate = frameRate
            ).encodeByPngFrames()

            MuxerMerger(
                videoPath = videoOnlyFile.absolutePath,
                audioPath = aacFile.absolutePath,
                outputPath = savePath ?: File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES),
                    "Video_${System.currentTimeMillis()}.mp4").absolutePath
            ).merge()

            pcmFile.delete()
            videoOnlyFile.delete()

            Log.i("VideoEncoderBridge", "Encoding successful")

            if (isDebug){
                copyDebugFrames(imageFolderPath)
            }
        }
        catch (e: Exception){
            Log.e("VideoEncoderBridge", "Encoding failed", e)
        }
    }

    private fun copyDebugFrames(imageFolderPath: String) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val debugDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "DebugFrames/Encode_$timeStamp"
        )

        if (!debugDir.exists()) {
            debugDir.mkdirs()
        }

        val sourceDir = File(imageFolderPath)
        sourceDir.listFiles { _, name -> name.endsWith(".png") }?.forEach { file ->
            val destFile = File(debugDir, file.name)
            file.copyTo(destFile, overwrite = true)
        }
    }
}