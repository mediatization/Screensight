package com.example.screenlife2;
public class Constants {
	public final static String UPLOAD_ADDRESS = "https://rcostesting-amcxfjbxebf5akdg.eastus-01.azurewebsites.net/api/upload";
	public final static int BATCH_SIZE = 10;
	public static final int AUTO_UPLOAD_COUNT = 600; //30 min worth of capturing
	// This is the decrypted key (or just 'key' in the DMPO, not the 'encrypted key')
	public static final String USER_KEY = "cfaaf06debb3993bc01a9b312f3f314a4fe9d2964e94a8d246b4ef61c780ba8f";
	// This is the hashed key, (not the plain 'hash' in the DMPO, but the 'hashed key')
	public static final String USER_HASH = "9605db4dd730930d43b058ebed79978e899dfcd460f79457fbfec4a97ae9ad6e";
}