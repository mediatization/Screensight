package com.example.screenlife2;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.concurrent.Executor;

/*
 * myaccessibilityservice - implements screenshot request and forwards bitmaps to CaptureService
 * CHANGES MARKED with // new: or // changed:
 */

public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "MyAccessibilityService";

    // new: singleton accessor so other classes (scheduler/mediaProjectionCallback) can query service
    private static MyAccessibilityService instance;

    // new: getter for singleton
    public static MyAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "service connected");

        // new: set singleton
        instance = this;

        // new: register with CaptureService to forward the service reference
        CaptureService.setAccessibilityService(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // not needed for screenshots, but required override
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // new: clear singleton
        if (instance == this) instance = null;
        // new: unregister from CaptureService (optional)
        CaptureService.setAccessibilityService(null);
        Log.d(TAG, "service destroyed");
    }

    /**
     * new: requestScreenshot() - use accessibility takeScreenshot api (android 11+ / 30+)
     * - takes screenshot, converts to bitmap, forwards to CaptureService.handleAccessibilityScreenshot(...)
     * - guarded by sdk check
     */
    @RequiresApi(api = Build.VERSION_CODES.R) // keeps compile-time clarity; runtime guard exists too
    public void requestScreenshot() {
        // new: runtime guard
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "requestScreenshot: sdk < R - not supported");
            return;
        }

        try {
            final int displayId = Display.DEFAULT_DISPLAY;
            final Executor executor = getMainExecutor(); // use main executor for callback

            // new: call framework api
            takeScreenshot(displayId, executor, new AccessibilityService.TakeScreenshotCallback() {
                @Override
                public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                    try {
                        HardwareBuffer hb = screenshot.getHardwareBuffer();
                        ColorSpace cs = screenshot.getColorSpace();

                        if (hb == null) {
                            Log.w(TAG, "onSuccess: hardwareBuffer is null");
                            return;
                        }

                        // new: wrap hardware buffer into bitmap
                        Bitmap wrapped = Bitmap.wrapHardwareBuffer(hb, cs);
                        if (wrapped == null) {
                            Log.w(TAG, "onSuccess: wrapped bitmap is null");
                            try { hb.close(); } catch (Exception ex) { /* ignore */ }
                            return;
                        }

                        // new: copy to accessible config (immutable wrapped may be restricted)
                        Bitmap copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);

                        // close hardware buffer as we no longer need it
                        try { hb.close(); } catch (Exception ex) { /* ignore */ }

                        // forward to CaptureService pipeline
                        CaptureService svc = CaptureService.getInstance();
                        if (svc != null) {
                            svc.handleAccessibilityScreenshot(copy);
                            Log.d(TAG, "onSuccess: forwarded screenshot to CaptureService");
                        } else {
                            // no service available - log and recycle bitmap
                            Log.w(TAG, "onSuccess: capture service is null; dropping screenshot");
                            copy.recycle();
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "onSuccess: error processing screenshot", t);
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.w(TAG, "takeScreenshot failed, errorCode=" + errorCode);
                    // failure handling: nothing else to do here; captureScheduler will fallback when invoked next tick
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "requestScreenshot: thrown", t);
        }
    }
}
