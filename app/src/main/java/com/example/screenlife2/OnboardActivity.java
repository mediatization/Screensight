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
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class OnboardActivity extends AppCompatActivity {
    private static final String TAG = "OnboardingActivity";
    private ImageView m_blackOutPanel;
    private EditText m_userKeyTextInput;
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
        m_userKeyTextInput = findViewById(R.id.m_userKeyTextInput);
        m_settingUseCellularButton = findViewById(R.id.m_settingUseCellular);
        m_submitButton = findViewById(R.id.m_userKeySubmitButton);
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
        m_userKeyTextInput.setText(Settings.getString("key", ""));
        m_settingUseCellularButton.setChecked(Boolean.parseBoolean(Settings.getString("useCellular", "")));
        // Disable black out panel
        m_blackOutPanel.setVisibility(View.INVISIBLE);

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
            Settings.setString("hash", "00000000");
            Settings.setString("key", "0000000000000000000000000000000000000000000000000000000000000000");
            Settings.setString("useCellular", "false");
            Settings.save();
        }
        else{
            // Populate an empty settings file
            if (Settings.getString("hash", "00000000").isEmpty())
            {
                Log.d(TAG, "Adding hash to Settings");
                Settings.setString("hash", "00000000");
            }
            if (Settings.getString("key", "0000000000000000000000000000000000000000000000000000000000000000").length() != 64) {
                Log.d(TAG, "Adding key to Settings");
                Settings.setString("key", "0000000000000000000000000000000000000000000000000000000000000000");
            }
            if (Settings.getString("useCellular", "").isEmpty()) {
                Log.d(TAG, "Adding useCellular to Settings");
                Settings.setString("useCellular", "false");
            }
            Settings.save();
        }
    }

    private boolean trySubmitSettings (){
        String key = m_userKeyTextInput.getText().toString();
        String hash = "";
        String useCellular = Boolean.toString(m_settingUseCellularButton.isChecked());
        Log.d(TAG, "Key has length " + key.length());
        if (key.length() == 64) {
            try {
                Log.d(TAG, "getSHA " + getSHA(key));
                Log.d(TAG, "hash " + toHexString(getSHA(key)));
                hash = toHexString(getSHA(key));
                // Set the settings
                Settings.setString("key", key);
                Settings.setString("hash", hash);
                Settings.setString("useCellular", useCellular);
                Settings.save();
                return true;

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    private byte[] getSHA(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Converter.hexStringToByteArray(input));
        return md.digest();
    }

    private String toHexString(byte[] hash) {
        BigInteger num = new BigInteger(1, hash);
        StringBuilder hexString = new StringBuilder(num.toString(16));
        while (hexString.length() < 64) {
            hexString.insert(0, '0');
        }
        return hexString.toString();
    }
}
