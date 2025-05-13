package com.nanuq.native_video_encoder;

import java.util.List;

public class UnityBridge {
    public static void encodeRgbaVideo(String path, int width, int height, int frameRate, List<byte[]> rgbaList) {
        VideoEncoderBridge.INSTANCE.encodeRgbaVideo(path, width, height, frameRate, rgbaList);
    }
}