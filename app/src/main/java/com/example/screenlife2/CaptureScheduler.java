package com.example.screenlife2;

import static androidx.core.content.ContextCompat.getSystemService;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;

import android.os.Build;

import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

// A scheduler which periodically takes captures
public class CaptureScheduler {
    private static final String TAG = "CaptureScheduler";
    public interface CaptureListener{
        void onInvoke(CaptureStatus status, int numCaptured);
    }
    public enum CaptureStatus
    {
        STOPPED,
        CAPTURING
    }
    private CaptureStatus m_captureStatus = CaptureStatus.STOPPED;
    private final long m_captureInterval = 3000;
    // The scheduler
    private final ScheduledExecutorService m_scheduler =
            Executors.newScheduledThreadPool(1);
    // The current runnable task of the scheduler
    private ScheduledFuture<?> m_captureHandle = null;
    // The context of the application, needed for certain function calls.
    private Context m_context;
    private MediaProjection m_mediaProjection;
    private MediaProjectionManager m_projectionManager;
    private MediaProjectionCallback m_mediaProjectionCallback;
    // The image reader that allows us to take screenshots
    private ImageReader m_imageReader;
    private VirtualDisplay m_virtualDisplay;
    private static int m_screenDensity;
    private static int m_pixelStride;
    private static int m_rowPadding;
    private final int DISPLAY_WIDTH = 720;
    private final int DISPLAY_HEIGHT = 1280;
    private static final DateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    private ArrayList<CaptureListener> m_onStatusChangedCallbacks = new ArrayList<>();

    // optional field to accept an AccessibilityService, kept for compatibility
    private MyAccessibilityService accessibilityService;

    private UploadScheduler m_uploadScheduler;

