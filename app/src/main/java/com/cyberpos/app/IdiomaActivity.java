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

    /** Oculta todos los checkmarks primero, luego marca solo el idioma guardado. */
    private void markCurrentLanguage() {
        // Resetear todos a GONE
        binding.checkEs.setVisibility(View.GONE);
        binding.checkEn.setVisibility(View.GONE);
        binding.checkPt.setVisibility(View.GONE);
        binding.checkFr.setVisibility(View.GONE);
        binding.checkDe.setVisibility(View.GONE);
        binding.checkIt.setVisibility(View.GONE);

        // Mostrar solo el activo
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
        // Guardar en SharedPreferences ANTES de la recreación
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit().putString(KEY_LANG, tag).apply();

        // AppCompatDelegate persiste internamente y recrea las Activities con el nuevo idioma
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tag));
    }
}
