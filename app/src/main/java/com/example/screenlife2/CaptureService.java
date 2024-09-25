package com.example.screenlife2;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

// A Service allows us to run functions in the background while the app is minimized.
// We are using a bound service, which acts almost like a server.
// The Service is bound to an app, the one running in the background
// When the app is terminated, the Service is as well
// The app can send messages and receive answers from the service
public class CaptureService extends Service {

    // The scheduler used to take screenshots
    private CaptureScheduler captureScheduler = null;

    // Required function called when service is bound to an app
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    // PLACEHOLDER
    @Override
    public void onCreate(){
        captureScheduler = new CaptureScheduler();
        captureScheduler.startCapture();
    }
    // PLACEHOLDER
    @Override
    public void onDestroy(){
        captureScheduler.stopCapture();
    }
    /** Methods for the client */
    // Pauses capturing
    public void pause(){
        captureScheduler.pauseCapture();
    }
    // Returns captured files
    public String[] getFiles(){
        return captureScheduler.getCaptures();
    }
}