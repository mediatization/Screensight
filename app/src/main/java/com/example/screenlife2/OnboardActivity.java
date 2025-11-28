package com.example.screenlife2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OnboardActivity extends AppCompatActivity {
    private static final String TAG = "OnboardingActivity";

    private ImageView m_blackOutPanel;
    private TextView m_userKeyDisplay;
    private ToggleButton m_settingUseCellularButton;
    private Button m_submitButton;
    private Button m_enableAccessibilityButton;
    private TextView m_accessibilityStatus;

    // track previous accessibility state to avoid repeated toasts
    private boolean m_wasAccessibilityEnabled = false;
    
    private Button m_enablebatteryLifeButton;

    private TextView m_batteryStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboard);

        // Grab the settings
        tryGetSettings();

        // Set up UI
        m_blackOutPanel = findViewById(R.id.m_blackOutPanel2);
        m_settingUseCellularButton = findViewById(R.id.m_useCellularButton);
        m_submitButton = findViewById(R.id.m_saveButton);
        m_userKeyDisplay = findViewById(R.id.m_userKeyDisplay);

        // optional accessibility UI (if present in layout)
        m_enableAccessibilityButton = findViewById(R.id.btn_enable_accessibility);
        m_accessibilityStatus = findViewById(R.id.txt_accessibility_status);

        m_batteryStatus = findViewById(R.id.btn_enable_battery);
        m_batteryStatus = findViewById(R.id.txt_battery_status);

        // add click handler to open system accessibility settings
        if (m_enableAccessibilityButton != null) {
            m_enableAccessibilityButton.setOnClickListener(v -> {
                // open accessibility settings so user can enable our service
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            });
        }

        if (m_batteryStatus != null) {
            m_batteryStatus.setOnClickListener(v -> {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS));
            });
        }

        // Add submit button on click listener
        m_submitButton.setOnClickListener(view -> {
            if (trySubmitSettings()) {
                Toast.makeText(OnboardActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(OnboardActivity.this, CaptureActivity.class);
                OnboardActivity.this.startActivity(intent);
                finish();
            } else {
                Toast.makeText(OnboardActivity.this, "Settings incomplete", Toast.LENGTH_SHORT).show();
            }
        });

        // Fill in settings: pretty-print the user key
        String rawKey = Constants.USER_KEY;
        StringBuilder alteredKey = new StringBuilder();
        for (int i = 0; i < 64; i+=8)
        {
            for (int j = 0; j < 4; j++)
                alteredKey.append(rawKey.charAt(i+j)).append(" ");
            alteredKey.append("-");
            for (int j = 4; j < 8; j++)
                alteredKey.append(" ").append(rawKey.charAt(i+j));
            alteredKey.append('\n');
        }
        m_userKeyDisplay.setText(alteredKey);

        m_settingUseCellularButton.setChecked(Boolean.parseBoolean(Settings.getString("useCellular", "")));

        // Disable black out panel
        m_blackOutPanel.setVisibility(View.INVISIBLE);

        //starts the timer to check every 15 min if app has been closed
        scheduleInactivityCheck();

        Log.d(TAG, "Activity Created");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // check accessibility service state and update UI (if the helper is available)
        boolean enabled = AccessibilityUtil.isAccessibilityServiceEnabled(this, MyAccessibilityService.class);

        if (m_accessibilityStatus != null) {
            m_accessibilityStatus.setText(enabled ? "accessibility: enabled" : "accessibility: disabled");
        }

        if(m_batteryStatus != null) {
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            m_batteryStatus.setText(pm.isIgnoringBatteryOptimizations(packageName) ? "Battery Useage: enabled" : "Battery Useage: disabled");
        }


        // only notify on transition to enabled to avoid spamming toasts on every resume
        if (enabled && !m_wasAccessibilityEnabled) {
            Toast.makeText(this, "accessibility service enabled", Toast.LENGTH_SHORT).show();
        }
        m_wasAccessibilityEnabled = enabled;
    }

    private void tryGetSettings(){
        Log.d(TAG, "Calling startCheckSettings");
        // Check permissions
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        // Get the directory path
        Settings.findOrCreateDirectory(getApplicationContext().getExternalFilesDir(null) + "/screenlife");
        File directory = new File(getApplicationContext().getExternalFilesDir(null), "/screenlife");
        // Create the file inside the directory
        File jsonFile = new File(directory, "settings.JSON");
        // Delete an invalid json
        if (jsonFile.exists() && !jsonFile.isFile()) {
            jsonFile.delete();
        }
        // If the file doesn't exist, create it
        boolean existed = jsonFile.exists();
        if (!existed) {
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
        // Handle no settings file
        if (!existed) {
            // Populate an empty settings file
            Settings.setString("useCellular", "false");
            Settings.save();
        }
        else{
            // Populate an empty settings file
            if (Settings.getString("useCellular", "").isEmpty()) {
                Log.d(TAG, "Adding useCellular to Settings");
                Settings.setString("useCellular", "false");
            }
            Settings.save();
        }
    }

    private boolean trySubmitSettings (){
        String useCellular = Boolean.toString(m_settingUseCellularButton.isChecked());
        Settings.setString("useCellular", useCellular);
        Settings.save();
        return true;
    }

    private void scheduleInactivityCheck() {

        Log.d(TAG, "Starting worker task");

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false) // Important: set to false
                .setRequiresCharging(false)      // Important: set to false
                .setRequiresDeviceIdle(false)    // Important: set to false
                .build();

        PeriodicWorkRequest workRequest =
                new PeriodicWorkRequest.Builder(
                        InactivityCheckWorker.class,
                        15, // 15 minutes
                        TimeUnit.MINUTES
                )
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "inactivity_check_work",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                workRequest
        );
    }
}
