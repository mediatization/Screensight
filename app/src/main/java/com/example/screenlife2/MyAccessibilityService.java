package com.example.screenlife2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;

import android.content.Intent;   // <-- this is the one you’re missing

/**
 * Accessibility service that attempts to take screenshots using the Android 13+ API via reflection.
 *
 * How to use:
 *  - Enable the service in Settings -> Accessibility.
 *  - Either call MyAccessibilityService.setScheduler(yourScheduler) from your Activity
 *    when you create the CaptureScheduler, or let the scheduler call
 *    CaptureScheduler.setAccessibilityService(MyAccessibilityService.getInstance()) once the service is connected.
 */
public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "MyAccessibilityService";

    // single shared instance for easy wiring. Set in onServiceConnected()
    private static MyAccessibilityService sInstance = null;

    // optional scheduler reference (set by your Activity or when scheduler is created)
    private CaptureScheduler mScheduler = null;

    public static MyAccessibilityService getInstance() {
        return sInstance;
    }

    public void setScheduler(CaptureScheduler scheduler) {
        this.mScheduler = scheduler;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used for screenshotting; keep empty or use for other telemetry.
    }

    @Override
    public void onInterrupt() {
        // ignore for now
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service connected");

        // register with CaptureService
        CaptureService.setAccessibilityService(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Accessibility service disconnected");
        if (sInstance == this) sInstance = null;
        return super.onUnbind(intent);
    }
    

    /**
     * Try to take a screenshot. This method uses reflection to call the Android 13 API
     * TakeScreenshotRequest/AccessibilityService.takeScreenshot if available. If it succeeds
     * the resulting bitmap will be passed to CaptureScheduler.saveFromAccessibility(bitmap, descriptor).
     */
    public void requestScreenshot() {
        // must be API 33+ at runtime to succeed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.e(TAG, "requestScreenshot: Android version < 33; screenshot API unavailable.");
            return;
        }

        if (mScheduler == null && CaptureSchedulerHolder.get() != null) {
            // optional: try to fetch a scheduler if stored in CaptureSchedulerHolder
            mScheduler = CaptureSchedulerHolder.get();
        }
        try {
            // Build TakeScreenshotRequest via reflection: new TakeScreenshotRequest.Builder().setSource(1).build()
            Class<?> builderClass = Class.forName("android.accessibilityservice.TakeScreenshotRequest$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();

            // setSource(int) - SOURCE_DISPLAY == 1
            try {
                Method setSource = builderClass.getMethod("setSource", int.class);
                setSource.invoke(builder, 1); // SOURCE_DISPLAY
            } catch (NoSuchMethodException ignored) {
                // older/variant builder might not have setSource; ignore
            }

            Method buildMethod = builderClass.getMethod("build");
            Object request = buildMethod.invoke(builder);

            // Prepare callback interface type
            Class<?> callbackIface = Class.forName("android.accessibilityservice.AccessibilityService$TakeScreenshotCallback");

            // Create a dynamic proxy to implement the callback interface
            Object callbackProxy = Proxy.newProxyInstance(
                    callbackIface.getClassLoader(),
                    new Class<?>[]{callbackIface},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        try {
                            if ("onSuccess".equals(name)) {
                                Object result = args != null && args.length > 0 ? args[0] : null;
                                if (result != null) {
                                    // result is android.accessibilityservice.AccessibilityService.ScreenshotResult
                                    Method getHwBuffer = result.getClass().getMethod("getHardwareBuffer");
                                    Object hwBuffer = getHwBuffer.invoke(result); // android.hardware.HardwareBuffer
                                    Method getColorSpace = null;
                                    try {
                                        getColorSpace = result.getClass().getMethod("getColorSpace");
                                    } catch (NoSuchMethodException ignored) {}
                                    Object colorSpace = null;
                                    if (getColorSpace != null) {
                                        colorSpace = getColorSpace.invoke(result); // android.graphics.ColorSpace
                                    }

                                    if (hwBuffer != null) {
                                        // Wrap hardware buffer into Bitmap
                                        Bitmap bmp = null;
                                        try {
                                            // cast is safe on runtime devices that have the class
                                            bmp = Bitmap.wrapHardwareBuffer((android.hardware.HardwareBuffer) hwBuffer,
                                                    (android.graphics.ColorSpace) colorSpace);
                                        } catch (Throwable t) {
                                            Log.e(TAG, "wrapHardwareBuffer failed", t);
                                        }

                                        if (bmp != null) {
                                            Log.d(TAG, "Accessibility screenshot obtained; saving via scheduler.");
                                            if (mScheduler != null) {
                                                mScheduler.saveFromAccessibility(bmp, "accessibility");
                                            } else {
                                                Log.w(TAG, "Scheduler is null; not saving screenshot.");
                                            }
                                            // optionally recycle if you want to free memory here:
                                            // bmp.recycle();
                                        } else {
                                            Log.e(TAG, "Bitmap.wrapHardwareBuffer returned null");
                                        }
                                    } else {
                                        Log.e(TAG, "HardwareBuffer was null in ScreenshotResult");
                                    }
                                } else {
                                    Log.e(TAG, "ScreenshotResult is null in onSuccess");
                                }
                            } else if ("onFailure".equals(name)) {
                                int error = (args != null && args.length > 0 && args[0] instanceof Integer) ? (Integer) args[0] : -1;
                                Log.e(TAG, "takeScreenshot onFailure: " + error);
                            }
                        } catch (Throwable ex) {
                            Log.e(TAG, "Callback proxy error", ex);
                        }
                        return null;
                    }
            );

            // Acquire reference to takeScreenshot(TakeScreenshotRequest, Executor, TakeScreenshotCallback)
            Method takeScreenshotMethod = AccessibilityService.class.getMethod(
                    "takeScreenshot",
                    Class.forName("android.accessibilityservice.TakeScreenshotRequest"),
                    java.util.concurrent.Executor.class,
                    callbackIface);

            // Executor that runs callbacks on calling thread (synchronous)
            Executor executor = Runnable::run;

            // invoke
            takeScreenshotMethod.invoke(this, request, executor, callbackProxy);
            Log.d(TAG, "takeScreenshot invoked via reflection.");
        } catch (ClassNotFoundException cnf) {
            Log.e(TAG, "Screenshot API classes not found at runtime", cnf);
        } catch (NoSuchMethodException nsme) {
            Log.e(TAG, "Screenshot API method missing", nsme);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to invoke takeScreenshot via reflection", t);
        }
    }

    // --- Convenience holder so CaptureScheduler can share a reference if desired ---
    public static class CaptureSchedulerHolder {
        private static CaptureScheduler sScheduler;
        public static void set(CaptureScheduler scheduler) { sScheduler = scheduler; }
        public static CaptureScheduler get() { return sScheduler; }
    }
}
