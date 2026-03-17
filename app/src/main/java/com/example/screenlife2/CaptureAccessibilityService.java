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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * CaptureAccessibilityService - implements screenshot request and notifies registered listeners.
 */
public class CaptureAccessibilityService extends AccessibilityService {
    private static final String TAG = "CaptureAccessibilityService";

    /**
     * Interface for components interested in screenshots captured by this service.
     */
    public interface ScreenshotListener {
        void onScreenshotCaptured(Bitmap bitmap);
    }

    private static final List<ScreenshotListener> listeners = new ArrayList<>();

    public static void registerListener(ScreenshotListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
                Log.d(TAG, "Listener registered");
            }
        }
    }

    public static void unregisterListener(ScreenshotListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            Log.d(TAG, "Listener unregistered");
        }
    }

    private static CaptureAccessibilityService instance;

    public static CaptureAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected");
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not needed for screenshots
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        Log.d(TAG, "Accessibility service destroyed");
    }

    /**
     * Triggers a screenshot capture using the Accessibility API (Android 11+).
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public void requestScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "requestScreenshot: SDK < R - not supported");
            return;
        }

        try {
            final int displayId = Display.DEFAULT_DISPLAY;
            final Executor executor = getMainExecutor();

            takeScreenshot(displayId, executor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshot) {
                    try {
                        HardwareBuffer hb = screenshot.getHardwareBuffer();
                        ColorSpace cs = screenshot.getColorSpace();

                        if (hb == null) return;

                        Bitmap wrapped = Bitmap.wrapHardwareBuffer(hb, cs);
                        if (wrapped == null) {
                            hb.close();
                            return;
                        }

                        Bitmap copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
                        hb.close();

                        notifyListeners(copy);
                    } catch (Throwable t) {
                        Log.e(TAG, "Error processing screenshot", t);
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Log.w(TAG, "takeScreenshot failed, errorCode=" + errorCode);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "requestScreenshot thrown", t);
        }
    }

    private void notifyListeners(Bitmap bitmap) {
        synchronized (listeners) {
            for (ScreenshotListener listener : listeners) {
                listener.onScreenshotCaptured(bitmap);
            }
        }
    }
}
