package com.example.screenlife2;

public class Constants {
    public final static String UPLOAD_ADDRESS = "https://screenlifetest.azurewebsites.net/api/upload";
    public final static int BATCH_SIZE = 10;
    public static final int AUTO_UPLOAD_COUNT = 720;//1 hours worth of capturing

    private final static long UPLOAD_INTERVAL = 60; //Minutes

    public final static int REQ_TIMEOUT = 1200;
}
