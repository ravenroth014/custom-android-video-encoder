package com.nanuq.native_video_encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.List;

public class NativeVideoEncoder {
    private static final String TAG = "NativeVideoEncoder";

    public static boolean encode(
            String outputPath,
            List<byte[]> videoFramesRGBA,
            byte[] audioPCM,
            int width,
            int height,
            int frameRate,
            int audioSampleRate,
            boolean isStereo){

        final int bitRate = 4000_000;
        final int channelCount = isStereo ? 2 : 1;

        MediaCodec videoEncoder = null;
        MediaCodec audioEncoder = null;
        MediaMuxer mediaMuxer = null;

        int videoTrackIndex = -1;
        int audioTrackIndex = -1;
        boolean muxerStarted = false;
        boolean videoTrackAdded = false;
        boolean audioTrackAdded = false;

        Log.i(TAG, "videoFramesRGBA list size : " + videoFramesRGBA.size());
        Log.i(TAG, "audioPCM size : " + audioPCM.length);

        try {
            // VIDEO ENCODER
            MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoEncoder.start();

            // AUDIO ENCODER
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, channelCount);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000);

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            // MUXER
            mediaMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // VIDEO FEED
            long videoPresentationTimeUs = 0;
            long frameDurationUs = 1_000_000L / frameRate;

//            for (int i = 0; i < videoFramesRGBA.size(); i++) {
//                byte[] rgba = videoFramesRGBA.get(i);
//                byte[] yuv = convertRGBAtoYUV420(rgba, width, height);
//
//                int inputIndex = videoEncoder.dequeueInputBuffer(10_000);
//                if (inputIndex >= 0) {
//                    ByteBuffer inputBuffer = videoEncoder.getInputBuffer(inputIndex);
//                    if (inputBuffer != null){
//                        inputBuffer.clear();
//                        inputBuffer.put(yuv);
//                        videoEncoder.queueInputBuffer(inputIndex, 0, yuv.length, videoPresentationTimeUs, 0);
//                        videoPresentationTimeUs += frameDurationUs;
//                    }
//                }
//
//                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//                while (true) {
//                    int outputIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 0);
//                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        break;
//                    }
//                    if (outputIndex >= 0) {
//                        ByteBuffer encodedData = videoEncoder.getOutputBuffer(outputIndex);
//                        if (!videoTrackAdded) {
//                            MediaFormat outFormat = videoEncoder.getOutputFormat();
//                            videoTrackIndex = mediaMuxer.addTrack(outFormat);
//                            videoTrackAdded = true;
//                        }
//                        else if (videoTrackAdded && audioTrackAdded && !muxerStarted) {
//                            mediaMuxer.start();
//                            muxerStarted = true;
//                        }
//                        else if (muxerStarted && bufferInfo.size > 0) {
//                            encodedData.position(bufferInfo.offset);
//                            encodedData.limit(bufferInfo.offset + bufferInfo.size);
//                            mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
//                        }
//                        videoEncoder.releaseOutputBuffer(outputIndex, false);
//                    }
//                }
//            }

