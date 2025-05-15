package com.nanuq.native_video_encoder;

import java.util.List;

public class UnityBridge {
    public static void encodeRgbaVideo(
            String imageFolderPath,
            String audioFilePath,
            String savePath,
            int width,
            int height,
            int frameRate,
            List<byte[]> rgbaList) {
        VideoEncoderBridge.INSTANCE.encodeRgbaVideo(imageFolderPath, audioFilePath, savePath, width, height, frameRate, rgbaList);
    }

    public static void encodeImageVideo(
            String imageFolderPath,
            String audioFilePath,
            String savePath,
            int width,
            int height,
            int frameRate){
        VideoEncoderBridge.INSTANCE.encodeRgbaImageVideo(imageFolderPath, audioFilePath, savePath, width, height, frameRate);
    }

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
}