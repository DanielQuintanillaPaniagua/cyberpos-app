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
            // ES: No hay browser disponible
            // EN: No browser available
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
