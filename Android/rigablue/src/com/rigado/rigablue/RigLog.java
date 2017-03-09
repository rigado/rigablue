package com.rigado.rigablue;

import android.util.Log;

/**
 *  RigLog.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This class provides logging functionality for Rigablue.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigLog {

    private static String TAG = "rigablue";

    private static int mLogLevel = 0;

    public static void setLogLevel(int level) {
        mLogLevel = level;
    }

    public static void e(Throwable throwable) {
        Log.e(TAG, "", throwable);
    }

    public static void e(String message) {
        Log.e(TAG, message);
    }

    public static void w(String message) {
        if (mLogLevel < 4) {
            Log.w(TAG, message);
        }
    }

    public static void i(String message) {
        if (mLogLevel < 3) {
            Log.i(TAG, message);
        }
    }

    public static void d(String message) {
        if (mLogLevel < 2) {
            Log.d(TAG, message);
        }
    }

    public static void v(String message) {
        if (mLogLevel == 0) {
            Log.v(TAG, message);
        }
    }
}
