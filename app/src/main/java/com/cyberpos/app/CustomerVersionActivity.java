package com.cyberpos.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityCustomerVersionBinding;

public class CustomerVersionActivity extends AppCompatActivity {

    private static final String URL_TERMINOS   = "https://github.com/DanielQuintanillaPaniagua/cyberpos-app";
    private static final String URL_PRIVACIDAD = "https://github.com/DanielQuintanillaPaniagua/cyberpos-app";

    private static final String LICENCIAS_TEXT =
        "GNU General Public License v3.0\n\n" +
        "Copyright (C) 2025 CyberPOS\n\n" +
        "Este programa es software libre: puedes redistribuirlo y/o modificarlo " +
        "bajo los términos de la Licencia Pública General de GNU publicada por la " +
        "Free Software Foundation, ya sea la versión 3 de la Licencia, o (a tu " +
        "elección) cualquier versión posterior.\n\n" +
        "Dependencias de código abierto utilizadas:\n" +
        "• Firebase Android SDK (Apache 2.0)\n" +
        "• ZXing Android Embedded (Apache 2.0)\n" +
        "• Material Components for Android (Apache 2.0)\n" +
        "• AndroidX Biometric (Apache 2.0)\n" +
        "• CoinGecko API (términos propios)";

    private ActivityCustomerVersionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerVersionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        binding.rowTerminos.setOnClickListener(v -> openUrl(URL_TERMINOS));
        binding.rowPrivacidad.setOnClickListener(v -> openUrl(URL_PRIVACIDAD));
        binding.rowLicencias.setOnClickListener(v -> showLicenses());
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            // No hay browser disponible
        }
    }

    private void showLicenses() {
        new AlertDialog.Builder(this)
            .setTitle("Licencias de código abierto")
            .setMessage(LICENCIAS_TEXT)
            .setPositiveButton("Cerrar", null)
            .show();
    }
}
