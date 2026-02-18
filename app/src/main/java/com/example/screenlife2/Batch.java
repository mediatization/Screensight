package com.example.screenlife2;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


//class for sending a batch of files all at once
public class Batch {

    private static final String TAG = "Batch";

    private final MediaType PNG = MediaType.parse("image/*");

    //Variable which keeps track of all the images
    private final List<File> files;

    private final OkHttpClient client;

    //constructor, takes a list of files to send and a client
    //to send the files to
    Batch(OkHttpClient client) {
        this.files = new ArrayList<>();
        this.client = client;
    }

    //helper function to add files to a batch
    public void addFile(File f) {
        files.add(f);
    }

    //Function to actually send the files
    //returns true on success and false on failure
    public boolean sendFiles() {

        //MultipartBody Builder is a class for adding data to http packets easily
        MultipartBody.Builder bodyPart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        //iterating through all our files and checking their validity
        //if the file corresponds to an actual image then we add it to the packet
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).isFile()) {
                bodyPart.addFormDataPart("file" + (i + 1), files.get(i).getName(), RequestBody.create(files.get(i), PNG));
            }
        }

        //Building the rest of our request to send to Azure
        RequestBody body = bodyPart.build();
        Request request = new Request.Builder()
                .addHeader("Content-Type", "multipart/form-data")
                .url(Constants.UPLOAD_ADDRESS)
                .post(body)
                .build();

        Log.d(TAG, "Sending request: " + request);

        //initiating variable to store response from client
        Response response = null;
        try {
            //variable to keep track of how long our upload takes
            long startTime = System.nanoTime();
            //sending the upload request and storing the response
            response = client.newCall(request).execute();
            //printing out how long upload too to our console
            Log.d(TAG, "Send of " + files.size() + " files took " + (System.nanoTime() - startTime)/1000000 + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }

        //turning our response into a code (need to refactor)
        int code = response != null ? response.code() : 999;
        Log.d(TAG, "Received response of: " + response.toString());
        //closing the connection
        response.close();

        //if files were successfully sent delete them
        //and tell calling function upload finished
        if(code == 201) {
            files.forEach( file -> {
                if (!file.delete()) {
                    Log.d(TAG, "Warning: Failed to delete file");
                }
            });
            return true;
        }

        //otherwise let the calling function know there was an error
        return false;
    }
}