            // Signal video EOS
            int eosInputIndex = videoEncoder.dequeueInputBuffer(10000);
            if (eosInputIndex >= 0) {
                ByteBuffer inputBuffer = videoEncoder.getInputBuffer(eosInputIndex);
                inputBuffer.clear();
                videoEncoder.queueInputBuffer(eosInputIndex, 0, 0, videoPresentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            Log.d(TAG, "Video Feed Render End");

            // AUDIO FEED
            int pcmOffset = 0;
            int maxInputSize = 2048; // adjust depending on buffer
            long audioPresentationTimeUs = 0;
            int bytesPerSample = 2 * channelCount;

            while (pcmOffset < audioPCM.length) {
                int aInputIndex = audioEncoder.dequeueInputBuffer(10_000);
                if (aInputIndex >= 0) {
                    ByteBuffer inputBuffer = audioEncoder.getInputBuffer(aInputIndex);
                    inputBuffer.clear();

                    int chunkSize = Math.min(audioPCM.length - pcmOffset, maxInputSize);
                    inputBuffer.put(audioPCM, pcmOffset, chunkSize);
                    pcmOffset += chunkSize;

                    audioEncoder.queueInputBuffer(aInputIndex, 0, chunkSize, audioPresentationTimeUs, 0);
                    audioPresentationTimeUs += (chunkSize / bytesPerSample) * 1_000_000L / audioSampleRate;
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (true) {
                    int outputIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
                    if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                    if (outputIndex >= 0) {
                        ByteBuffer encodedData = audioEncoder.getOutputBuffer(outputIndex);
                        if (!audioTrackAdded) {
                            MediaFormat outFormat = audioEncoder.getOutputFormat();
                            audioTrackIndex = mediaMuxer.addTrack(outFormat);
                            audioTrackAdded = true;
                        }
                        else if (videoTrackAdded && audioTrackAdded && !muxerStarted) {
                            mediaMuxer.start();
                            muxerStarted = true;
                        }
                        if (muxerStarted && bufferInfo.size > 0) {
                            encodedData.position(bufferInfo.offset);
                            encodedData.limit(bufferInfo.offset + bufferInfo.size);
                            mediaMuxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo);
                        }
                        audioEncoder.releaseOutputBuffer(outputIndex, false);
                    }
                }
            }

            // Signal audio EOS
            int eosAudioIndex = audioEncoder.dequeueInputBuffer(10000);
            if (eosAudioIndex >= 0) {
                ByteBuffer inputBuffer = audioEncoder.getInputBuffer(eosAudioIndex);
                inputBuffer.clear();
                audioEncoder.queueInputBuffer(eosAudioIndex, 0, 0, audioPresentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            Log.d(TAG, "Audio Feed Render End");

            // Flush remaining buffers
            drainEncoder(videoEncoder, mediaMuxer, videoTrackIndex, muxerStarted);
            drainEncoder(audioEncoder, mediaMuxer, audioTrackIndex, muxerStarted);

        } catch (Exception e) {
            Log.e(TAG, "Encoding failed", e);
        }
        finally {
            try {
                if (videoEncoder != null) {
                    videoEncoder.stop();
                }
            } catch (Exception ignored) {
                Log.e(TAG, "Error stopping video encoder", ignored);
            }

            try {
                if (audioEncoder != null) {
                    audioEncoder.stop();
                }
            } catch (Exception ignored) {
                Log.e(TAG, "Error stopping audio encoder", ignored);
            }

            try {
                if (mediaMuxer != null && muxerStarted) {
                    mediaMuxer.stop();
                }
            } catch (Exception ignored) {
                Log.e(TAG, "Error stopping media muxer", ignored);
            }

            if (videoEncoder != null) {
                videoEncoder.release();
            }
            if (audioEncoder != null) {
                audioEncoder.release();
            }
            if (mediaMuxer != null) {
                mediaMuxer.release();
            }
        }

        return true;
    }

    private static void drainEncoder(
            MediaCodec encoder,
            MediaMuxer muxer,
            int trackIndex,
            boolean muxerStarted) {
        if (!muxerStarted || trackIndex == -1) {
            return;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (true) {
            int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }
            if (outputIndex >= 0) {
                ByteBuffer encodedData = encoder.getOutputBuffer(outputIndex);
                if (bufferInfo.size > 0) {
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                }
                encoder.releaseOutputBuffer(outputIndex, false);
            }
        }
    }

    private static byte[] convertRGBAtoYUV420(byte[] rgba, int width, int height) {
        int frameSize = width * height;
        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + frameSize / 4;

        byte[] yuv420 = new byte[frameSize + frameSize / 2];

        int r, g, b, y, u, v;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int rgbaIndex = (j * width + i) * 4;

                r = rgba[rgbaIndex] & 0xFF;
                g = rgba[rgbaIndex + 1] & 0xFF;
                b = rgba[rgbaIndex + 2] & 0xFF;

                // RGB to YUV conversion (BT.601)
                y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                u = (int) (-0.169 * r - 0.331 * g + 0.5 * b + 128);
                v = (int) (0.5 * r - 0.419 * g - 0.081 * b + 128);

                yuv420[yIndex++] = (byte) (y < 0 ? 0 : Math.min(255, y));

                // Sub-sample U and V (4:2:0)
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    yuv420[uIndex++] = (byte) (u < 0 ? 0 : Math.min(255, u));
                    yuv420[vIndex++] = (byte) (v < 0 ? 0 : Math.min(255, v));
                }
            }
        }

        return yuv420;
    }
}
