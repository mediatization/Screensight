package com.example.screenlife2;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

public class MainActivity extends AppCompatActivity {
    /** UI Members */
    private final Button m_startStopCaptureButton = findViewById(R.id.m_startStopCaptureButton);
    private final Button m_resumePauseCaptureButton = findViewById(R.id.m_resumePauseCaptureButton);
    private final Button m_uploadButton = findViewById(R.id.m_uploadButton);
    private final TextView m_captureStatusText = findViewById(R.id.m_captureStatusText);
    private final TextView m_numCapturedFilesText = findViewById(R.id.m_numCapturedFilesText);
    private final TextView m_uploadStatusText = findViewById(R.id.m_uploadStatusText);
    private final EditText m_userKeyTextInput = findViewById(R.id.m_userKeyTextInput);
    private final Button m_userKeySubmitButton = findViewById(R.id.m_userKeySubmitButton);
    private final Button m_userKeyEditButton = findViewById(R.id.m_userKeyEditButton);
    private final TextView m_userKeyText = findViewById(R.id.m_userKeyText);
    private final ToggleButton m_settingUseCellularButton = findViewById(R.id.m_settingUseCellularButton);

    /** Data Members*/
    private Settings m_settings = null;

    /** Service Members*/
    private CaptureService m_captureService = null;
    private final ServiceConnection captureServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            CaptureService.LocalBinder localBinder = (CaptureService.LocalBinder) iBinder;
            m_captureService = localBinder.getService();
            m_captureService.addCaptureListener(this::updateCaptureStatus);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            m_captureService = null;
        }

        public void updateCaptureStatus(CaptureScheduler.CaptureStatus status, int numCaptured)
        {
            m_captureStatusText.setText(status.toString());
            m_numCapturedFilesText.setText(Integer.toString(numCaptured));
            switch(status)
            {
                case CAPTURING:
                    m_captureStatusText.setTextColor(Color.GREEN);
                    break;
                case STOPPED:
                    m_captureStatusText.setTextColor(Color.RED);
                    break;
                case PAUSED:
                    m_captureStatusText.setTextColor(Color.YELLOW);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // Try to grab the settings
        loadOrCreateSettings();
        // Populate the settings
        m_userKeyTextInput.setText(m_settings.getString("hash", "00000000"));
        m_settingUseCellularButton.setChecked(Boolean.parseBoolean(m_settings.getString("useCellular", "")));
        // Initialize the rest of the UI
        // TODO: CHANGE THE BELOW TO LINES TO BE PART OF THE updateCaptureStatus listener ***
        m_startStopCaptureButton.setText("START CAPTURE");
        m_resumePauseCaptureButton.setActivated(false);
        // Bind the capture service
        Intent screenCaptureIntent = new Intent(this, CaptureService.class);
        // Add the settings to the intent
        screenCaptureIntent.putExtra("settings", m_settings);
        this.getApplicationContext().bindService(screenCaptureIntent, captureServiceConnection, 0);
        // Add the on-click events to the UI
        m_startStopCaptureButton.setOnClickListener((View view) ->
        {
            if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.STOPPED) {
                m_captureService.start();
                m_startStopCaptureButton.setText("STOP CAPTURE");
                m_resumePauseCaptureButton.setActivated(true);
            }
            else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.CAPTURING) {
                m_captureService.stop();
                m_startStopCaptureButton.setText("START CAPTURE");
                m_resumePauseCaptureButton.setActivated(false);
            }
            else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.PAUSED) {
                m_captureService.stop();
                m_startStopCaptureButton.setText("START CAPTURE");
                m_resumePauseCaptureButton.setActivated(false);
            }
        });
        m_resumePauseCaptureButton.setOnClickListener((View view) ->
        {
            if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.PAUSED) {
                m_captureService.start();
                m_startStopCaptureButton.setText("STOP CAPTURE");
                m_resumePauseCaptureButton.setText("PAUSE CAPTURE");
            }
            else if (m_captureService.getCaptureStatus() == CaptureScheduler.CaptureStatus.CAPTURING) {
                m_captureService.pause();
                m_startStopCaptureButton.setText("STOP CAPTURE");
                m_resumePauseCaptureButton.setText("RESUME CAPTURE");
            }
        });
        m_userKeySubmitButton.setOnClickListener((View view) ->
        {
            if(m_userKeyTextInput.length() == 8 && m_userKeyTextInput.getText() != null) {
                m_settings.setString("hash", m_userKeyTextInput.getText().toString());
                m_settings.save();
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
              m_settings.setString("useCellular", Boolean.toString(isChecked));
          }
      });
    }
    // Loads an existing settings JSON file into a Settings object
    // OR creates a new JSON file in the settings folder, populates it, and loads it into a Settings object
    private void loadOrCreateSettings(){
        boolean existed = true;
        File jsonFile = new File(getApplicationContext().getExternalFilesDir(null).getAbsolutePath()
                + "/settings/settings.JSON");
        if (!jsonFile.exists())
        {
            existed = false;
            try {
                jsonFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // Create the settings
        m_settings = new Settings(jsonFile);
        // Populate an empty settings file
        m_settings.setString("hash", "00000000");
        m_settings.setString("useCellular", "true");
        // Save the new settings file
        m_settings.save();
    }
}