package com.nanuq.native_video_encoder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class MuxerMerger (
    private val videoPath: String,
    private val audioPath: String,
    private val outputPath: String,
){
    fun merge(){
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()

        videoExtractor.setDataSource(videoPath)
        audioExtractor.setDataSource(audioPath)

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val trackIndexMap = mutableMapOf<Int, Int>()

        // Add video track
        for (i in 0 until videoExtractor.trackCount){
            val format = videoExtractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true){
                videoExtractor.selectTrack(i)
                val muxerTrackIndex = muxer.addTrack(format)
                trackIndexMap[i] = muxerTrackIndex
                break
            }
        }

        // Add audio track
        for (i in 0 until audioExtractor.trackCount){
            val format = audioExtractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioExtractor.selectTrack(i)
                val muxerTrackIndex = muxer.addTrack(format)
                trackIndexMap[i + 1000] = muxerTrackIndex // offset to distinguish audio
            }
        }

        muxer.start()

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        // Copy video samples
        for ((extractor, offset) in listOf(videoExtractor to 0, audioExtractor to 1000)){
            while (true){
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }

                bufferInfo.presentationTimeUs = extractor.sampleTime

                val isVideo = extractor == videoExtractor
                bufferInfo.flags = if (isVideo && extractor.sampleFlags == MediaExtractor.SAMPLE_FLAG_SYNC) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }

                val trackIndex = extractor.sampleTrackIndex + offset
                muxer.writeSampleData(trackIndexMap[trackIndex]!!, buffer, bufferInfo)
                extractor.advance()
            }
        }

        videoExtractor.release()
        audioExtractor.release()
        muxer.stop()
        muxer.release()
    }
}