package com.example.screenlife2;


import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class Encryptor {
    private static final String TAG = "Encryptor";

    /**
     * Saves a bitmap to a temporary PNG file, encrypts that file to a new location,
     * and then deletes the temporary file to save space and ensure security.
     */
    static void encryptAndSaveBitmap(Bitmap bitmap, byte[] key, String filename, String unencryptedPath, String encryptedPath) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        File inputFile = new File(unencryptedPath);
        
        try {
            // 1. Save bitmap to unencrypted file
            try (FileOutputStream fos = new FileOutputStream(inputFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }

            // 2. Encrypt the file
            try (FileInputStream fis = new FileInputStream(inputFile);
                 FileOutputStream fos = new FileOutputStream(encryptedPath)) {

                // Grab the filename by truncating the first part of the path (the '/' plus the hash slice)
                String fname = filename.length() > 10 ? filename.substring(10) : filename;
                Log.d(TAG, "ENCRYPTING WITH FNAME " + fname);

                // Get 8 bytes from an encrypted version of fname, using SHA-256
                byte[] ivBytes = Arrays.copyOfRange(getSHA(fname), 0, 7);

                Log.d(TAG, "ENCRYPTING WITH KEY " + toHex(key));
                Log.d(TAG, "ENCRYPTING WITH IV " + toHex(ivBytes));

                // Create a secret key spec using the key and AES/GCM/NoPadding algorithm
                SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES/GCM/NoPadding");
                // Create a GCMParameterSpec of length 16 * 8, with the iv bytes
                GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(16 * 8, ivBytes);
                // Create an AES/GCM/NoPadding cipher
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                // Set the cypher to encrypt using the secret key spec and gcm paramter spec
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);

                // Run the file through the cipher and into a cipher output stream
                try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        cos.write(buffer, 0, bytesRead);
                    }
                    cos.flush();
                }
            }
        } finally {
            // 3. Delete the original unencrypted file
            if (inputFile.exists()) {
                if (!inputFile.delete()) {
                    Log.e(TAG, "Failed to delete temporary file: " + unencryptedPath);
                }
            }
        }
        Log.d(TAG, "ENCRYPTING FINISHED");
    }

    static private byte[] getSHA(String input)  throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEX_DIGITS = "0123456789abcdef";
    public static String toHex(byte[] data) {
        StringBuffer buf = new StringBuffer();

        for (int i = 0; i != data.length; i++) {
            int v = data[i] & 0xff;

            buf.append(HEX_DIGITS.charAt(v >> 4));
            buf.append(HEX_DIGITS.charAt(v & 0xf));

            buf.append(" ");
        }

        return buf.toString();
    }
}
