package com.nanuq.native_video_encoder;

import java.util.List;

public class UnityBridge {
    public static void encodeRgbaVideo(String cachePath, String savePath, int width, int height, int frameRate, List<byte[]> rgbaList) {
        VideoEncoderBridge.INSTANCE.encodeRgbaVideo(cachePath, savePath, width, height, frameRate, rgbaList);
    }

    public static void encodeImageVideo(String cachePath, String savePath, int width, int height, int frameRate){
        VideoEncoderBridge.INSTANCE.encodeRgbaImageVideo(cachePath, savePath, width, height, frameRate);
    }
}