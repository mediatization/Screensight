import sys
import os
import bcrypt
import pycrypto
import json
import subprocess
import shutil

def build_all(args : list[str]):
    # Path for App folder (root folder, not subfolder)
    if len(args) < 2:
        print("ERROR: there should be 4 command line args")
        return
    app_path = args[1]
    if not(os.path.exists(app_path)):
        print("ERROR: failed to find android app path")
        return
    # Path for DMPO folder
    if len(args) < 3:
        print("ERROR: there should be 4 command line args")
        return
    dmpo_path = args[2]
    if not(os.path.exists(dmpo_path)):
        print("ERROR: failed to find DMPO path")
        return
    # Path for Keys folder
    keys_path = dmpo_path + "\\keys"
    if not(os.path.exists(keys_path)):
        print("ERROR: failed to find keys folder path in DMPO files")
        return
    # Path for Passphrase Hash file
    passphrase_path = dmpo_path + "\\password_hash"
    if not(os.path.exists(passphrase_path)):
        print("ERROR: failed to find passphrase hash path in DMPO files")
        return
    # Path for Constants file
    constants_path = app_path + "\\app\\src\\main\\java\\com\\example\\screenlife2\\Constants.java"
    if not(os.path.exists(constants_path)):
        print("ERROR: failed to finds constants path in Android App files")
        return
    # Take passphrase
    if len(args) < 4:
        print("ERROR: there should be 4 command line args")
        return
    passphrase = args[3]
    # Verify passphrase
    with open(passphrase_path, mode='r', encoding='utf8') as past:
        if not(bcrypt.compareSync(passphrase, past)):
            print("ERROR: filed to open passphrase hash file in DMPO files")
            return
    # Take upload path
    if len(args) < 5:
        print("ERROR: there should be 4 command line args")
        return
    upload_address = args[4]
    # Go through each key
    for key_file in os.listdir(keys_path):
        with open('key_file') as f:
            key_json = json.load(f)
            # Decrypt the key
            iv = key_json["iv"]
            encrypted_key = key_json["encrypted_key"]
            hashed_key = key_json["hashed_key"]
            key = decipher(encrypted_key, iv, passphrase)
            # Edit the constants
            with open(constants_path) as c:
                c.flush()
                c.write(constants(upload_address, key, hashed_key))
            # Build the app apks
            print("starting build for hashed key: " + hashed_key)
            build()
            print("finished build for hashed key: " + hashed_key)

def build(app_path: str):
    # Paths
    project_dir = app_path
    gradlew = os.path.join(project_dir, "gradlew")
    apk_path = os.path.join(project_dir, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    destination_dir = os.path.join(project_dir, "apk_output")
    # Step 1: Run the Gradle build command
    print("Building APK...")
    build_result = subprocess.run([gradlew, "assembleDebug"], cwd=project_dir)
    if build_result.returncode != 0:
        print("ERROR: Build failed.")
        return
    # Step 2: Check if APK exists
    if not os.path.exists(apk_path):
        print(f"ERROR: APK not found at {apk_path}")
        return
    # Step 3: Create destination folder if not exists
    os.makedirs(destination_dir, exist_ok=True)
    # Step 4: Copy APK
    destination_apk = os.path.join(destination_dir, "app-debug.apk")
    shutil.copy2(apk_path, destination_apk)
    print("Built APK!")
    print(f"APK copied to: {destination_apk}")

def decipher(encrypted_key : str, iv : str, passphrase : str):
    iv_buffer = bytes(iv, "hex")
    key_buffer = bytes(encrypted_key, "hex")
    decipher = pycrypto.createDecipheriv("aes-128-gcm", bytes(passphrase), iv_buffer)
    key = decipher.update(key_buffer).toString("hex")
    return key

def constants(upload_address : str, key : str, hashed_key : str):
    return ("package com.example.screenlife2;\n" + "public class Constants {\n" + 
    "\tpublic final static String UPLOAD_ADDRESS = \"" + upload_address +";\n" +
    "\tpublic final static int BATCH_SIZE = 10;\n" +
    "\tpublic static final int AUTO_UPLOAD_COUNT = 600; //30 min worth of capturing\n" +
    "\t// This is the decrypted key (or just 'key' in the DMPO, not the 'encrypted key')\n" +
    "\tpublic static final String USER_KEY = \"" + key + "\";\n" +
    "\t// This is the hashed key, (not the plain 'hash' in the DMPO, but the 'hashed key')\n" +
    "\tpublic static final String USER_HASH = \"" + hashed_key  + "\";)\n" +
    "}")

if __name__ == "__main__":
    args = sys.argv
    build_all(args)