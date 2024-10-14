package com.example.screenlife2;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

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
    public interface UploadListener{
        void onInvoke(UploadScheduler.UploadStatus status);
    }
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

    private ArrayList<UploadScheduler.UploadListener> m_onStatusChangedCallbacks = new ArrayList<>();

    // The scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // The current runnable task of the scheduler
    private ScheduledFuture<?> uploadHandle = null;

    public UploadScheduler(Context c) {
        m_context = c;
    }

    // Adds listeners when the status changes
    public void addListener(UploadScheduler.UploadListener listener){
        m_onStatusChangedCallbacks.add(listener);
    }
    public void clearListeners(){
        m_onStatusChangedCallbacks.clear();
    }
    private void invokeListeners() {
        Log.d(TAG, "Invoking listeners: " + m_onStatusChangedCallbacks.size());
        for(UploadScheduler.UploadListener listener : m_onStatusChangedCallbacks)
        {
            listener.onInvoke(uploadStatus);
        }
    }

    // Schedules to start
    public void startUpload() {
        stopUpload();
        Runnable uploadRunner = this::uploadImages;
        //may not make sense to have it be a constantly scheduled thing?
        Log.d(TAG, "Upload scheduler is attempting first upload");
        uploadHandle = scheduler.scheduleWithFixedDelay(uploadRunner, 0, uploadInterval, TimeUnit.MINUTES);
        uploadStatus = UploadStatus.UPLOADING;
        // Invoke listeners
        invokeListeners();
    }

    // Stops uploading of images
    public void stopUpload(){
        if (uploadHandle == null)
            return;
        //insertPauseImage();
        uploadHandle.cancel(false);
        uploadHandle = null;
        uploadStatus = UploadStatus.IDLE;
        // Invoke listeners
        invokeListeners();
    }

    public void updateUpload(){
        // Invoke listeners
        invokeListeners();
    }

    public void uploadImages() {

        Log.d(TAG, "Upload service is attempting to access file directory");

        //getting directory of where files are stored and making a list
        String dirString = m_context.getExternalFilesDir(null) + "/screenLife" + "/encrypt";
        File dir = new File(dirString);
        //need to check for null files
        File[] files = dir.listFiles();
        //converting the files into a more iteration friendly datastructure
        LinkedList<File> fileList = new LinkedList<>(Arrays.asList(files));

        //updating our upload status
        uploadStatus = UploadStatus.UPLOADING;
        // Invoke listeners
        invokeListeners();

        Log.d(TAG, "Upload service has found " + fileList.size() +  " files to upload");

        //split our list of files into batches and uploading them
        while (!fileList.isEmpty()) {
            //creating a list of files for a single batch
            List<File> nextBatch = new LinkedList<>();

            //iterating through our list of files adding them to our nextBatch list while
            //ensuring that we do not send to many in one batch and that the file list does not run out
            for (int i = 0; i < Constants.BATCH_SIZE  && !fileList.isEmpty(); i++) {
                nextBatch.add(fileList.remove());
                Log.d(TAG, "Adding file: " + nextBatch.get(nextBatch.size()-1).toString() + "to batch");
            }

            //unsure what client does so leaving this as a method variable for the time being
            OkHttpClient client = new OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build();

            //creating a new batch with our list of files and adding it to our array of batches
            Batch batch = new Batch(nextBatch, client);
            String code = batch.sendFiles();
            if (code.equals("201")) {
                batch.deleteFiles();
            } else {
                Log.d(TAG, "Batch failed to send");
                uploadStatus = UploadStatus.FAILED;
                break;
            }

            if(uploadStatus != UploadStatus.FAILED)
                Log.d(TAG, "Upload service has sent a batch of: " + nextBatch.size());
        }

        //updating uploadStatus
        if(uploadStatus != UploadStatus.FAILED) uploadStatus = UploadStatus.SUCCESS;

        // Invoke listeners
        invokeListeners();
    }

    public UploadStatus getUploadStatus() { return uploadStatus; }

}