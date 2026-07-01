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
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityAyudaContactoBinding;

public class AyudaContactoActivity extends AppCompatActivity {

    private ActivityAyudaContactoBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAyudaContactoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        setupFaq();
        setupContacto();
        setupAcercaDe();
    }

    private void setupFaq() {
        setupFaqRow(binding.rowFaq1, binding.respFaq1, binding.iconFaq1);
        setupFaqRow(binding.rowFaq2, binding.respFaq2, binding.iconFaq2);
        setupFaqRow(binding.rowFaq3, binding.respFaq3, binding.iconFaq3);
    }

    private void setupFaqRow(View row, TextView respuesta, TextView icono) {
        row.setOnClickListener(v -> {
            boolean visible = respuesta.getVisibility() == View.VISIBLE;
            respuesta.setVisibility(visible ? View.GONE : View.VISIBLE);
            icono.setText(visible ? "›" : "⌄");
        });
    }

    private void setupContacto() {
        binding.tvSectionContacto.setVisibility(View.GONE);
        binding.cardContacto.setVisibility(View.GONE);
    }

    private void setupAcercaDe() {
        binding.rowTerminos.setOnClickListener(v ->
            openUrl("https://docs.google.com/document/d/e/2PACX-1vSFnOO5M03b4BR7W322V_Sp-PCubqI5eut1MjFtt3ANmiXklJRP0Ed_VMJ0tt2tkRva7PQPSB5K5d01/pub"));
        binding.rowPrivacidad.setOnClickListener(v ->
            openUrl("https://cyberpos.app/privacidad"));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
