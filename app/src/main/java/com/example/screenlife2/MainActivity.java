package com.example.screenlife2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.Manifest;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_MEDIA = 1000;
    public MediaProjectionManager m_projectionManager;
    /** UI Members */
    private Button m_startStopCaptureButton;
    private Button m_resumePauseCaptureButton;
    private Button m_uploadButton;
    private TextView m_captureStatusText;
    private TextView m_numCapturedFilesText;
    private TextView m_uploadStatusText;
    private EditText m_userKeyTextInput;
    private Button m_userKeySubmitButton;
    private Button m_userKeyEditButton;
    private TextView m_userKeyText;
    private ToggleButton m_settingUseCellularButton;

    /** Service Members*/
    private CaptureService m_captureService = null;
    private final ServiceConnection captureServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            CaptureService.LocalBinder localBinder = (CaptureService.LocalBinder) iBinder;
            m_captureService = localBinder.getService();
            m_captureService.addCaptureListener(this::updateCaptureStatus);
            Log.d(TAG, "Service Connected");
            // Add the on-click events to the UI
            m_startStopCaptureButton.setOnClickListener((View view) ->
            {
                if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.STOPPED) {
                    m_captureService.start();
                }
                else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.CAPTURING) {
                    m_captureService.stop();
                }
                else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.PAUSED) {
                    m_captureService.stop();
                }
            });
            m_resumePauseCaptureButton.setOnClickListener((View view) ->
            {
                if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.PAUSED) {
                    m_captureService.start();
                }
                else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.CAPTURING) {
                    m_captureService.pause();
                }
            });
            m_userKeySubmitButton.setOnClickListener((View view) ->
            {
                if(m_userKeyTextInput.length() == 8 && m_userKeyTextInput.getText() != null) {
                    // Overwrite the hash and key in the settings
                    // TODO: DETERMINE CORRECT "KEY" VALUE
                    Settings.setString("hash", m_userKeyTextInput.getText().toString());
                    Settings.setString("key", "00000000000000000000000000000000");
                    Settings.save();
                    m_userKeyText.setText(m_userKeyTextInput.getText().toString());
                    m_userKeyTextInput.setActivated(false);
                    m_userKeySubmitButton.setActivated(false);
                    m_userKeyEditButton.setActivated(true);
                }
            });
            m_userKeyEditButton.setOnClickListener((View view) ->
            {
                m_userKeyTextInput.setActivated(true);
                m_userKeySubmitButton.setActivated(true);
                m_userKeyEditButton.setActivated(false);
            });
            m_settingUseCellularButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Settings.setString("useCellular", Boolean.toString(isChecked));
                }
            });
            Log.d(TAG, "UI On clicks were added");
            // Start the capturing
            m_captureService.start();
            Log.d(TAG, "Capture Service was started");
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            m_captureService = null;
        }

        public void updateCaptureStatus(CaptureScheduler.CaptureStatus status, int numCaptured)
        {
            Log.d(TAG, "Updating capture status to " + status.toString());
            m_captureStatusText.setText(status.toString());
            m_numCapturedFilesText.setText(Integer.toString(numCaptured));
            switch(status)
            {
                case CAPTURING:
                    m_captureStatusText.setTextColor(Color.GREEN);
                    m_startStopCaptureButton.setText("STOP CAPTURE");
                    m_resumePauseCaptureButton.setText("PAUSE CAPTURE");
                    m_resumePauseCaptureButton.setActivated(true);
                    break;
                case STOPPED:
                    m_captureStatusText.setTextColor(Color.RED);
                    m_startStopCaptureButton.setText("START CAPTURE");
                    m_resumePauseCaptureButton.setText("CAN'T PAUSE/RESUME");
                    m_resumePauseCaptureButton.setActivated(false);
                    break;
                case PAUSED:
                    m_captureStatusText.setTextColor(Color.YELLOW);
                    m_startStopCaptureButton.setText("STOP CAPTURE");
                    m_resumePauseCaptureButton.setText("RESUME CAPTURE");
                    m_resumePauseCaptureButton.setActivated(true);
                    break;
            }
        }
    };

    private UploadService m_uploadService = null;
    private final ServiceConnection uploadServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            UploadService.LocalBinder localBinder = (UploadService.LocalBinder) iBinder;
            m_uploadService = localBinder.getService();
            m_uploadService.addUploadListener(this::updateUploadStatus);
            Log.d(TAG, "Service Connected");
            m_uploadButton.setOnClickListener((View view) ->
            {
                if(m_uploadService.getUploadStatus() != UploadScheduler.UploadStatus.UPLOADING) {
                    m_uploadService.start();
                }
                else {
                    m_uploadService.stop();
                }
            });
        };

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            m_uploadService = null;
        };

        public void updateUploadStatus(UploadScheduler.UploadStatus status) {
            Log.d(TAG, "Updating capture status to " + status.toString());
            m_captureStatusText.setText(status.toString());
            switch(status)
            {
                case IDLE:
                    m_captureStatusText.setTextColor(Color.GREEN);
                    m_uploadButton.setText("START UPLOAD");
                    m_uploadButton.setActivated(false);
                    break;
                case UPLOADING:
                    m_captureStatusText.setTextColor(Color.BLACK);
                    m_uploadButton.setText("STOP UPLOAD");
                    m_uploadButton.setActivated(true);
                    break;
                case SUCCESS:
                    m_captureStatusText.setTextColor(Color.YELLOW);
                    m_uploadButton.setText("UPLOAD AGAIN");
                    m_uploadButton.setActivated(false);
                    break;
                case FAILED:
                    m_captureStatusText.setTextColor(Color.RED);
                    m_uploadButton.setText("UPLOAD FAILED");
                    m_uploadButton.setActivated(false);
                    break;
            }
        };
    };

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
                    Toast.makeText(MainActivity.this, "Notification Permission Granted", Toast.LENGTH_SHORT).show();
                } else {
                    // Permission denied
                    Toast.makeText(MainActivity.this, "Notification Permission Denied", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.this, "Media Projection Permission Granted", Toast.LENGTH_SHORT).show();
                        // Now you can start the screen capture
                        onResult(result, getIntent());
                        // mediaProjectionManager.getMediaProjection(result.getResultCode(), result.getData());
                    } else {
                        // Permission denied
                        Toast.makeText(MainActivity.this, "Media Projection Permission Denied", Toast.LENGTH_SHORT).show();
                    }
                }
            });


    private void startNotificationRequest() {
        // Only required for Android 13 (API level 33) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Permission is already granted
                Toast.makeText(MainActivity.this, "Notification Permission Already Granted", Toast.LENGTH_SHORT).show();
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                // Show rationale and request permission
                Toast.makeText(MainActivity.this, "Notification Permission is required for showing notifications.", Toast.LENGTH_SHORT).show();
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
            Intent intent = m_projectionManager.createScreenCaptureIntent();
            // Launch the intent using the ActivityResultLauncher
            requestMediaProjectionLauncher.launch(intent);
        }
    }

    public void onResult(ActivityResult result, Intent intent)
    {
        /*
        if (result.getResultCode() != REQUEST_CODE_MEDIA) {
            Log.e(TAG, "Unknown request code: " + result.getResultCode());
            Toast.makeText(getApplicationContext(), "Unknown request code: " + result.getResultCode(),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (result.getResultCode() != RESULT_OK) {
            // Mark not recording in UI
            Toast.makeText(getApplicationContext(), "Permission denied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        */
        Log.d(TAG, "Calling onResult");
        // Set up UI
        m_startStopCaptureButton = findViewById(R.id.m_startStopCaptureButton);
        m_resumePauseCaptureButton = findViewById(R.id.m_resumePauseCaptureButton);
        m_uploadButton = findViewById(R.id.m_uploadButton);
        m_captureStatusText = findViewById(R.id.m_captureStatusText);
        m_numCapturedFilesText = findViewById(R.id.m_numCapturedFilesText);
        m_uploadStatusText = findViewById(R.id.m_uploadStatusText);
        m_userKeyTextInput = findViewById(R.id.m_userKeyTextInput);
        m_userKeySubmitButton = findViewById(R.id.m_userKeySubmitButton);
        m_userKeyEditButton = findViewById(R.id.m_userKeyEditButton);
        m_userKeyText = findViewById(R.id.m_userKeyText);
        m_settingUseCellularButton = findViewById(R.id.m_settingUseCellularButton);
        Log.d(TAG, "UI was hooked up");
        // Try to grab the settings
        loadOrCreateSettings();
        Log.d(TAG, "Settings were loaded");
        // Populate the settings
        m_userKeyTextInput.setText(Settings.getString("hash", "00000000"));
        m_settingUseCellularButton.setChecked(Boolean.parseBoolean(Settings.getString("useCellular", "")));
        Log.d(TAG, "Settings UI was updated");
        // Get screen density
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenDensity = metrics.densityDpi;
        // Set up the capture service
        Intent screenCaptureIntent = new Intent(MainActivity.this, CaptureService.class);
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

        // Set up the upload service
        Intent uploadIntent = new Intent(MainActivity.this, UploadService.class);
        // Start the service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Calling startForegroundService");
            startForegroundService(uploadIntent);
        }
        // Bind the service
        bindService(screenCaptureIntent, uploadServiceConnection, Context.BIND_AUTO_CREATE);

        Log.d(TAG, "Upload Service was bound");
    }

    // Loads values into the user's settings file
    private void overwriteSettings(String hash, String key, String useCellular)
    {
        Settings.setString("hash", "00000000");
        Settings.setString("key", "00000000000000000000000000000000");
        Settings.setString("useCellular", "false");
        Settings.save();
    }

    // Loads an existing settings JSON file into a Settings object
    // OR creates a new JSON file in the settings folder, populates it, and loads it into a Settings object
    private void loadOrCreateSettings(){
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        boolean existed = true;

        // Get the directory path
        File directory = new File(getApplicationContext().getExternalFilesDir(null), "/screenlife");

        // Check if the directory path is not a file
        if (directory.exists() && !directory.isDirectory()) {
            throw new RuntimeException("Path is not a directory: " + directory.getAbsolutePath());
        }

        // Create the directory if it doesn't exist
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + directory.getAbsolutePath());
            } else {
                Log.i(TAG, "Directory created: " + directory.getAbsolutePath());
            }
        } else {
            Log.i(TAG, "Directory already exists: " + directory.getAbsolutePath());
        }

        // Create the file inside the directory
        File jsonFile = new File(directory, "settings.JSON");

        if (jsonFile.exists() && !jsonFile.isFile()) {
            throw new RuntimeException("Path is not a file: " + jsonFile.getAbsolutePath());
        }

        // If the file doesn't exist, create it
        if (!jsonFile.exists()) {
            Log.i(TAG, "JSON file does not exist, creating a new one.");
            try {
                if (jsonFile.createNewFile()) {
                    Log.i(TAG, "JSON file created: " + jsonFile.getAbsolutePath());
                } else {
                    Log.e(TAG, "Failed to create JSON file: " + jsonFile.getAbsolutePath());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error creating JSON file", e);
                throw new RuntimeException(e);
            }
        } else {
            Log.i(TAG, "JSON file already exists: " + jsonFile.getAbsolutePath());
        }
        // Load the settings
        Settings.load(jsonFile);

        // Populate an empty settings file
        // TODO: ADD MORE RIGOROUS CASES FOR CHECKING BROKEN SETTINGS
        overwriteSettings(
                "00000000",
                "00000000000000000000000000000000",
                "false");
    }
}