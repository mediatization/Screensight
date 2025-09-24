package com.example.screenlife2;

import android.content.Context;
import android.util.Log;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class InactivityCheckWorker extends Worker {

    private static final String TAG = "InactivityCheckWorker";

    public InactivityCheckWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {

        Log.d(TAG, "Starting worker task");

        // Check inactivity and send notification, maybe optimize to not use NotificationHelper?
        NotificationHelper notificationHelper = new NotificationHelper(getApplicationContext());

        // Get last used time from SharedPreferences
        long lastUsedTime = getApplicationContext()
                .getSharedPreferences("app_usage", Context.MODE_PRIVATE)
                .getLong("last_used_time", System.currentTimeMillis());

        long timeSinceLastUsed = System.currentTimeMillis() - lastUsedTime;

        if (timeSinceLastUsed > TimeUnit.MINUTES.toMillis(5)) {
            notificationHelper.sendReminderNotification();
            Log.d(TAG, "Sending reminder notification");
        }

        return Result.success();
    }
}
