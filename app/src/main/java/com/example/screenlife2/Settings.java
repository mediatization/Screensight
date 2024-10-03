package com.example.screenlife2;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class Settings implements Serializable {
    private File m_file = null;
    private JSONObject m_json = null;
    public File File () { return m_file; }
    public JSONObject JSON () { return m_json; }

    //
    public Settings (File file)
    {
        // Set the file
        m_file = file;
        // Parse the JSON file at the location
        try {
            m_json = new JSONObject(m_file.toString());
        } catch (JSONException e) {
            System.out.println("ERROR: Failed to parse settings JSON");
            m_json = null;
        }
    }
    //
    public String getString(String s1, String s2){
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
    public void setString(String s1, String s2)
    {
        if (m_json != null) {
            try {
                m_json.put(s1, s2);
            } catch (JSONException e) {
                System.out.println("ERROR: Setting string in Settings JSON");
            }
        }
        else
            System.out.println("ERROR: Setting string in Settings JSON");
    }
    //
    public void save(){
        if (m_json != null) {
            try {
                m_file.setWritable(true);
                FileOutputStream out = new FileOutputStream(m_file);
                byte[] array = m_json.toString().getBytes();
                out.write(array);
                out.flush();
            }
            catch (FileNotFoundException e) {
                    System.out.println("ERROR: JSON file not found for Settings saving");
            }
            catch (IOException e) {
                    System.out.println("ERROR: Could not overwrite JSON for Settings saving");
            }
        }
        else{
            System.out.print("ERROR: Setting string in Settings JSON");
        }
    }
}
