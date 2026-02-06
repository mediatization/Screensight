package com.example.screenlife2;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class CaptureActivity extends AppCompatActivity {
    private boolean m_isActivityVisible = false;
    private static final String TAG = "MainActivity";
    public MediaProjectionManager m_projectionManager;
    /** UI Members */
    private ImageView m_blackOutPanel;
    private Button m_startStopCaptureButton;
    private TextView m_captureStatusDisplay;
    private TextView m_captureNumberLabel;
    private TextView m_captureSizeLabel;
    private Button m_uploadButton;
    private TextView m_uploadStatusDisplay;
    private TextView m_uploadResultLabel;

    /** Service Members*/
    private CaptureService m_captureService = null;
    private final ServiceConnection captureServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            CaptureService.LocalBinder localBinder = (CaptureService.LocalBinder) iBinder;
            m_captureService = localBinder.getService();
            m_captureService.addCaptureListener(this::updateCaptureStatus);
            Log.d(TAG, "Capture Service Connected");
            // Only start the capture service when it is active
            if (m_captureService != null && !m_captureService.Initialized && m_isActivityVisible) {
                // Start the capturing
                m_captureService.startCapture();
                Log.d(TAG, "Capture Service Started On Connection");
            }
            // Add the on-click events to the UI
            m_startStopCaptureButton.setOnClickListener((View view) -> {

                switch (m_captureService.getCaptureStatus()) {
                    case STOPPED:
                        m_captureService.startCapture();
                        break;
                    case CAPTURING:
                        m_captureService.stopCapture(true);
                        break;
                    default:
                        m_captureService.stopCapture(true);
                        break;
                }

            });
            // Upload stuff
            m_captureService.addUploadListener(this::updateUploadUI);
            Log.d(TAG, "Upload Service Connected");
            // Update the UI for the upload
            if (m_captureService != null && m_isActivityVisible) {
                m_captureService.updateUpload();
            }
            m_uploadButton.setOnClickListener((View view) ->
            {
                if(m_captureService.getUploadStatus() != UploadScheduler.UploadStatus.UPLOADING) {
                    m_captureService.startUpload();
                }
                else {
                    m_captureService.stopUpload();
                }
            });
            Log.d(TAG, "UI On clicks were added");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            m_captureService = null;
        }

        public void updateCaptureStatus(CaptureScheduler.CaptureStatus status, int numCaptured) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Updating capture status to " + status.toString());
                    Log.d(TAG, "Is activity active? " + Boolean.toString(m_isActivityVisible));

                    if (!m_isActivityVisible)
                        return;

                    // Update file size label
                    m_captureStatusDisplay.setText(status.toString());
                    float fileSize = m_captureService.getSizeCapturedKB();
                    if (fileSize > 1024 * 1024 * 1024){
                        m_captureSizeLabel.setText( getString(R.string.file_size_tb, fileSize / 1024 / 1024 / 1024));
                    }
                    else if (fileSize > 1024 * 1024){
                        m_captureSizeLabel.setText( getString(R.string.file_size_gb, fileSize / 1024 / 1024));
                    }
                    else if (fileSize > 1024) {
                        m_captureSizeLabel.setText( getString(R.string.file_size_mb, fileSize / 1024));
                    }
                    else{
                        m_captureSizeLabel.setText( getString(R.string.file_size_kb, fileSize));
                    }

                    Log.d(TAG, "TEXT: " +  m_captureSizeLabel.getText().toString());

                    // Update file number label
                    m_captureNumberLabel.setText(getString(R.string.file_count, numCaptured));


                    switch (status) {
                        case CAPTURING:
                            m_captureStatusDisplay.setTextColor(Color.GREEN);
                            m_startStopCaptureButton.setText("STOP CAPTURE");
                            break;
                        case STOPPED:
                            m_captureStatusDisplay.setTextColor(Color.RED);
                            m_startStopCaptureButton.setText("START CAPTURE");
                            break;
                        default:
                            Log.d(TAG, "UC ERROR ERROR ERROR!!!");
                            break;
                    }
                    // Disable the black out panel
                    m_blackOutPanel.setVisibility(View.INVISIBLE);
                }
            });
        }
        public void updateUploadUI(UploadScheduler.UploadStatus uploadStatus, UploadScheduler.UploadResult uploadResult, UploadScheduler.UploadData uploadData) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Is activity active? " + Boolean.toString(m_isActivityVisible));

                    if (!m_isActivityVisible)
                        return;

                    Log.d(TAG, "Updating upload status to " + uploadStatus.toString());
                    m_uploadStatusDisplay.setText(uploadStatus.toString());

                    switch (uploadStatus) {
                        case IDLE:
                            m_uploadStatusDisplay.setTextColor(Color.BLACK);
                            m_uploadButton.setText("START UPLOAD");
                            m_uploadButton.setVisibility(View.VISIBLE);
                            // Update the upload result
                            switch (uploadResult) {
                                case NO_UPLOADS:
                                    // Not uploading any files
                                    m_uploadResultLabel.setText(getString(R.string.upload_no_uploads));
                                    break;
                                case SUCCESS:
                                    // Uploaded files
                                    m_uploadResultLabel.setText(getString(R.string.upload_success, uploadData.FilesMax, uploadData.BatchesMax));
                                    break;
                                case WIFI_FAILURE:
                                    // Wifi failed trying to upload files
                                    m_uploadResultLabel.setText(getString(R.string.upload_wifi_failure, uploadData.FilesMax, uploadData.BatchesMax));
                                    break;
                                case NETWORK_FAILURE:
                                    // Network failed tyring to upload files
                                    m_uploadResultLabel.setText(getString(R.string.upload_network_failure, uploadData.FilesMax, uploadData.BatchesMax));
                                    break;
                            }
                            break;
                        case UPLOADING:
                            m_uploadStatusDisplay.setTextColor(Color.GREEN);
                            m_uploadButton.setText("STOP UPLOAD");
                            m_uploadButton.setVisibility(View.VISIBLE);
                            // Update the upload result be pending
                            m_uploadResultLabel.setText(getString(R.string.upload_uploading, uploadData.FilesCurrent, uploadData.FilesMax, uploadData.BatchesCurrent, uploadData.BatchesMax));
                            break;
                    }

                    Log.d(TAG, "Updating upload result to " + uploadResult.toString());
                }
            });
        }
    };

    @Override
    protected void onStart(){
        super.onStart();
        m_isActivityVisible = true; // Activity is visible
        Log.d(TAG, "Activity Started");
    }
    @Override
    protected void onResume() {
        super.onResume();
        m_isActivityVisible = true; // Activity is visible
        Log.d(TAG, "Activity Resumed");
        // Only start the capture service when it is active
        if (m_captureService != null && !m_captureService.Initialized)
        {
            // Start the capturing
            m_captureService.startCapture();
            Log.d(TAG, "Capture Service Started On Resume");
        }
        else if (m_captureService != null) {
            m_captureService.updateCapture();
            m_captureService.updateUpload();
        }
        else{
            Log.d(TAG, "Capture service is null");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        m_isActivityVisible = false; // Activity is not visible
        Log.d(TAG, "Activity Paused");
    }

    @Override
    protected  void onStop(){
        super.onStop();
        m_isActivityVisible = false; // Activity is not visible
        Log.d(TAG, "Activity Stopped");
    }

    @Override
    protected void onDestroy(){
        getApplicationContext().stopService(new Intent(CaptureActivity.this, CaptureService.class));
        super.onDestroy();
        Log.d(TAG, "Activity Destroyed");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Request notifications
        startNotificationRequest();
        // Request media projection if accessibility isn't enabled
        if (!AccessibilityUtil.isAccessibilityServiceEnabled(this, MyAccessibilityService.class)) {
            m_projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startMediaProjectionRequest();
        }
        // Create
        super.onCreate(savedInstanceState);
        // Load the UI layout from res/layout/activity_main.xml
        setContentView(R.layout.activity_main);

        // Log message indicating that the UI is being displayed
        Log.d(TAG, "Activity Created");

        //setting up broadcast receiver for phone waking up/going to sleep
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, filter);
    }

    //whenever the phone goes to sleep/wakes up our activity takes notice
    private BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            //the app in some scenarios may attempt to continue capturing screenshots
            //even when the phone is asleep, so as best practice we stop the capture service
            //whenever the phone is put to sleep
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    m_captureService.stopCapture(false);

            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                m_captureService.startCapture();
            }

        }
    };

    // Register a launcher for the permission request
    private final ActivityResultLauncher<String> requestNotificationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted
                    Toast.makeText(CaptureActivity.this, "Notification Permission Granted", Toast.LENGTH_SHORT).show();
                } else {
                    // Permission denied
                    Toast.makeText(CaptureActivity.this, "Permission Denied. Closing...", Toast.LENGTH_SHORT).show();
                    // Close the app
                    closeApp();
                }
            });

    // Register a launcher for the permission request
    private final ActivityResultLauncher<Intent> requestMediaProjectionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Permission granted
                        Toast.makeText(CaptureActivity.this, "Media Projection Permission Granted", Toast.LENGTH_SHORT).show();
                        // Now you can start the screen capture
                        onResult(result, getIntent());
                    } else {
                        // Permission denied
                        Toast.makeText(CaptureActivity.this, "Permission Denied. Closing...", Toast.LENGTH_SHORT).show();
                        // Close the app
                        closeApp();
                    }
                }
            });


    private void startNotificationRequest() {
        // Only required for Android 13 (API level 33) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Permission is already granted
                Toast.makeText(CaptureActivity.this, "Notification Permission Already Granted", Toast.LENGTH_SHORT).show();
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                // Show rationale and request permission
                Toast.makeText(CaptureActivity.this, "Notification Permission is required for showing notifications.", Toast.LENGTH_SHORT).show();
                requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Request the permission directly
                requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void startMediaProjectionRequest() {
        // Create an intent for the media projection permission
        if (m_projectionManager != null) {
            Intent intent;
            // Ask specifically for full screen projecting
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                intent = m_projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            }
            else{
                intent = m_projectionManager.createScreenCaptureIntent();
            }
            // Launch the intent using the ActivityResultLauncher
            requestMediaProjectionLauncher.launch(intent);
        }
    }

    private void closeApp(){
        // Delay the closing to let the user see the message
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Close the app
                finishAffinity(); // Closes the app and all activities in the task
            }
        }, 2000); // 2-second delay to allow the user to read the message
    }

    public void onResult(ActivityResult result, Intent intent)
    {
        Log.d(TAG, "Calling onResult");
        // Set up UI
        m_blackOutPanel = findViewById(R.id.m_blackOutPanel);
        m_startStopCaptureButton = findViewById(R.id.m_startStopCaptureButton);
        m_captureStatusDisplay = findViewById(R.id.m_captureStatusDisplay);
        m_captureNumberLabel = findViewById(R.id.m_captureNumberLabel);
        m_captureSizeLabel = findViewById(R.id.m_captureSizeLabel);
        m_uploadButton = findViewById(R.id.m_uploadButton);
        m_uploadStatusDisplay = findViewById(R.id.m_uploadStatusDisplay);
        m_uploadResultLabel = findViewById(R.id.m_uploadResultLabel);
        Log.d(TAG, "UI was hooked up");
        // Get screen density
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenDensity = metrics.densityDpi;
        // Set up the capture service
        Intent screenCaptureIntent = new Intent(CaptureActivity.this, CaptureService.class);
        screenCaptureIntent.putExtra("resultCode", result.getResultCode());
        screenCaptureIntent.putExtra("intentData", result.getData());
        screenCaptureIntent.putExtra("screenDensity", screenDensity);
        // Start the service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Calling startForegroundService");
            startForegroundService(screenCaptureIntent);
        }
        // Bind the service
        bindService(screenCaptureIntent, captureServiceConnection, Context.BIND_AUTO_CREATE);

        Log.d(TAG, "Capture Service was bound");
    }
}