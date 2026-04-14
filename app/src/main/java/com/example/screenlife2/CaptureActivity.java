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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.File;
import java.util.Objects;

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

    /** Manual pause tracking */
    private boolean m_manualPause = false;

    /** Service Members*/
    private CaptureService m_captureService = null;
    private final ServiceConnection captureServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            CaptureService.LocalBinder localBinder = (CaptureService.LocalBinder) iBinder;
            m_captureService = localBinder.getService();
            m_captureService.addCaptureListener(this::updateCaptureStatus);
            // Only start the capture service when it is active
            if (m_captureService != null && !m_captureService.Initialized && m_isActivityVisible) {
                // Start the capturing
                m_captureService.startCapture();
                Log.d(TAG, "Capture Service Started On Connection");
            }
            // Add the on-click events to the UI
            m_startStopCaptureButton.setOnClickListener((View view) -> {

                if (Objects.requireNonNull(m_captureService.getCaptureStatus()) == CaptureScheduler.CaptureStatus.STOPPED) {
                    m_captureService.startCapture();
                    m_manualPause = false;
                } else {
                    m_captureService.stopCapture(true);
                    m_manualPause = true;
                }
                
                // Persist the manual pause state
                Settings.setString("manualPause", String.valueOf(m_manualPause));
                Settings.save();
            });

            m_uploadButton.setOnClickListener((View view) ->
            {
                if(m_captureService.getUploadStatus() != UploadScheduler.UploadStatus.UPLOADING) {
                    m_captureService.startUpload();
                }
                else {
                    m_captureService.stopUpload();
                }
            });

            // Observe WorkManager for real-time status updates
            WorkManager.getInstance(CaptureActivity.this).getWorkInfosForUniqueWorkLiveData("UploadWork")
                    .observe(CaptureActivity.this, workInfos -> {
                        if (workInfos != null && !workInfos.isEmpty()) {
                            WorkInfo workInfo = workInfos.get(0);
                            updateWorkManagerUI(workInfo);
                        }
                    });
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            m_captureService = null;
        }

        public void updateCaptureStatus(CaptureScheduler.CaptureStatus status, int numCaptured) {
            runOnUiThread(() -> {

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
                        Log.d(TAG, "Error: unknown status");
                        break;
                }
                // Disable the black out panel
                m_blackOutPanel.setVisibility(View.INVISIBLE);
            });
        }
        
        private void updateWorkManagerUI(WorkInfo workInfo) {
            if (!m_isActivityVisible) return;

            WorkInfo.State state = workInfo.getState();
            Log.d(TAG, "WorkManager state: " + state);

            if (state == WorkInfo.State.RUNNING) {
                m_uploadStatusDisplay.setText("UPLOADING");
                m_uploadStatusDisplay.setTextColor(Color.GREEN);
                m_uploadButton.setText("STOP UPLOAD");
                m_uploadResultLabel.setText("Uploading files...");
            } else if (state == WorkInfo.State.ENQUEUED) {
                m_uploadStatusDisplay.setText("PENDING");
                m_uploadStatusDisplay.setTextColor(Color.BLUE);
                m_uploadButton.setText("STOP UPLOAD");
                m_uploadResultLabel.setText("Waiting for network conditions...");
            } else {
                m_uploadStatusDisplay.setText("IDLE");
                m_uploadStatusDisplay.setTextColor(Color.BLACK);
                m_uploadButton.setText("START UPLOAD");
                if (state == WorkInfo.State.SUCCEEDED) {
                    m_uploadResultLabel.setText("Last upload successful.");
                } else if (state == WorkInfo.State.FAILED) {
                    m_uploadResultLabel.setText("Last upload failed.");
                }
            }
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
        
        // Create
        super.onCreate(savedInstanceState);
        // Load the UI layout from res/layout/activity_main.xml
        setContentView(R.layout.activity_main);

        // Ensure settings are loaded
        if (Settings.JSON() == null) {
            File directory = new File(getExternalFilesDir(null), "/screenlife");
            File jsonFile = new File(directory, "settings.JSON");
            if (jsonFile.exists()) {
                Settings.load(jsonFile);
            }
        }
        m_manualPause = Boolean.parseBoolean(Settings.getString("manualPause", "false"));

        // Request media projection ONLY if accessibility isn't enabled
        if (!AccessibilityUtil.isAccessibilityServiceEnabled(this, CaptureAccessibilityService.class)) {
            m_projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startMediaProjectionRequest();
        } else {
            // Accessibility is enabled, so we don't need MediaProjection.
            initializeService(null);
        }

        // Log message indicating that the UI is being displayed
        Log.d(TAG, "Activity Created");

        //setting up broadcast receiver for phone waking up/going to sleep
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, filter, Context.RECEIVER_EXPORTED);
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
                if (!m_manualPause) {
                    m_captureService.startCapture();
                }
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
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // Permission granted
                    Toast.makeText(CaptureActivity.this, "Media Projection Permission Granted", Toast.LENGTH_SHORT).show();
                    // Now you can start the screen capture
                    initializeService(result);
                } else {
                    // Permission denied
                    Toast.makeText(CaptureActivity.this, "Permission Denied. Closing...", Toast.LENGTH_SHORT).show();
                    // Close the app
                    closeApp();
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
        // Close the app
        // Closes the app and all activities in the task
        new Handler().postDelayed(this::finishAffinity, 2000); // 2-second delay to allow the user to read the message
    }

    public void initializeService(@Nullable ActivityResult result)
    {
        // Set up UI
        m_blackOutPanel = findViewById(R.id.m_blackOutPanel);
        m_startStopCaptureButton = findViewById(R.id.m_startStopCaptureButton);
        m_captureStatusDisplay = findViewById(R.id.m_captureStatusDisplay);
        m_captureNumberLabel = findViewById(R.id.m_captureNumberLabel);
        m_captureSizeLabel = findViewById(R.id.m_captureSizeLabel);
        m_uploadButton = findViewById(R.id.m_uploadButton);
        m_uploadStatusDisplay = findViewById(R.id.m_uploadStatusDisplay);
        m_uploadResultLabel = findViewById(R.id.m_uploadResultLabel);
        // Get screen density
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenDensity = metrics.densityDpi;
        // Set up the capture service
        Intent screenCaptureIntent = new Intent(CaptureActivity.this, CaptureService.class);
        if (result != null) {
            screenCaptureIntent.putExtra("resultCode", result.getResultCode());
            screenCaptureIntent.putExtra("intentData", result.getData());
        } else {
            screenCaptureIntent.putExtra("resultCode", -1);
        }
        screenCaptureIntent.putExtra("screenDensity", screenDensity);
        
        // Android 8.0+ simplified call
        Log.d(TAG, "Calling startForegroundService");
        startForegroundService(screenCaptureIntent);

        // Bind the service
        bindService(screenCaptureIntent, captureServiceConnection, Context.BIND_AUTO_CREATE);

        Log.d(TAG, "Capture Service was bound");
    }
}
