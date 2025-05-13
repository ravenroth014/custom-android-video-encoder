package com.nanuq.simple_math;

import android.util.Log;

public class SimpleMathematicsBridge {
    private static final String TAG = "SimpleMathematicsBridge";
    private static SimpleMathematics bridge;

    public static int add(int a, int b) {
        if (bridge == null) {
            bridge = new SimpleMathematics();
        }

        int result = bridge.add(a, b);
        Log.i(TAG, "SimpleMathematicsBridge, Add value a : " + a + " value b : " + b + " result : " + result);
        return result;
    }

    public static int subtract(int a, int b){
        if (bridge == null) {
            bridge = new SimpleMathematics();
        }

        int result = bridge.subtract(a, b);
        Log.i(TAG, "SimpleMathematicsBridge, Subtract value a : " + a + " value b : " + b + " result : " + result);
        return result;
    }
}
