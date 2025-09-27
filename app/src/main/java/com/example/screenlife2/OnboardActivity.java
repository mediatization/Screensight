package com.example.screenlife2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

public class OnboardActivity extends AppCompatActivity {
    private static final String TAG = "OnboardingActivity";
    private ImageView m_blackOutPanel;
    private TextView m_userKeyDisplay;
    private ToggleButton m_settingUseCellularButton;
    private Button m_submitButton;

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
        // Add submit button on click listener
        m_submitButton.setOnClickListener(view -> {
            if (trySubmitSettings()) {
                Toast.makeText(OnboardActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(OnboardActivity.this, CaptureActivity.class);
                OnboardActivity.this.startActivity(intent);
                finish();
            }
            else {
                Toast.makeText(OnboardActivity.this, "Settings incomplete", Toast.LENGTH_SHORT).show();
            }
        });
        // Fill in settings
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
                ExistingPeriodicWorkPolicy.UPDATE, // Use UPDATE instead of REPLACE (change back to replace)
                workRequest
        );
    }
}
