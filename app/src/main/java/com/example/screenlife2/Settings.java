package com.example.screenlife2;

import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Arrays;

public class Settings {
    private static final String TAG = "Settings";
    private static File m_file = null;
    private static JSONObject m_json = null;
    public static File File () { return m_file; }
    public static JSONObject JSON () { return m_json; }

    //
    public static void load (File file)
    {
        // Set the file
        m_file = file;

        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file)) {
            int ch;
            while ((ch = fis.read()) != -1) {
                content.append((char) ch);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String contents = content.toString();

        Log.d(TAG, "Creating Settings with: " + contents);
        // Parse the JSON file at the location
        try {
            m_json = new JSONObject(contents);
        } catch (JSONException e) {
            System.out.println("ERROR: Failed to parse settings JSON");
            m_json = new JSONObject();
        }
    }
    //
    public static String getString(String s1, String s2){
        if (m_json == null)
            return "";
        else {
            try {
                return String.format(m_json.getString(s1), s2);
            } catch (JSONException e) {
                System.out.println("ERROR: Settings JSON has no such value");
                return "";
            }
        }
    }
    //
    public static void setString(String s1, String s2)
    {
        if (m_json != null) {
            try {
                m_json.put(s1, s2);
            } catch (JSONException e) {
                Log.d(TAG, "ERROR: Setting string in Settings JSON");
            }
        }
        else
            Log.d(TAG, "ERROR: Setting string in Settings JSON");
    }
    //
    public static void save(){
        if (m_json != null) {
            try {
                m_file.setWritable(true);
                FileOutputStream out = new FileOutputStream(m_file);
                byte[] array = m_json.toString().getBytes();
                out.write(array);
                out.flush();
            }
            catch (FileNotFoundException e) {
                Log.d(TAG, "ERROR: JSON file not found for Settings saving");
            }
            catch (IOException e) {
                Log.d(TAG, "ERROR: Could not overwrite JSON for Settings saving");
            }
        }
        else{
            Log.d(TAG, "ERROR: Setting string in Settings JSON");
        }
    }
    public static void findOrCreateDirectory(String dir)
    {
        // Get the directory path
        File directory = new File(dir);

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
    }
}
