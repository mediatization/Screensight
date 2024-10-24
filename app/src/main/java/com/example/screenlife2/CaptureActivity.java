package com.example.screenlife2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
    private static final int AUTO_UPLOAD_COUNT = 500;
    public MediaProjectionManager m_projectionManager;
    /** UI Members */
    private ImageView m_blackOutPanel;
    private Button m_startStopCaptureButton;
    private Button m_resumePauseCaptureButton;
    private Button m_uploadButton;
    private TextView m_captureStatusText;
    private TextView m_numCapturedFilesText;
    private TextView m_uploadStatusText;
    private TextView m_uploadResultText;

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
            m_startStopCaptureButton.setOnClickListener((View view) ->
            {
                if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.STOPPED) {
                    m_captureService.startCapture();
                } else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.CAPTURING) {
                    m_captureService.stopCapture();
                } else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.PAUSED) {
                    m_captureService.stopCapture();
                }
            });
            m_resumePauseCaptureButton.setOnClickListener((View view) ->
            {
                if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.PAUSED) {
                    m_captureService.startCapture();
                } else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.CAPTURING) {
                    m_captureService.pauseCapture();
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

                    m_captureStatusText.setText(status.toString());
                    m_numCapturedFilesText.setText(Integer.toString(numCaptured));

                    switch (status) {
                        case CAPTURING:
                            m_captureStatusText.setTextColor(Color.GREEN);
                            m_startStopCaptureButton.setText("STOP CAPTURE");
                            m_resumePauseCaptureButton.setText("PAUSE CAPTURE");
                            m_resumePauseCaptureButton.setVisibility(View.VISIBLE);
                            //Seeing if conditions are met for an auto upload to start
                            //
                            if (numCaptured >= Constants.AUTO_UPLOAD_COUNT && m_captureService.ableToUpload()){
                                m_captureService.startUpload();
                            }
                            break;
                        case STOPPED:
                            m_captureStatusText.setTextColor(Color.RED);
                            m_startStopCaptureButton.setText("START CAPTURE");
                            m_resumePauseCaptureButton.setText("CAN'T PAUSE/RESUME");
                            m_resumePauseCaptureButton.setVisibility(View.INVISIBLE);
                            break;
                        case PAUSED:
                            m_captureStatusText.setTextColor(Color.YELLOW);
                            m_startStopCaptureButton.setText("STOP CAPTURE");
                            m_resumePauseCaptureButton.setText("RESUME CAPTURE");
                            m_resumePauseCaptureButton.setVisibility(View.VISIBLE);
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
        public void updateUploadUI(UploadScheduler.UploadStatus uploadStatus, UploadScheduler.UploadResult uploadResult) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Is activity active? " + Boolean.toString(m_isActivityVisible));

                    if (!m_isActivityVisible)
                        return;

                    Log.d(TAG, "Updating upload status to " + uploadStatus.toString());
                    m_uploadStatusText.setText(uploadStatus.toString());

                    switch (uploadStatus) {
                        case IDLE:
                            m_uploadStatusText.setTextColor(Color.BLACK);
                            m_uploadButton.setText("START UPLOAD");
                            m_uploadButton.setVisibility(View.VISIBLE);
                            break;
                        case UPLOADING:
                            m_uploadStatusText.setTextColor(Color.GREEN);
                            m_uploadButton.setText("STOP UPLOAD");
                            m_uploadButton.setVisibility(View.VISIBLE);
                            break;
                    }

                    Log.d(TAG, "Updating upload result to " + uploadResult.toString());
                    m_uploadResultText.setText(uploadResult.toString());
                    switch (uploadResult) {
                        case NO_UPLOADS:
                            m_uploadResultText.setVisibility(View.INVISIBLE);
                            break;
                        default:
                            m_uploadResultText.setVisibility(View.VISIBLE);
                            break;
                    }
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
        // Request media projection
        m_projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startMediaProjectionRequest();
        // Create
        super.onCreate(savedInstanceState);
        // Load the UI layout from res/layout/activity_main.xml
        setContentView(R.layout.activity_main);

        // Log message indicating that the UI is being displayed
        Log.d(TAG, "Activity Created");
    }

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
            Intent intent = null;
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
        m_resumePauseCaptureButton = findViewById(R.id.m_resumePauseCaptureButton);
        m_uploadButton = findViewById(R.id.m_uploadButton);
        m_captureStatusText = findViewById(R.id.m_captureStatusText);
        m_numCapturedFilesText = findViewById(R.id.m_numCapturedFilesText);
        m_uploadStatusText = findViewById(R.id.m_uploadStatusText);
        m_uploadResultText = findViewById(R.id.m_uploadResultText);
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