package com.example.screenlife2;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Environment;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.app.Application;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

// Another version of the capture service that uses a ScheduledExecutorService
public class CaptureScheduler {
    public enum CaptureStatus
    {
        STOPPED,
        PAUSED,
        CAPTURING
    }
    private CaptureStatus captureStatus = CaptureStatus.STOPPED;
    private final long captureInterval = 3000;
    private final long pauseDuration = 300000;
    // The scheduler
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    // The current runnable task of the scheduler
    private ScheduledFuture<?> captureHandle = null;

    // Schedules to start
    public void startCapture() {
        stopCapture();
        insertResumeImage();
        Runnable captureRunner = this::takeCapture;
        captureHandle = scheduler.scheduleWithFixedDelay(captureRunner, captureInterval, captureInterval, MILLISECONDS);
        captureStatus = CaptureStatus.CAPTURING;
    }
    // Stops capture and pause
    public void stopCapture(){
        if (captureHandle == null)
            return;
        insertPauseImage();
        captureHandle.cancel(false);
        captureHandle = null;
        captureStatus = CaptureStatus.STOPPED;
    }
    // Stops capture and schedules to start
    public void pauseCapture(){
        stopCapture();
        captureStatus = CaptureStatus.PAUSED;
        Runnable capturePauser = this::startCapture;
        captureHandle = scheduler.schedule(capturePauser, pauseDuration, MILLISECONDS);
    }
    //
    private void takeCapture() {
        /*
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        if (!mKeyguardManager.isKeyguardLocked()) {

            System.out.println("CAPTURE:INTERVAL:2");

            Image image = mImageReader.acquireLatestImage();
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            rowPadding = rowStride - pixelStride * DISPLAY_WIDTH;
            image.close();

            Bitmap bitmap = Bitmap.createBitmap(DISPLAY_WIDTH + rowPadding / pixelStride,
                    DISPLAY_HEIGHT, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            encryptImage(bitmap, "placeholder");
            buffer.rewind();
        }
        mHandler.postDelayed(captureInterval, CAPTURE_INTERVAL_VALUE);
        */
    }
    private void insertResumeImage()
    {
        /*
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        InputStream is = getResources().openRawResource(R.raw.resumerecord);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        encryptImage(bitmap, "resume");
         */
    }
    private void insertPauseImage()
    {
        /*
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        InputStream is = getResources().openRawResource(R.raw.pauserecord);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        encryptImage(bitmap, "pause");
         */
    }
    private void encryptImage(Bitmap bitmap, String descriptor) {
        /*
        Settings prefs = new Settings();
        String hash = prefs.getString("hash", "00000000").substring(0, 8);
        String keyRaw = prefs.getString("key", "");
        byte[] key = Converter.hexStringToByteArray(keyRaw);
        FileOutputStream fos = null;
        Date date = new Date();
        String dir = getApplicationContext().getExternalFilesDir(null).getAbsolutePath();
        String screenshot = "/" + hash + "_" + sdf.format(date) + "_" + descriptor + ".png";

        try {
            if (keyRaw != "") {

                fos = new FileOutputStream(dir + "/images" + screenshot);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
                try {
                    Encryptor.encryptFile(key, screenshot, dir + "/images" + screenshot, dir +
                            "/encrypt" + screenshot);
                    Log.i(TAG, "Encryption done with key " + Arrays.toString(key));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                File f = new File(dir + "/images" + screenshot);
            }
//            if (f.delete()) Log.e(TAG, "file deleted: " + dir + "/images" + screenshot);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            bitmap.recycle();
        }
        */
    }
    //
    public String[] getCaptures()
    {
        return null;
    }
    public CaptureStatus getCaptureStatus() { return captureStatus; }
}
