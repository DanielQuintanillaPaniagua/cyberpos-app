package com.cyberpos.app;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class CyberPOSApp extends Application {

    static final String PREFS_NAME = "cyberpos_prefs";
    static final String KEY_THEME  = "app_theme";

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyTheme(prefs.getString(KEY_THEME, "Oscuro"));
    }

    static void applyTheme(String tema) {
        int mode;
        switch (tema) {
            case "Claro":   mode = AppCompatDelegate.MODE_NIGHT_NO;             break;
            case "Sistema": mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;  break;
            default:        mode = AppCompatDelegate.MODE_NIGHT_YES;            break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
