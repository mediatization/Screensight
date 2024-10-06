package com.example.screenlife2;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.KeyguardManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.graphics.Paint;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

// Another version of the capture service that uses a ScheduledExecutorService
public class CaptureScheduler {
    private static final String TAG = "CaptureScheduler";
    public interface CaptureListener{
        void onInvoke(CaptureStatus status, int numCaptured);
    }
    public CaptureScheduler (Context context){
        m_context = context;
    }
    public enum CaptureStatus
    {
        STOPPED,
        PAUSED,
        CAPTURING
    }
    private CaptureStatus m_captureStatus = CaptureStatus.STOPPED;
    private final long m_captureInterval = 3000;
    private final long m_pauseDuration = 300000;
    // The scheduler
    private final ScheduledExecutorService m_scheduler =
            Executors.newScheduledThreadPool(1);
    // The current runnable task of the scheduler
    private ScheduledFuture<?> m_captureHandle = null;
    // The context of the application, needed for certain function calls.
    private Context m_context;
    // The image reader that allows us to take screenshots
    private ImageReader m_imageReader;
    // TODO: FIGURE OUT THIS AND WHERE TO GET IT, ALSO IMAGE READER???
    private KeyguardManager m_keyguardManager;
    private static int m_pixelStride;
    private static int m_rowPadding;
    private final int DISPLAY_WIDTH = 720;
    private final int DISPLAY_HEIGHT = 1280;
    private static final DateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
    private HashSet<CaptureListener> m_onStatusChangedCallbacks = new HashSet<CaptureListener>();

    // Adds listeners when the status changes
    public void addListener(CaptureListener listener){
        m_onStatusChangedCallbacks.add(listener);
    }
    public void clearListeners(){
        m_onStatusChangedCallbacks.clear();
    }
    private void invokeListeners() {
        for(CaptureListener listener : m_onStatusChangedCallbacks)
        {
            listener.onInvoke(m_captureStatus, getNumCaptured());
        }
    }
    // Schedules to start
    public void startCapture() {
        Log.d(TAG, "Starting capture");
        stopCapture();
        insertResumeImage();
        Runnable captureRunner = this::takeCapture;
        m_captureHandle = m_scheduler.scheduleWithFixedDelay(captureRunner, m_captureInterval, m_captureInterval, MILLISECONDS);
        m_captureStatus = CaptureStatus.CAPTURING;
        // Invoke listeners
        invokeListeners();
    }
    // Stops capture and pause
    public void stopCapture(){
        if (m_captureHandle == null)
            return;
        insertPauseImage();
        m_captureHandle.cancel(false);
        m_captureHandle = null;
        m_captureStatus = CaptureStatus.STOPPED;
    }
    // Stops capture and schedules to start
    public void pauseCapture(){
        stopCapture();
        m_captureStatus = CaptureStatus.PAUSED;
        Runnable capturePauser = this::startCapture;
        m_captureHandle = m_scheduler.schedule(capturePauser, m_pauseDuration, MILLISECONDS);
    }
    //
    private void takeCapture() {
        //android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        if (!m_keyguardManager.isKeyguardLocked()) {

            //System.out.println("CAPTURE:INTERVAL:2");

            Image image = m_imageReader.acquireLatestImage();
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            m_pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            m_rowPadding = rowStride - m_pixelStride * DISPLAY_WIDTH;
            image.close();

            Bitmap bitmap = Bitmap.createBitmap(DISPLAY_WIDTH + m_rowPadding / m_pixelStride,
                    DISPLAY_HEIGHT, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            encryptImage(bitmap, "placeholder");
            buffer.rewind();
        }
    }
    private void insertResumeImage()
    {
        //android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        InputStream is = m_context.getResources().openRawResource(R.raw.resumerecord);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        encryptImage(bitmap, "resume");
    }
    private void insertPauseImage()
    {
        //android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        InputStream is = m_context.getResources().openRawResource(R.raw.pauserecord);
        Bitmap bitmap = BitmapFactory.decodeStream(is);
        encryptImage(bitmap, "pause");
    }
    private void encryptImage(Bitmap bitmap, String descriptor) {
        String hash = Settings.getString("hash", "00000000").substring(0, 8);
        String keyRaw = Settings.getString("key", "");
        byte[] key = Converter.hexStringToByteArray(keyRaw);
        FileOutputStream fos = null;
        Date date = new Date();
        String dir = m_context.getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/screenLife";
        String dir2 = dir + "/images";
        String dir3 = dir + "/encrypt";
        String screenshot = "/" + hash + "_" + sdf.format(date) + "_" + descriptor + ".png";

        try {
            if (keyRaw != "") {
                // LOOK FOR screenLife DIRECTORY
                Settings.findOrCreateDirectory(dir);
                // LOOK FOR images DIRECTORY
                Settings.findOrCreateDirectory(dir2);
                // LOOK FOR encrypt DIRECTORY
                Settings.findOrCreateDirectory(dir3);
                // Create the file output stream
                fos = new FileOutputStream(dir2 + screenshot);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
                try {
                    Encryptor.encryptFile(key, screenshot, dir2 + screenshot, dir3 + screenshot);
                    Log.i("SLAZ", "Encryption done with key " + Arrays.toString(key));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                File f = new File(dir2 + screenshot);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            bitmap.recycle();
        }
    }
    //
    public File[] getCaptures()
    {
        String dir = m_context.getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/images";
        File directory = new File(dir);
        FileFilter filter = new FileFilter()
        {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith(".png");
            }
        };
        return directory.listFiles(filter);
    }
    public int getNumCaptured(){
        return getCaptures().length;
    }
    public CaptureStatus getCaptureStatus() { return m_captureStatus; }
}
