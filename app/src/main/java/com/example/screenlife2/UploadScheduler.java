package com.example.screenlife2;

import static androidx.core.content.ContextCompat.getSystemService;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
        void onInvoke(UploadScheduler.UploadStatus uploadStatus, UploadScheduler.UploadResult uploadResult);
    }
    public enum UploadStatus
    {
        IDLE,
        UPLOADING
    }
    public enum UploadResult
    {
        NO_UPLOADS,
        SUCCESS,
        WIFI_FAILURE,
        NETWORK_FAILURE
    }
    private UploadStatus uploadStatus = UploadStatus.IDLE;
    private UploadResult uploadResult = UploadResult.NO_UPLOADS;

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
            listener.onInvoke(uploadStatus, uploadResult);
        }
    }

    //stolen directly from stack overflow
    //if it does not work its not my fault :)
    private boolean checkWifiOnAndConnected() {
        WifiManager wifiMgr = (WifiManager) m_context.getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();

            // Checking if connected to an access point
            return wifiInfo.getNetworkId() != -1;
        }
        else {
            return false; // Wi-Fi adapter is OFF
        }
    }

    //will upload all files currently in directory
    public void startUpload() {
        stopUpload();

        //first argument reads from the settings whether or not the user currently has uploading
        //with cellular enabled, second argument checks whether or not user is currently connected
        //to wifi

        Log.d(TAG, "whether or not currently connected to wifi: " + checkWifiOnAndConnected());

        // Wifi is required and we are not connected
        if(!Boolean.parseBoolean(Settings.getString("useCellular", "")) &&!checkWifiOnAndConnected()) {
            // FAIL
            uploadResult = UploadResult.WIFI_FAILURE;
            // Invoke listeners
            invokeListeners();
            return;
        }

        Runnable uploadRunner = this::uploadImages;
        Log.d(TAG, "Upload scheduler is attempting upload");
        uploadHandle = scheduler.schedule(uploadRunner, 0, TimeUnit.MINUTES);
        uploadStatus = UploadStatus.UPLOADING;
        // Invoke listeners
        invokeListeners();
    }

    // Stops uploading of images
    public void stopUpload(){
        if (uploadHandle == null)
            return;
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
        //getting directory of where files are stored and making a list
        String dirString = m_context.getExternalFilesDir(null) + "/screenLife" + "/encrypt";
        File dir = new File(dirString);
        //need to check for null files
        File[] files = dir.listFiles();
        //converting the files into a more iteration friendly datastructure
        //i dont know if list is optimal or how much performance we losing using a list
        LinkedList<File> fileList = new LinkedList<>(Arrays.asList(files));
        Log.d(TAG, "Found " + fileList.size() +  " files to upload");

        //unsure what client does so leaving this as a method variable for the time being
        OkHttpClient client = new OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build();

        //split our list of files into batches and uploading them
        while (!fileList.isEmpty()) {

            //creating a new batch with our list of files
            Batch batch = new Batch(client);

            //iterating through our list of files adding them to our nextBatch list while
            //ensuring that we do not send to many in one batch and that the file list does not run out
            for (int i = 0; i < Constants.BATCH_SIZE  && !fileList.isEmpty(); i++) {
                File file = fileList.remove();
                batch.addFile(file);
                Log.d(TAG, "Adding file: " + file.toString() + "to batch");
            }

            if (!batch.sendFiles()) {
                //on failure notify user and stop the loop
                uploadResult = UploadResult.NETWORK_FAILURE;
                break;
            }
        }

        //updating uploadStatus
        if(uploadResult != UploadResult.NETWORK_FAILURE) uploadResult = UploadResult.SUCCESS;

        //stopping upload
        //no need to invoke listeners as stopUpload does that for us
        this.stopUpload();
    }

    public UploadStatus getUploadStatus() {return uploadStatus;}
    public UploadResult getUploadResult() {return uploadResult;}
    //function for checking whether or not there is already an upload in progress,
    //and if the user requires wifi connectivity to upload whether or not they are currently
    //connected to wifi
    public boolean ableToUpload() {
        boolean isCurrentlyIdle = this.uploadStatus == UploadStatus.IDLE;

        if(Boolean.parseBoolean(Settings.getString("useCellular", ""))) {
            return isCurrentlyIdle;
        }

        return checkWifiOnAndConnected() && isCurrentlyIdle;
    }
}