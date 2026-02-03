package com.example.screenlife2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.graphics.Bitmap; // new: needed for handleAccessibilityScreenshot

import androidx.core.app.NotificationCompat;

import java.io.File;

public class CaptureService extends Service {
    private static final String TAG = "CaptureService";
    private static final String CHANNEL_ID = "CaptureServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    // the scheduler used to take screenshots
    private CaptureScheduler captureScheduler = null;
    // the scheduler used to upload
    private UploadScheduler uploadScheduler = null;
    // whether the service has been started at least once
    public boolean Initialized = false;

    // accessibility service
    private static MyAccessibilityService accessibilityService;

    // singleton instance so other classes can reach the running CaptureService
    private static CaptureService instance = null;

    // return the running service instance (may be null if not started/bound)
    public static CaptureService getInstance() {
        return instance;
    }

    // set accessibility service and forward to scheduler if available
    public static void setAccessibilityService(MyAccessibilityService service) {
        accessibilityService = service;
        Log.d(TAG, "AccessibilityService registered in CaptureService");

        // if a CaptureService instance and scheduler exist, forward the service to the scheduler
        CaptureService svc = CaptureService.getInstance();
        if (svc != null && svc.captureScheduler != null) {
            svc.captureScheduler.setAccessibilityService(service);
            Log.d(TAG, "forwarded accessibilityservice to captureScheduler");
        }
    }

    public static MyAccessibilityService getAccessibilityService() {
        return accessibilityService;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        // new: register running instance
        instance = this;
        Log.d(TAG, "service onCreate - instance registered");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Capture Service On Start was called");
        // Create a notification channel for Android 8.0 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Capture Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // Create a notification for the foreground service
        PendingIntent pendingIntent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12 and above
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Capture Service")
                .setContentText("The service is running in the foreground")
                .setSmallIcon(R.raw.appicon)  // Add an icon for the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();

        // Start the service in the foreground with the notification
        startForeground(NOTIFICATION_ID, notification);
        return Service.START_STICKY;
    }

    // required function for Services to bind, call this after start
    @Override
    public IBinder onBind(Intent intent) {
        int screenDensity = intent.getIntExtra("screenDensity", 0);
        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent parceIntent = intent.getParcelableExtra("intentData");
        if (uploadScheduler == null)
            uploadScheduler = new UploadScheduler(getApplicationContext());
        if (captureScheduler == null)
            captureScheduler = new CaptureScheduler(getApplicationContext(), screenDensity, resultCode, parceIntent, uploadScheduler);

        // if accessibilityService already registered, forward it into scheduler
        if (accessibilityService != null && captureScheduler != null) {
            captureScheduler.setAccessibilityService(accessibilityService);
            Log.d(TAG, "onBind: forwarded existing accessibilityService to captureScheduler");
        }

        Log.d(TAG, "Capture Service on bind was called");
        return new LocalBinder();
    }
    public class LocalBinder extends Binder {
        public CaptureService getService() {
            return CaptureService.this;
        }
    }

    @Override
    public void onDestroy(){
        Log.d(TAG, "Capture Service destroyed");

        // new: clear singleton instance
        if (instance == this) instance = null;

        // cleanup schedulers safely
        try {
            if (captureScheduler != null) {
                captureScheduler.stopCapture();
                captureScheduler.destroy();
            }
        } catch (Exception e) { Log.e(TAG, "error stopping capture scheduler", e); }
        try {
            if (uploadScheduler != null) {
                uploadScheduler.stopUpload();
                uploadScheduler.destroy();
            }
        } catch (Exception e) { Log.e(TAG, "error stopping upload scheduler", e); }

        // clear accessibility reference if present (we'll let the accessibility service null itself too)
        accessibilityService = null;

        super.onDestroy();
    }

    /** Methods for the client */

    public void addCaptureListener(CaptureScheduler.CaptureListener listener){
        if (captureScheduler != null) captureScheduler.addListener(listener);
    }
    public void clearCaptureListeners(){
        if (captureScheduler != null) captureScheduler.clearListeners();
    }
    // starts capturing
    public void startCapture(){
        Initialized = true;
        if (captureScheduler != null) captureScheduler.startCapture();
    }
    // stops capturing
    public void stopCapture(){
        if (captureScheduler != null) captureScheduler.stopCapture();
    }
    // updates any listeners
    public void updateCapture() { if (captureScheduler != null) captureScheduler.updateCapture(); }
    // returns the number of captured files
    public int getNumCaptured() { if (captureScheduler != null) return captureScheduler.getNumCaptured(); return 0; }
    public int getSizeCapturedKB() { if (captureScheduler != null) return captureScheduler.getSizeCapturedKB(); return 0; }
    // returns the capture status
    public CaptureScheduler.CaptureStatus getCaptureStatus() { return captureScheduler != null ? captureScheduler.getCaptureStatus() : CaptureScheduler.CaptureStatus.STOPPED; }

    public void addUploadListener(UploadScheduler.UploadListener listener){
        if (uploadScheduler != null) uploadScheduler.addListener(listener);
    }

    public void startUpload(){ if (uploadScheduler != null) uploadScheduler.startUpload(); }
    public void updateUpload() { if (uploadScheduler != null) uploadScheduler.updateUpload(); }
    public void stopUpload() { if (uploadScheduler != null) uploadScheduler.stopUpload(); }
    public UploadScheduler.UploadStatus getUploadStatus() {return uploadScheduler != null ? uploadScheduler.getUploadStatus() : UploadScheduler.UploadStatus.IDLE;}
    public boolean ableToUpload() {return uploadScheduler != null && uploadScheduler.ableToUpload();}

    // accessibility -> scheduler entrypoint, called by MyAccessibilityService when it has a bitmap
    public void handleAccessibilityScreenshot(Bitmap bitmap, String descriptor) {
        // new: defensive null checks
        if (bitmap == null) {
            Log.w(TAG, "handleAccessibilityScreenshot: bitmap is null");
            return;
        }
        if (captureScheduler != null) {
            // new: hand bitmap into same pipeline used by media projection
            captureScheduler.saveFromAccessibility(bitmap, descriptor);
            // also notify listeners that a capture occurred
            captureScheduler.updateCapture();
            Log.d(TAG, "handleAccessibilityScreenshot: forwarded bitmap to captureScheduler");
        } else {
            // new: if scheduler not initialized yet, optionally save to temp or log
            Log.w(TAG, "handleAccessibilityScreenshot: captureScheduler is null - cannot save screenshot now");
            // optional: store temporarily or drop - behavior depends on your needs
        }
    }

}
