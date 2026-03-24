package com.example.screenlife2;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

public class InactivityCheckWorker extends Worker {

    private static final String TAG = "InactivityCheckWorker";
    private final Context context;
    private static final String CHANNEL_ID = "InactivityCheckWorker";
    private static final int NOTIFICATION_ID = 1;

    public InactivityCheckWorker(Context context, WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {

        Log.d(TAG, "Inactivity Worker Running");

        //checking if the screen is currently on, if its not then we don't want to annoy the user
        //with a needless notification
        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        if(!powerManager.isInteractive()) {
            return Result.success();
        }

        String dir = context.getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/screenLife/encrypt";
        File directory = new File(dir);
        File[] results = directory.listFiles();
        if (results == null) {
            sendReminder();
            return Result.success();
        }

        //now checking if any image was taken within the past 15 minutes
        long currentTime = System.currentTimeMillis();
        long fifteenMinutesInMillis = 15 * 60 * 1000;

        for (File file : results) {
            if (file.isFile()) {
                long lastModified = file.lastModified();

                if (currentTime - lastModified < fifteenMinutesInMillis) {
                    return Result.success();
                }

            }
        }

        sendReminder();
        return Result.success();
    }

    private void sendReminder() {
        // Create notification channel
        createNotificationChannel();

        // Show notification
        showNotification();
    }

    private void createNotificationChannel() {
        // Create a notification channel for Android 8.0 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Inactivity Check Worker",
                        NotificationManager.IMPORTANCE_HIGH
                );
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification() {
        Intent intent = new Intent(this.getApplicationContext(), CaptureActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Create a notification for the foreground service
        PendingIntent pendingIntent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12 and above
            pendingIntent = PendingIntent.getActivity(
                    this.getApplicationContext(),
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pendingIntent = PendingIntent.getActivity(
                    this.getApplicationContext(),
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        Notification notification = new NotificationCompat.Builder(this.getApplicationContext(), CHANNEL_ID)
                .setContentTitle("Screensight")
                .setContentText("It's been more than 15 minutes since your last screenshot please check that Screensight is running.")
                .setSmallIcon(R.raw.appicon)  // Add an icon for the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();

        // Get NotificationManager and show the notification
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
}
