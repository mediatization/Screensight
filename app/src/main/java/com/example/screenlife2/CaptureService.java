package com.example.screenlife2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.lang.reflect.Method;

// A Service allows us to run functions in the background while the app is minimized.
// We are using a bound service, which acts almost like a server.
// The Service is bound to an app, the one running in the background
// When the app is terminated, the Service is as well
// The app can send messages and receive answers from the service
// This service is bound and a foreground service
public class CaptureService extends Service {
    private static final String TAG = "CaptureService";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    // The scheduler used to take screenshots
    private CaptureScheduler captureScheduler = null;
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
        // Create a notification channel for Android 8.0 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // Create a notification for the foreground service
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("The service is running in the foreground")
                .setSmallIcon(R.drawable.ic_launcher_background)  // Add an icon for the notification
                .setContentIntent(pendingIntent)
                .build();

        // Start the service in the foreground with the notification
        startForeground(NOTIFICATION_ID, notification);
        return Service.START_NOT_STICKY;
    }

    // Required function for Services to bind, call this after start
    @Override
    public IBinder onBind(Intent intent) {
        int screenDensity = intent.getIntExtra("screenDensity", 0);
        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent parceIntent = intent.getParcelableExtra("intentData");
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

    // PLACEHOLDER
    @Override
    public void onDestroy(){
        captureScheduler.stopCapture();
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
    public void start(){
        Initialized = true;
        captureScheduler.startCapture();
    }
    // Pauses capturing
    public void pause(){
        captureScheduler.pauseCapture();
    }
    // Pauses capturing
    public void stop(){
        captureScheduler.stopCapture();
    }
    // Updates any listeners
    public void update() { captureScheduler.updateCapture(); }
    // Returns captured files
    public File[] getFiles(){
        return captureScheduler.getCaptures();
    }
    // Returns the number of captured files
    public int getNumCaptured() {return captureScheduler.getNumCaptured();}
    // Returns the capture status
    public CaptureScheduler.CaptureStatus getCaptureStatus() { return captureScheduler.getCaptureStatus();}
}