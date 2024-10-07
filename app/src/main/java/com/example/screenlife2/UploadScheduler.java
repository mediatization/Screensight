package com.example.screenlife2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

// The main upload service
// is responsible for actually uploading, uploadService is what allows this to
// run asynchronously
public class UploadScheduler {
    private static final String TAG = "UploadScheduler";
    public enum UploadStatus
    {
        IDLE,
        UPLOADING,
        FAILED,
        SUCCESS
    }
    private UploadStatus uploadStatus = UploadStatus.IDLE;

    //interval between attempted uploads in minutes
    //could probably be turned into a const?
    private final long uploadInterval = 60;

    //For keeping track of if we are connected to wifi
    //will need to figure out how to update this
    private boolean wifiConnection = false;

    // The context of the application, needed for certain function calls.
    private Context m_context;

    // The scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // The current runnable task of the scheduler
    private ScheduledFuture<?> uploadHandle = null;

    public UploadScheduler(Context c) {
        m_context = c;
    }

    // Schedules to start
    public void startUpload() {
        stopUpload();
        Runnable uploadRunner = this::uploadImages;
        //may not make sense to have it be a constantly scheduled thing?
        uploadHandle = scheduler.scheduleWithFixedDelay(uploadRunner, uploadInterval, uploadInterval, TimeUnit.MINUTES);
        uploadStatus = UploadStatus.UPLOADING;
    }

    // Stops uploading of images
    public void stopUpload(){
        if (uploadHandle == null)
            return;
        //insertPauseImage();
        uploadHandle.cancel(false);
        uploadHandle = null;
        uploadStatus = UploadStatus.IDLE;
    }

    //looks like we are no longer doing intent stuff, will have to ask about that
    public void uploadImages() {
        //either pass in the context from main activities or ask logan how to set up a listener

        //getting directory of where files are stored and making a list
        File dir = m_context.getExternalFilesDir(null);
        //need to check for null files
        File[] files = dir.listFiles();
        //converting the files into a more iteration friendly datastructure
        LinkedList<File> fileList = new LinkedList<>(Arrays.asList(files));

        //datastructure to keep track of all the batches we make
        List<Batch> batches = new ArrayList<>();

        //split our list of files into batches
        while (!fileList.isEmpty()) {
            //creating a list of files for a single batch
            List<File> nextBatch = new LinkedList<>();

            //iterating through our list of files adding them to our nextBatch list while
            //ensuring that we do not send to many in one batch and that the file list does not run out
            for (int i = 0; i < 10  && !fileList.isEmpty(); i++) {
                nextBatch.add(fileList.remove());
            }

            //unsure what client does so leaving this as a method variable for the time being
            OkHttpClient client = new OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build();

            //creating a new batch with our list of files and adding it to our array of batches
            Batch batch = new Batch(nextBatch, client);
            batches.add(batch);
        }

        //updating our upload status
        uploadStatus = UploadStatus.UPLOADING;

        //can definitely just refactor into above while loop, too tired right now
        //should ask about necessity of doing as background task, seems like a lot of extra work
        for(Batch batch : batches) {
            String code = batch.sendFiles();
            if (code.equals("201")) {
                batch.deleteFiles();
            } else {
                System.err.println("Batches failed to send");
                uploadStatus = UploadStatus.FAILED;
                break;
            }
        }

        //updating uploadStatus
        if(uploadStatus != UploadStatus.FAILED) uploadStatus = UploadStatus.SUCCESS;

    }

    public UploadStatus getUploadStatus() { return uploadStatus; }

}