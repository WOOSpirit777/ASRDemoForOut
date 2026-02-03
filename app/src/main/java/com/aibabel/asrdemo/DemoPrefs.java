package com.aibabel.asrdemo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Demo-only preferences to share state between demo screens (Java only).
 */
public final class DemoPrefs {

    private DemoPrefs() {
    }

    private static final String PREFS = "asr_sdk_demo";

    private static final String KEY_SN = "sn";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_SELECTED_MODEL_ID = "selected_model_id";

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveDevice(Context context, String sn, String license) {
        sp(context).edit()
                .putString(KEY_SN, sn)
                .putString(KEY_LICENSE, license)
                .apply();
    }

    @Nullable
    public static String getSn(Context context) {
        return sp(context).getString(KEY_SN, null);
    }

    @Nullable
    public static String getLicense(Context context) {
        return sp(context).getString(KEY_LICENSE, null);
    }

    public static void setSelectedModelId(Context context, String modelId) {
        sp(context).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply();
    }

    @Nullable
    public static String getSelectedModelId(Context context) {
        return sp(context).getString(KEY_SELECTED_MODEL_ID, null);
    }
}
