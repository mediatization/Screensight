package com.example.screenlife2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class UploadService extends Service {
    private static final String TAG = "UploadService";
    private static final String CHANNEL_ID = "UploadServiceChannel";
    private static final int NOTIFICATION_ID = 2;
    private UploadScheduler uploadScheduler;
    // PLACEHOLDER
    @Override
    public void onCreate(){
        // Do nothing
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (uploadScheduler == null)
            uploadScheduler = new UploadScheduler(getApplicationContext());
        Log.d(TAG, "Upload Service on bind was called");
        return new UploadService.LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Upload Service On Start was called");
        // Create a notification channel for Android 8.0 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Upload Service Channel",
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
                .setContentTitle("Upload Service")
                .setContentText("The service is running in the foreground")
                .setSmallIcon(R.drawable.ic_launcher_background)  // Add an icon for the notification
                .setContentIntent(pendingIntent)
                .build();

        // Start the service in the foreground with the notification
        startForeground(NOTIFICATION_ID, notification);
        return Service.START_NOT_STICKY;
    }

    //placeholder
    @Override
    public void onDestroy(){
        uploadScheduler.stopUpload();
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        public UploadService getService() {
            return UploadService.this;
        }
    }

    public void addUploadListener(UploadScheduler.UploadListener listener){
        uploadScheduler.addListener(listener);
    }

    public void start(){uploadScheduler.startUpload();}
    public void update() {uploadScheduler.updateUpload();}
    public void stop() {uploadScheduler.stopUpload();}
    public UploadScheduler.UploadStatus getUploadStatus() {return uploadScheduler.getUploadStatus();}
    public UploadScheduler.UploadResult getUploadResult() {return uploadScheduler.getUploadResult();}
    public void setWifiRequirement(boolean b) {uploadScheduler.setWifiRequirement(b);}
}
