package com.example.screenlife2;

public class Constants {
    public final static String UPLOAD_ADDRESS = "https://screenlifetest.azurewebsites.net/api/upload";
    public final static int BATCH_SIZE = 10;
    public static final int AUTO_UPLOAD_COUNT = 600; //30 min worth of capturing
    // This is the decrypted key (or just 'key' in the DMPO, not the 'encrypted key')
    public static final String USER_KEY = "edcd58981ef4beda3cf19148da4f078ca39e502640ad2fb1e47eb93b97e21775";
    // This is the hashed key, (not the plain 'hash' in the DMPO, but the 'hashed key')
    public static final String USER_HASH = "a6b598d8f845fc5fcfcb9e6e21966ccf9f836d0adbb748f697bf3d69112e1e62";
}

