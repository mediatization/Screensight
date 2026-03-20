package com.example.screenlife2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.graphics.Bitmap;

import androidx.core.app.NotificationCompat;

public class CaptureService extends Service {
    private static final String TAG = "CaptureService";
    private static final String CHANNEL_ID = "CaptureServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private CaptureScheduler captureScheduler = null;
    private UploadScheduler uploadScheduler = null;
    public boolean Initialized = false;

    private static CaptureService instance = null;

    public static CaptureService getInstance() {
        return instance;
    }

    @Override
    public void onCreate(){
        super.onCreate();
        instance = this;
        CaptureAccessibilityService.registerListener(this::handleAccessibilityScreenshot);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Capture Service On Start was called");
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

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Capture Service")
                .setContentText("The service is running in the foreground")
                .setSmallIcon(R.raw.appicon)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();

        // Android 14+: Determine foreground service type dynamically
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            
            // Only use mediaProjection type if we actually have the projection intent data
            if (intent != null && intent.hasExtra("intentData")) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                Log.d(TAG, "Starting foreground service with MEDIA_PROJECTION type");
            } else {
                Log.d(TAG, "Starting foreground service with DATA_SYNC type (Accessibility mode)");
            }
            
            startForeground(NOTIFICATION_ID, notification, type);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        int screenDensity = intent.getIntExtra("screenDensity", 0);
        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent parceIntent = intent.getParcelableExtra("intentData");
        
        if (uploadScheduler == null)
            uploadScheduler = new UploadScheduler(getApplicationContext());
        if (captureScheduler == null)
            captureScheduler = new CaptureScheduler(getApplicationContext(), screenDensity, resultCode, parceIntent);

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
        if (instance == this) instance = null;

        CaptureAccessibilityService.unregisterListener(this::handleAccessibilityScreenshot);

        try {
            if (captureScheduler != null) {
                captureScheduler.stopCapture(false);
                captureScheduler.destroy();
            }
        } catch (Exception e) { Log.e(TAG, "error stopping capture scheduler", e); }
        
        try {
            if (uploadScheduler != null) {
                uploadScheduler.stopUpload();
                uploadScheduler.destroy();
            }
        } catch (Exception e) { Log.e(TAG, "error stopping upload scheduler", e); }

        super.onDestroy();
    }

    public void addCaptureListener(CaptureScheduler.CaptureListener listener){
        if (captureScheduler != null) captureScheduler.addListener(listener);
    }
    public void clearCaptureListeners(){
        if (captureScheduler != null) captureScheduler.clearListeners();
    }
    public void startCapture(){
        Initialized = true;
        if (captureScheduler != null) captureScheduler.startCapture();
    }
    public void stopCapture(boolean manualPause){
        if (captureScheduler != null)
            captureScheduler.stopCapture(manualPause);
    }
    public void updateCapture() { if (captureScheduler != null) captureScheduler.updateCapture(); }
    public int getNumCaptured() { if (captureScheduler != null) return captureScheduler.getNumCaptured(); return 0; }
    public int getSizeCapturedKB() { if (captureScheduler != null) return captureScheduler.getSizeCapturedKB(); return 0; }
    public CaptureScheduler.CaptureStatus getCaptureStatus() { return captureScheduler != null ? captureScheduler.getCaptureStatus() : CaptureScheduler.CaptureStatus.STOPPED; }

    public void addUploadListener(UploadScheduler.UploadListener listener){
        if (uploadScheduler != null) uploadScheduler.addListener(listener);
    }

    public void startUpload(){ if (uploadScheduler != null) uploadScheduler.startUpload(); }
    public void updateUpload() { if (uploadScheduler != null) uploadScheduler.updateUpload(); }
    public void stopUpload() { if (uploadScheduler != null) uploadScheduler.stopUpload(); }
    public UploadScheduler.UploadStatus getUploadStatus() {return uploadScheduler != null ? uploadScheduler.getUploadStatus() : UploadScheduler.UploadStatus.IDLE;}

    public void handleAccessibilityScreenshot(Bitmap bitmap) {
        if (bitmap == null) return;
        if (captureScheduler != null) {
            captureScheduler.saveFromAccessibility(bitmap, Constants.USER_ID);
            captureScheduler.updateCapture();
            Log.d(TAG, "handleAccessibilityScreenshot: forwarded bitmap to captureScheduler");
        }
    }
}
