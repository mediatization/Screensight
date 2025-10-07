// CaptureServiceHolder.java
package com.example.screenlife2;

public class CaptureServiceHolder {
    private static CaptureService instance;

    public static void setInstance(CaptureService service) {
        instance = service;
    }

    public static CaptureService getInstance() {
        return instance;
    }
}
