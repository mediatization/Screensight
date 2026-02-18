package com.example.screenlife2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

//not currently used, exists as a fallback in case having a scheduler manage a scheduler causes problems
public class UploadWorker extends Worker {

    public static class UploadData
    {
        public int BatchesCurrent;
        public int BatchesMax;
        public int FilesCurrent;
        public int FilesMax;
        public UploadData (int bCurrent, int bMax, int fCurrent, int fMax)
        {
            BatchesCurrent = bCurrent;
            BatchesMax = bMax;
            FilesCurrent = fCurrent;
            FilesMax = fMax;
        }
    }

    private UploadScheduler.UploadData uploadData = new UploadScheduler.UploadData(0, 0, 0, 0);

    private final Context m_context;

    private static final String TAG = "UploadWorker";

    /* Wifi Connectivity Monitoring
    don't entirely understand but based off of following stack overflow post
    https://stackoverflow.com/questions/54527301/connectivitymanager-networkcallback-onavailablenetwork-network-method-is */
    private boolean connectedToWifi = false;
    private final NetworkRequest networkRequest = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network){
            super.onAvailable(network);
            connectedToWifi = true;
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            connectedToWifi = false;
        }
    };
    private final ConnectivityManager connectivityManager;

    public UploadWorker(Context context, WorkerParameters params) {
        super(context, params);

        m_context = context;
        connectivityManager = (ConnectivityManager) m_context.getSystemService(Context.CONNECTIVITY_SERVICE);
        //start monitoring wifi connection
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    @NonNull
    @Override
    public Result doWork() {

        //If Wifi is required and we are not connected
        Log.d(TAG, "Are we connected to wifi: " + connectedToWifi);
        Log.d(TAG, "are we allowed to use cellular: " + Boolean.parseBoolean(Settings.getString("useCellular", "")));
        if(!Boolean.parseBoolean(Settings.getString("useCellular", "")) && !connectedToWifi) {
            return Result.failure();
        }

        Log.d(TAG, "attempting upload");

        //getting directory of where files are stored and making a list
        String dirString = m_context.getExternalFilesDir(null) + "/screenLife" + "/encrypt";
        File dir = new File(dirString);
        File[] files = dir.listFiles();

        if (files == null) {
            Log.d(TAG, "Could not find files");
            return Result.failure();
        }

        //converting the files into a more iteration friendly datastructure
        //i don't know if list is optimal or how much performance we losing using a list
        LinkedList<File> fileList = new LinkedList<>(Arrays.asList(files));
        // update the upload data
        uploadData.BatchesCurrent = 0;
        uploadData.BatchesMax = (int) Math.ceil((float)fileList.size() / Constants.BATCH_SIZE);
        uploadData.FilesCurrent = 0;
        uploadData.FilesMax = fileList.size();

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
            }
            // Increment the current batches and files
            uploadData.BatchesCurrent++;
            uploadData.FilesCurrent = uploadData.FilesMax - fileList.size();


            if (!batch.sendFiles()) {
                //on failure notify user and stop the loop
                Log.d(TAG, "Network Failure");
                return Result.failure();
            }
        }

        return Result.success();
    }
}
