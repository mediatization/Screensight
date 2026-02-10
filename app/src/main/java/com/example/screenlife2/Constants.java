package com.example.screenlife2;
public class Constants {
    public final static String UPLOAD_ADDRESS = "UPLOAD_ADDRESS";
    public final static int BATCH_SIZE = 10;
    public static final int AUTO_UPLOAD_COUNT = 600; //30 min worth of capturing
    // This is the decrypted key (or just 'key' in the DMPO, not the 'encrypted key')
    public static final String USER_KEY = "USER_KEY";
    // This is the hashed key, (not the plain 'hash' in the DMPO, but the 'hashed key')
    public static final String USER_HASH = "USER_HASH";
    //id of the user (to be filled in by the DMPO)
    public static final String USER_ID = "placeholder";
}