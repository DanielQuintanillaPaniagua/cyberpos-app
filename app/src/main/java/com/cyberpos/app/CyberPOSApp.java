/*
 * CyberPOS — Bitcoin POS para pequeños negocios
 * Copyright (C) 2026 Daniel Quintanilla Paniagua
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.cyberpos.app;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class CyberPOSApp extends Application {

    static final String PREFS_NAME = "cyberpos_prefs";
    static final String KEY_THEME  = "app_theme";
    static final String KEY_LANG   = "app_language";

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // ES: Aplicar idioma guardado (por defecto español)
        // EN: Apply saved language (default is Spanish)
        String lang = prefs.getString(KEY_LANG, "es");
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));

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
