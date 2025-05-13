package com.nanuq.simple_math;

import android.util.Log;

public class SimpleMathematics {

    private static final String TAG = "SimpleMathematics";

    public SimpleMathematics(){
        Log.i(TAG, "SimpleMathematics object created");
    }

    public int add(int a, int b){
        int result = a + b;
        Log.i(TAG, "Adding values " + a + " and " + b + " = " + result);
        return result;
    }

    public int subtract(int a, int b){
        int result = a - b;
        Log.i(TAG, "Subtracting values " + a + " and " + b + " = " + result);
        return result;
    }
}
