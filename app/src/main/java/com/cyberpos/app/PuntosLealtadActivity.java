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

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityPuntosLealtadBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * ES: Configuración del programa de puntos de lealtad (F13): puntos otorgados por cada
 *     $1 USD gastado, y el canje (X puntos = $Y de descuento). Guardado en
 *     users/{uid}/configuracion/lealtad.
 * EN: Loyalty points program configuration (F13): points earned per $1 USD spent, and
 *     the redemption rule (X points = $Y discount). Stored at
 *     users/{uid}/configuracion/lealtad.
 */
public class PuntosLealtadActivity extends AppCompatActivity {

    private ActivityPuntosLealtadBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPuntosLealtadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setFieldsEnabled(false);
        loadLealtadData();

        binding.btnEdit.setOnClickListener(v -> {
            if (!isEditMode) enterEditMode();
            else saveLealtadData();
        });
    }

    private void loadLealtadData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
                .collection("configuracion").document("lealtad")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        binding.etPuntosPorDolar.setText(doc.getString("puntosPorDolar"));
                        binding.etCanjeCantidad.setText(doc.getString("canjeCantidad"));
                        binding.etCanjeDescuento.setText(doc.getString("canjeDescuentoUsd"));
                    }
                });
    }

    private void enterEditMode() {
        isEditMode = true;
        setFieldsEnabled(true);
        binding.btnEdit.setText(R.string.btn_save_changes);
        binding.btnEdit.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.neon_green)));
        binding.btnEdit.setTextColor(ContextCompat.getColor(this, R.color.text_on_neon));
        binding.btnEdit.setStrokeWidth(0);
        binding.etPuntosPorDolar.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.etPuntosPorDolar, InputMethodManager.SHOW_IMPLICIT);
    }

    private void exitEditMode() {
        isEditMode = false;
        setFieldsEnabled(false);
        binding.btnEdit.setText(R.string.btn_edit_info);
        binding.btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        binding.btnEdit.setTextColor(ContextCompat.getColor(this, R.color.neon_green));
        binding.btnEdit.setStrokeWidth(
                (int) (1.5f * getResources().getDisplayMetrics().density));
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void setFieldsEnabled(boolean enabled) {
        binding.tilPuntosPorDolar.setEnabled(enabled);
        binding.tilCanjeCantidad.setEnabled(enabled);
        binding.tilCanjeDescuento.setEnabled(enabled);
    }

    private void saveLealtadData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        String puntosPorDolar = binding.etPuntosPorDolar.getText() != null
                ? binding.etPuntosPorDolar.getText().toString().trim() : "0";
        String canjeCantidad = binding.etCanjeCantidad.getText() != null
                ? binding.etCanjeCantidad.getText().toString().trim() : "0";
        String canjeDescuentoUsd = binding.etCanjeDescuento.getText() != null
                ? binding.etCanjeDescuento.getText().toString().trim() : "0";

        Map<String, Object> data = new HashMap<>();
        data.put("puntosPorDolar", puntosPorDolar);
        data.put("canjeCantidad", canjeCantidad);
        data.put("canjeDescuentoUsd", canjeDescuentoUsd);

        binding.btnEdit.setEnabled(false);
        db.collection("users").document(uid)
                .collection("configuracion").document("lealtad")
                .set(data)
                .addOnSuccessListener(v -> {
                    binding.btnEdit.setEnabled(true);
                    exitEditMode();
                    Toast.makeText(this, R.string.msg_saved_success, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    binding.btnEdit.setEnabled(true);
                    Toast.makeText(this,
                            getString(R.string.msg_save_error, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                });
    }
}
