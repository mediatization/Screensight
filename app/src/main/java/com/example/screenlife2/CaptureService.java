package com.example.screenlife2;

import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Method;

// A Service allows us to run functions in the background while the app is minimized.
// We are using a bound service, which acts almost like a server.
// The Service is bound to an app, the one running in the background
// When the app is terminated, the Service is as well
// The app can send messages and receive answers from the service
public class CaptureService extends Service {

    // The scheduler used to take screenshots
    private CaptureScheduler captureScheduler = null;
    // PLACEHOLDER
    @Override
    public void onCreate(){
        // Do nothing
    }
    // Required function for Services to bind
    @Override
    public IBinder onBind(Intent intent) {
        Settings settings = (Settings)intent.getSerializableExtra("settings");
        if (captureScheduler == null)
            captureScheduler = new CaptureScheduler(getApplicationContext(), settings);
        start();
        return new LocalBinder();
    }
    public class LocalBinder extends Binder {
        public CaptureService getService() {
            return CaptureService.this;
        }
    }

    // PLACEHOLDER
    @Override
    public void onDestroy(){
        captureScheduler.stopCapture();
    }
    /** Methods for the client */

    //
    public void addCaptureListener(CaptureScheduler.CaptureListener listener){
        captureScheduler.addListener(listener);
    }
    //
    public void clearCaptureListeners(){
        captureScheduler.clearListeners();
    }
    // Starts capturing
    public void start(){
        captureScheduler.startCapture();
    }
    // Pauses capturing
    public void pause(){
        captureScheduler.pauseCapture();
    }
    // Pauses capturing
    public void stop(){
        captureScheduler.stopCapture();
    }
    // Returns captured files
    public File[] getFiles(){
        return captureScheduler.getCaptures();
    }
    // Returns the number of captured files
    public int getNumCaptured() {return captureScheduler.getNumCaptured();}
    // Returns the capture status
    public CaptureScheduler.CaptureStatus getCaptureStatus() { return captureScheduler.getCaptureStatus();}
}