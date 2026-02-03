package com.example.screenlife2;
public class Constants {
	public final static String UPLOAD_ADDRESS = "https://rcostesting-amcxfjbxebf5akdg.eastus-01.azurewebsites.net/api/upload";
	public final static int BATCH_SIZE = 10;
	public static final int AUTO_UPLOAD_COUNT = 600; //30 min worth of capturing
	// This is the decrypted key (or just 'key' in the DMPO, not the 'encrypted key')
	public static final String USER_KEY = "62f6310750471f50f672d8a4a10b8598dd977add19eb9949d1cd740e54022467";
	// This is the hashed key, (not the plain 'hash' in the DMPO, but the 'hashed key')
	public static final String USER_HASH = "7ad126a85900a9827debf6f8113e77cc3fd6b559c946277fbb07a24c2eaadb1b";
}