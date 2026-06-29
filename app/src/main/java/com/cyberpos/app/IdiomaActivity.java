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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.cyberpos.app.databinding.ActivityIdiomaBinding;

public class IdiomaActivity extends AppCompatActivity {

    private static final String PREF_NAME = "cyberpos_prefs";
    private static final String KEY_LANG  = "app_language";

    private ActivityIdiomaBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIdiomaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        markCurrentLanguage();

        binding.rowEs.setOnClickListener(v -> applyLocale("es"));
        binding.rowEn.setOnClickListener(v -> applyLocale("en"));
        binding.rowPt.setOnClickListener(v -> applyLocale("pt"));
        binding.rowFr.setOnClickListener(v -> applyLocale("fr"));
        binding.rowDe.setOnClickListener(v -> applyLocale("de"));
        binding.rowIt.setOnClickListener(v -> applyLocale("it"));
    }

    /**
     * ES: Oculta todos los checkmarks primero, luego marca solo el idioma guardado.
     * EN: Hides all checkmarks first, then marks only the saved language.
     */
    private void markCurrentLanguage() {
        // ES: Resetear todos a GONE
        // EN: Reset all to GONE
        binding.checkEs.setVisibility(View.GONE);
        binding.checkEn.setVisibility(View.GONE);
        binding.checkPt.setVisibility(View.GONE);
        binding.checkFr.setVisibility(View.GONE);
        binding.checkDe.setVisibility(View.GONE);
        binding.checkIt.setVisibility(View.GONE);

        // ES: Mostrar solo el activo
        // EN: Show only the active one
        switch (getSavedLanguage()) {
            case "en": binding.checkEn.setVisibility(View.VISIBLE); break;
            case "pt": binding.checkPt.setVisibility(View.VISIBLE); break;
            case "fr": binding.checkFr.setVisibility(View.VISIBLE); break;
            case "de": binding.checkDe.setVisibility(View.VISIBLE); break;
            case "it": binding.checkIt.setVisibility(View.VISIBLE); break;
            default:   binding.checkEs.setVisibility(View.VISIBLE); break;
        }
    }

    private String getSavedLanguage() {
        return getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getString(KEY_LANG, "es");
    }

    private void applyLocale(String tag) {
        // ES: Guardar en SharedPreferences ANTES de la recreación
        // EN: Save in SharedPreferences BEFORE the activity recreation
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putString(KEY_LANG, tag).apply();

        // ES: AppCompatDelegate persiste internamente y recrea las Activities con el nuevo idioma
        // EN: AppCompatDelegate persists internally and recreates Activities with the new language
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tag));
    }
}
