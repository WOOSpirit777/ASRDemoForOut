package com.aibabel.asrdemo;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public final class AsrResultParser {

    private AsrResultParser() {
    }

    @NonNull
    public static String parseText(@NonNull String jsonOrText) {
        try {
            return new JSONObject(jsonOrText).optString("text", jsonOrText);
        } catch (JSONException ignored) {
            return jsonOrText;
        }
    }

    @NonNull
    public static String parsePartial(@NonNull String jsonOrText) {
        try {
            return new JSONObject(jsonOrText).optString("partial", jsonOrText);
        } catch (JSONException ignored) {
            return jsonOrText;
        }
    }
}
