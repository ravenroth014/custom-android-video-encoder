package com.nanuq.native_video_encoder;

public class UnityBridge {
    public static void encodeImageVideo(
            String imageFolderPath,
            byte[] audioPcmData,
            String savePath,
            int width,
            int height,
            int frameRate,
            int sampleRate,
            int channels,
            boolean isDebug
    ){
        VideoEncoderBridge.INSTANCE.encodeRgbaImageVideo(imageFolderPath, audioPcmData, savePath, width, height, frameRate, sampleRate, channels, isDebug);
    }

    public static void encodeImageVideo(
            String imageFolderPath,
            byte[] listenerAudioPcmData,
            byte[] clientVoiceAudioPcmData,
            String savePath,
            int width,
            int height,
            int frameRate,
            int sampleRate,
            int channels,
            boolean isDebug
    ){
        VideoEncoderBridge.INSTANCE.encodeRgbaImageVideo(imageFolderPath, listenerAudioPcmData, clientVoiceAudioPcmData, savePath, width, height, frameRate, sampleRate, channels, isDebug);
    }
}