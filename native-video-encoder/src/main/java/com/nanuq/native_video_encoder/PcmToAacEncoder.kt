package com.nanuq.native_video_encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.ByteArrayInputStream
import java.io.InputStream

class PcmToAacEncoder (
    private val pcmInputStream: InputStream,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val outputAacPath: String,
){
    fun encode(){
        val mime = "audio/mp4a-latm"
        val bitRate = 128_000

        val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputAacPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val bufferSize = 2048
        val inputBuffer = ByteArray(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        var muxerStarted = false
        var trackIndex = -1
        var presentationTimeUs = 0L

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputIndex = codec.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inputIndex)!!
                    inputBuf.clear()
                    val bytesRead = pcmInputStream.read(inputBuffer, 0, bufferSize)
                    if (bytesRead == -1) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        sawInputEOS = true
                    } else {
                        inputBuf.put(inputBuffer, 0, bytesRead)
                        inputBuf.limit(bytesRead)
                        codec.queueInputBuffer(
                            inputIndex, 0, bytesRead,
                            presentationTimeUs, 0
                        )
                        val frameSamples = bytesRead / (2 * channelCount)
                        presentationTimeUs += frameSamples * 1_000_000L / sampleRate
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IllegalStateException("Format changed twice")
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }
        }

        pcmInputStream.close()
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }

    companion object {
        fun fromByteArray(
            pcmData: ByteArray,
            sampleRate: Int,
            channelCount: Int,
            outputAacPath: String
        ): PcmToAacEncoder {
            return PcmToAacEncoder(
                pcmInputStream = ByteArrayInputStream(pcmData),
                sampleRate = sampleRate,
                channelCount = channelCount,
                outputAacPath = outputAacPath
            )
        }
    }
}