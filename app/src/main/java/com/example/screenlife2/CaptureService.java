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

import androidx.core.app.NotificationCompat;

import java.io.File;

// A Service allows us to run functions in the background while the app is minimized.
// We are using a bound service, which acts almost like a server.
// The Service is bound to an app, the one running in the background
// When the app is terminated, the Service is as well
// The app can send messages and receive answers from the service
// This service is bound and a foreground service
public class CaptureService extends Service {
    private static final String TAG = "CaptureService";
    private static final String CHANNEL_ID = "CaptureServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    // The scheduler used to take screenshots
    private CaptureScheduler captureScheduler = null;
    // The scheduler used to upload
    private UploadScheduler uploadScheduler;
    // Whether the service has been started at least once
    public boolean Initialized = false;

    // Create is called first
    @Override
    public void onCreate(){
        // Do nothing
    }
    // Then we should call start service
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

    // Required function for Services to bind, call this after start
    @Override
    public IBinder onBind(Intent intent) {
        int screenDensity = intent.getIntExtra("screenDensity", 0);
        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent parceIntent = intent.getParcelableExtra("intentData");
        if (captureScheduler == null)
            captureScheduler = new CaptureScheduler(getApplicationContext(), screenDensity, resultCode, parceIntent);
        if (uploadScheduler == null)
            uploadScheduler = new UploadScheduler(getApplicationContext());
        Log.d(TAG, "Capture Service on bind was called");
        return new LocalBinder();
    }
    public class LocalBinder extends Binder {
        public CaptureService getService() {
            return CaptureService.this;
        }
    }

    // PLACEHOLDER
    @Override
    public void onDestroy(){
        Log.d(TAG, "Capture Service destroyed");
        captureScheduler.stopCapture();
        uploadScheduler.stopUpload();
        captureScheduler.destroy();
        uploadScheduler.destroy();
        super.onDestroy();
    }
    /** Methods for the client */

    //
    public void addCaptureListener(CaptureScheduler.CaptureListener listener){
        captureScheduler.addListener(listener);
    }
    //
    public void clearCaptureListeners(){
        captureScheduler.clearListeners();
    }
    // Starts capturing
    public void startCapture(){
        Initialized = true;
        captureScheduler.startCapture();
    }
    // Pauses capturing
    public void pauseCapture(){
        captureScheduler.pauseCapture();
    }
    // Pauses capturing
    public void stopCapture(){
        captureScheduler.stopCapture();
    }
    // Updates any listeners
    public void updateCapture() { captureScheduler.updateCapture(); }
    // Returns captured files
    public File[] getFiles(){
        return captureScheduler.getCaptures();
    }
    // Returns the number of captured files
    public int getNumCaptured() {return captureScheduler.getNumCaptured();}
    public int getSizeCapturedKB() {return captureScheduler.getSizeCapturedKB();}
    // Returns the capture status
    public CaptureScheduler.CaptureStatus getCaptureStatus() { return captureScheduler.getCaptureStatus();}

    public void addUploadListener(UploadScheduler.UploadListener listener){
        uploadScheduler.addListener(listener);
    }

    public void startUpload(){uploadScheduler.startUpload();}
    public void updateUpload() {uploadScheduler.updateUpload();}
    public void stopUpload() {uploadScheduler.stopUpload();}
    public UploadScheduler.UploadStatus getUploadStatus() {return uploadScheduler.getUploadStatus();}
    public UploadScheduler.UploadResult getUploadResult() {return uploadScheduler.getUploadResult();}
    public boolean ableToUpload() {return uploadScheduler.ableToUpload();}

    public void reminderToRestart() {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

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
                .setContentText("Please re-open ScreenSight to continue phone monitoring")
                .setSmallIcon(R.raw.appicon)  // Add an icon for the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();

        // Get NotificationManager and show the notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID+1, notification);
    }

}