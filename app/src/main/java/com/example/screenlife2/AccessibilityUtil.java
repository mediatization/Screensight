package com.example.screenlife2;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

public class AccessibilityUtil {
    private static final String TAG = "AccessibilityUtil";

    // check if our accessibility service is enabled in system settings
    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        try {
            int enabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 1) {
                String services = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (!TextUtils.isEmpty(services)) {
                    ComponentName expected = new ComponentName(context, serviceClass);
                    return services.toLowerCase().contains(expected.flattenToString().toLowerCase());
                }
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "setting not found", e);
        }
        return false;
    }
}
