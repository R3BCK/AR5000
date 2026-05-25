package com.ar5000.common;
import android.util.Log;

public class Logger {
    private static final String TAG = "AR5000";
    public static void d(String msg) { Log.d(TAG, msg); }
    public static void i(String msg) { Log.i(TAG, msg); }
    public static void w(String msg) { Log.w(TAG, msg); }
    public static void e(String msg, Throwable t) { Log.e(TAG, msg, t); }
}