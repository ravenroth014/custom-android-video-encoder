package com.nanuq.native_video_encoder

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoEncoderBridge {

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

    @JvmStatic
    fun encodeRgbaImageVideo(
        imageFolderPath: String,
        listenerAudioPcmData: ByteArray,
        clientVoiceAudioPcmData: ByteArray?,
        saveFolder: String?,
        width: Int,
        height: Int,
        frameRate: Int,
        sampleRate: Int,
        channels: Int,
        isDebug: Boolean
    ){
        try {
            val mixedAudioPcmData = if (clientVoiceAudioPcmData == null || clientVoiceAudioPcmData.isEmpty())
                listenerAudioPcmData
            else
                AudioMixer.mixPcmBytesLE(listenerAudioPcmData, clientVoiceAudioPcmData)

            val pcmFile = File.createTempFile("audio_pcm_",".pcm")
            pcmFile.writeBytes(mixedAudioPcmData)

            val aacFile = File.createTempFile("audio_aac_",".aac")
            PcmToAacEncoder.fromByteArray(
                pcmData = mixedAudioPcmData,
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

            val outputPath = if (saveFolder.isNullOrEmpty()){
                val movieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val defaultDir = File(movieDir, "VideoEncoder")
                if (!defaultDir.exists()){
                    defaultDir.mkdirs()
                }
                File(defaultDir, "Video_${System.currentTimeMillis()}.mp4").absolutePath
            } else {
                val movieDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val customDir = File(movieDir, saveFolder)
                if (!customDir.exists()){
                    customDir.mkdirs()
                }
                File(customDir, "Video_${System.currentTimeMillis()}.mp4").absolutePath
            }

            MuxerMerger(
                videoPath = videoOnlyFile.absolutePath,
                audioPath = aacFile.absolutePath,
                outputPath = outputPath
//                outputPath = saveFolder ?: File(Environment.getExternalStoragePublicDirectory(
//                    Environment.DIRECTORY_MOVIES),
//                    "Video_${System.currentTimeMillis()}.mp4").absolutePath
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