    public CaptureScheduler (Context context, int screenDensity, int resultCode, Intent intent, UploadScheduler us){
        m_context = context;
        m_screenDensity = screenDensity;
        m_projectionManager = getSystemService(context, MediaProjectionManager.class);
        m_imageReader = ImageReader.newInstance(DISPLAY_WIDTH, DISPLAY_HEIGHT,
                PixelFormat.RGBA_8888, 5);
        m_imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "An image is available");
            }
        }, null);
        m_uploadScheduler = us;

        // If MediaProjection unavailable, we'll rely on accessibility when set via setter later
        if (m_projectionManager != null && intent != null) {
            m_mediaProjection = m_projectionManager.getMediaProjection(resultCode, intent);
            if (m_mediaProjection != null) {
                m_mediaProjectionCallback = new MediaProjectionCallback();
                m_mediaProjection.registerCallback(m_mediaProjectionCallback, null);
                m_virtualDisplay = m_mediaProjection.createVirtualDisplay(TAG, DISPLAY_WIDTH,
                        DISPLAY_HEIGHT, m_screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, m_imageReader.getSurface(),
                        null, null);
            }
        }

        // If a CaptureScheduler was stored by the Accessibility service wiring helper, ensure it's discoverable
        // (MyAccessibilityService sets CaptureSchedulerHolder.set(thisScheduler) when creating the scheduler.)
    }

    // Setter to provide an AccessibilityService instance (optional)
    public void setAccessibilityService(MyAccessibilityService service) {
        this.accessibilityService = service;
        Log.d("CaptureScheduler", "AccessibilityService reference set");
    }

    // Allow AccessibilityService (or other callers) to save a captured bitmap via the same pipeline
    public void saveFromAccessibility(Bitmap bitmap, String descriptor) {
        if (bitmap == null) return;
        encryptImage(bitmap, descriptor);
    }

    public void destroy(){
        // Release MediaProjection
        if (m_mediaProjection != null) {
            m_mediaProjection.stop();
            m_mediaProjection = null;
        }
        // Release VirtualDisplay
        if (m_virtualDisplay != null) {
            m_virtualDisplay.release();
            m_virtualDisplay = null;
        }
        // Release ImageReader
        if (m_imageReader != null) {
            m_imageReader.close();
            m_imageReader = null;
        }
    }

    // Adds listeners when the status changes
    public void addListener(CaptureListener listener){
        m_onStatusChangedCallbacks.add(listener);
    }
    public void clearListeners(){
        m_onStatusChangedCallbacks.clear();
    }
    private void invokeListeners() {
        Log.d(TAG, "Invoking listeners: " + m_onStatusChangedCallbacks.size());
        for(CaptureListener listener : m_onStatusChangedCallbacks)
        {
            listener.onInvoke(m_captureStatus, getNumCaptured());
        }
    }
    // Schedules to start
    public void startCapture() {
        Log.d(TAG, "Starting capture");
        stopCapture(false);
        Runnable captureRunner = this::takeCapture;
        m_captureHandle = m_scheduler.scheduleWithFixedDelay(captureRunner, m_captureInterval, m_captureInterval, MILLISECONDS);
        m_captureStatus = CaptureStatus.CAPTURING;
        // Invoke listeners
        invokeListeners();
    }
    // Stops capture and pause
    public void stopCapture(boolean manualPause){
        if (m_captureHandle == null)
            return;
        if (manualPause)
            insertPauseImage();
        m_captureHandle.cancel(false);
        m_captureHandle = null;
        m_captureStatus = CaptureStatus.STOPPED;
        // Invoke listeners
        invokeListeners();
    }
    public void updateCapture(){
        // Invoke listeners
        invokeListeners();
    }
    //
    private void takeCapture() {
        //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);

        // Unified capture entrypoint that prefers Accessibility (API33+) when available
        Log.d(TAG, "Taking a capture");

        if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            accessibilityService.requestScreenshot();
        }
        else {
            mediaProjectionScreenshot();
        }

        if (this.getNumCaptured() >= Constants.AUTO_UPLOAD_COUNT && m_uploadScheduler.ableToUpload()) {
            m_uploadScheduler.startUpload();
        }
    }


    private void mediaProjectionScreenshot() {
        if (m_imageReader == null) {
            Log.d(TAG, "ImageReader is null, cannot capture via MediaProjection");
            return;
        }
        Image image = null;
        try {
            image = m_imageReader.acquireLatestImage();
            Log.d(TAG, "Took an image");
            Log.d(TAG, "Max images " + m_imageReader.getMaxImages());
            if (image == null) {
                Log.d(TAG, "Image was null, canceling capture...");
                return;
            }
            Image.Plane[] planes = image.getPlanes();
            Log.d(TAG, "Got planes");
            ByteBuffer buffer = planes[0].getBuffer();
            Log.d(TAG, "Got a buffer");
            m_pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            m_rowPadding = rowStride - m_pixelStride * DISPLAY_WIDTH;
            Log.d(TAG, "Got image vars");

            Bitmap bitmap = Bitmap.createBitmap(DISPLAY_WIDTH + m_rowPadding / m_pixelStride,
                    DISPLAY_HEIGHT, Bitmap.Config.ARGB_8888);
            Log.d(TAG, "Created a bitmap");
            bitmap.copyPixelsFromBuffer(buffer);
            Log.d(TAG, "Copied buffer to bitmap");
            encryptImage(bitmap, "placeholder");
            Log.d(TAG, "Encrypted an image");
            buffer.rewind();
            Log.d(TAG, "Took a capture");
            // Invoke the listeners
            invokeListeners();
            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error during media projection capture", e);
        } finally {
            if (image != null) {
                try { image.close(); } catch (Exception ex) { /* ignore */ }
            }
        }
    }
    private void insertPauseImage()
    {
        //android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        Log.d(TAG, "Inserting pause image");
        InputStream is = m_context.getResources().openRawResource(R.raw.pauserecord);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        encryptImage(bitmap, "pause");
    }
    private void encryptImage(Bitmap bitmap, String descriptor) {
        String hash = Constants.USER_HASH;
        String keyRaw = Constants.USER_KEY;
        byte[] key = Converter.hexStringToByteArray(keyRaw);
        FileOutputStream fos = null;
        Date date = new Date();
        String dir = m_context.getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/screenLife";
        String dir2 = dir + "/images";
        String dir3 = dir + "/encrypt";
        String screenshot = "/" + hash.substring(0,8) + "_" + sdf.format(date) + "_" + descriptor + ".png";

        try {
            if (!keyRaw.isEmpty()) {
                // LOOK FOR screenLife DIRECTORY
                Settings.findOrCreateDirectory(dir);
                // LOOK FOR images DIRECTORY
                Settings.findOrCreateDirectory(dir2);
                // LOOK FOR encrypt DIRECTORY
                Settings.findOrCreateDirectory(dir3);
                // Create the file output stream
                fos = new FileOutputStream(dir2 + screenshot);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                try {
                    Encryptor.encryptFile(key, screenshot, dir2 + screenshot, dir3 + screenshot);
                    Log.d(TAG, "Encryption with hash " + hash);
                    Log.d(TAG, "Encryption with raw key " + keyRaw);
                    Log.d(TAG, "Encryption with key " + Arrays.toString(key));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                File f = new File(dir2 + screenshot);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try { bitmap.recycle(); } catch (Exception ex) { /* ignore */ }
        }
    }
    //
    public File[] getCaptures()
    {
        // TODO: CONSIDER SAVING THESE PATHS IN CONSTANTS
        String dir = m_context.getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/screenLife/encrypt";
        File directory = new File(dir);
        FileFilter filter = new FileFilter()
        {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith(".png");
            }
        };
        //Log.d(TAG, "Is " + dir + " a directory? " + directory.isDirectory());
        File[] results = directory.listFiles(/*filter*/);
        if (results == null)
            results = new File[]{};
        return results;
    }
    public int getNumCaptured(){
        return getCaptures().length;
    }
    public int getSizeCapturedKB()
    {
        int toReturn = 0;
        File[] captures = getCaptures();
        for (File capture : captures) {
            toReturn += Integer.parseInt(String.valueOf(capture.length() / 1024));
        }
        return toReturn;
    }
    public CaptureStatus getCaptureStatus() { return m_captureStatus; }

    // Called when Screen Cast is disabled
    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e(TAG, "I'm stopped");

            try {
                // Ensure we stop scheduled captures when projection stops
                stopCapture(false); // EDIT: stop capturing when projection revoked
                // If accessibility is available, we could continue capturing there
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    MyAccessibilityService svc = MyAccessibilityService.getInstance();
                    if (svc != null) {
                        Log.d(TAG, "MediaProjection stopped; accessibility fallback available");
                        // Optionally restart capture using accessibility immediately
                        //startCapture();
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
