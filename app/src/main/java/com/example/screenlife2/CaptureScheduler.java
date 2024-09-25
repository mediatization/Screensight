package com.example.screenlife2;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.Bitmap;
import android.os.Environment;
import android.view.View;
import android.app.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

// Another version of the capture service that uses a ScheduledExecutorService
public class CaptureScheduler {
    private final long captureInterval = 3000;
    private final long pauseDuration = 300000;
    // The scheduler
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);
    // The runnable for the scheduler
    private Runnable captureRunner = null;
    // The handle for the scheduler
    private ScheduledFuture<?> captureHandle = null;
    // The canceller for the scheduler
    private Runnable captureCanceller = null;
    // The pauser for the scheduler
    private Runnable capturePauser = null;

    //
    public void startCapture() {
        if (captureRunner != null)
            return;
        captureRunner = this::takeCapture;
        captureHandle = scheduler.scheduleWithFixedDelay(captureRunner, captureInterval, captureInterval, MILLISECONDS);
        captureCanceller = () -> captureHandle.cancel(false);
        scheduler.schedule(captureCanceller, 1, HOURS);
    }
    //
    public void stopCapture(){
        scheduler.schedule(captureCanceller, 0, MILLISECONDS);
    }
    //
    public void pauseCapture(){
        if (capturePauser != null)
            return;
        scheduler.schedule(captureCanceller, 0, MILLISECONDS);
        capturePauser = this::startCapture;
        scheduler.schedule(capturePauser, pauseDuration, HOURS);
    }
    //
    private void takeCapture() {
        /*
        Date now = new Date();
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);

        try {
            // image naming and path  to include sd card  appending name you choose for file
            String mPath = Environment.getExternalStorageDirectory().toString() + "/" + now + ".jpg";

            // create bitmap screen capture
            View v1 = getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
            v1.setDrawingCacheEnabled(false);

            File imageFile = new File(mPath);

            FileOutputStream outputStream = new FileOutputStream(imageFile);
            int quality = 100;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            outputStream.flush();
            outputStream.close();

        } catch (Throwable e) {
            // Several error may come out with file handling or DOM
            e.printStackTrace();
        }
        */
    }
    //
    public String[] getCaptures()
    {
        return null;
    }
}
