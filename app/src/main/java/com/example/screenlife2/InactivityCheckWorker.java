package com.example.screenlife2;


import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

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

        //if capture service is running we assume user intends to resume app at some point
        boolean isAppRunning = isServiceRunning();

        if (isAppRunning) {
            Log.d(TAG, "App is currently running, no need to remind user to restart");
        } else {
            // Create notification channel
            createNotificationChannel();

            // Show notification
            showNotification();
        }

        return Result.success();
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE);

        if (manager == null) return false;

        List<ActivityManager.RunningServiceInfo> runningServices = manager
                .getRunningServices(Integer.MAX_VALUE);

        if (runningServices == null) return false;

        String serviceName = CaptureService.class.getName();

        for (ActivityManager.RunningServiceInfo service : runningServices) {
            if (serviceName.equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
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
                .setContentTitle("Capture Service")
                .setContentText("Please re-open ScreenSight to continue phone monitoring")
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
