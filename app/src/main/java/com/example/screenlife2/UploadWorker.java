package com.example.screenlife2;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class UploadWorker extends Worker {
    private static final String TAG = "UploadWorker";

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "UploadWorker started");

        Context context = getApplicationContext();
        String dirString = context.getExternalFilesDir(null) + "/screenLife/encrypt";
        File dir = new File(dirString);

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            Log.d(TAG, "No files found to upload");
            return Result.success();
        }

        LinkedList<File> fileList = new LinkedList<>(Arrays.asList(files));
        Log.d(TAG, "Found " + fileList.size() + " files to upload");

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        boolean allSuccessful = true;

        while (!fileList.isEmpty()) {
            Batch batch = new Batch(client);

            for (int i = 0; i < Constants.BATCH_SIZE && !fileList.isEmpty(); i++) {
                File file = fileList.remove();
                batch.addFile(file);
            }

            if (!batch.sendFiles()) {
                Log.e(TAG, "Batch upload failed");
                allSuccessful = false;
                break;
            }
        }

        if (allSuccessful) {
            Log.d(TAG, "UploadWorker finished successfully");
            return Result.success();
        } else {
            Log.d(TAG, "UploadWorker failed, will retry later");
            return Result.retry();
        }
    }
}
