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

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityDosFaBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DosFaActivity extends AppCompatActivity {

    private ActivityDosFaBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isLoading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDosFaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setupListeners();
        loadConfig();
    }

    private void setupListeners() {
        binding.switchDosFa.setOnCheckedChangeListener((btn, checked) -> {
            updateMetodoVisibility(checked);
            if (!isLoading) saveConfig();
        });

        binding.radioGroupMetodo.setOnCheckedChangeListener((group, checkedId) -> {
            if (!isLoading) saveConfig();
        });
    }

    private void loadConfig() {
        if (auth.getCurrentUser() == null) { isLoading = false; return; }
        String uid = auth.getCurrentUser().getUid();
        isLoading = true;
        db.collection("users").document(uid)
            .collection("configuracion").document("dos_fa")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Boolean habilitado = doc.getBoolean("habilitado");
                    if (habilitado != null) binding.switchDosFa.setChecked(habilitado);
                    String metodo = doc.getString("metodo");
                    if ("sms".equals(metodo)) {
                        binding.radioSms.setChecked(true);
                    } else {
                        binding.radioEmail.setChecked(true);
                    }
                }
                isLoading = false;
            })
            .addOnFailureListener(e -> { isLoading = false; });
    }

    private void saveConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        String metodo = binding.radioSms.isChecked() ? "sms" : "email";
        Map<String, Object> data = new HashMap<>();
        data.put("habilitado", binding.switchDosFa.isChecked());
        data.put("metodo", metodo);

        db.collection("users").document(uid)
            .collection("configuracion").document("dos_fa")
            .set(data)
            .addOnFailureListener(e ->
                Toast.makeText(this, getString(R.string.msg_save_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show());
    }

    private void updateMetodoVisibility(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        binding.labelMetodo.setVisibility(visibility);
        binding.cardMetodo.setVisibility(visibility);
        binding.tvEstado2fa.setText(show ? "Activado" : "Desactivado");
    }
}
