package com.example.screenlife2;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class UploadService extends Service {
    private static final String TAG = "UploadService";
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
        start();
        Log.d(TAG, "Service was bound");
        return new UploadService.LocalBinder();
    }

    public class LocalBinder extends Binder {
        public UploadService getService() {
            return UploadService.this;
        }
    }

    public void start(){uploadScheduler.startUpload();}
    public void stop() {uploadScheduler.stopUpload();}
    public UploadScheduler.UploadStatus getUploadStatus() {return uploadScheduler.getUploadStatus();}
}
