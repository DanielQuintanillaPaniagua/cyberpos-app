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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityCustomerMonedaBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CustomerMonedaActivity extends AppCompatActivity {

    private ActivityCustomerMonedaBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String selectedMoneda = "USD";

    private final PriceService.Listener priceListener = price ->
        binding.tvPrecioLive.setText(String.format(Locale.US, "$%,.0f USD", price));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerMonedaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());

        binding.rowUsd.setOnClickListener(v -> selectMoneda("USD"));
        binding.rowBtc.setOnClickListener(v -> selectMoneda("BTC"));
        binding.rowSat.setOnClickListener(v -> selectMoneda("SAT"));
        binding.rowEur.setOnClickListener(v -> selectMoneda("EUR"));
        binding.rowGtq.setOnClickListener(v -> selectMoneda("GTQ"));

        binding.btnGuardar.setOnClickListener(v -> saveConfig());
        loadConfig();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PriceService.get().addListener(priceListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PriceService.get().removeListener(priceListener);
    }

    private void selectMoneda(String moneda) {
        selectedMoneda = moneda;
        updateUI();
    }

    private void updateUI() {
        String[] codigos = {"USD", "BTC", "SAT", "EUR", "GTQ"};
        TextView[] checks = {
            binding.checkUsd, binding.checkBtc,
            binding.checkSat, binding.checkEur, binding.checkGtq
        };
        for (int i = 0; i < codigos.length; i++) {
            boolean selected = codigos[i].equals(selectedMoneda);
            checks[i].setText(selected ? "✓" : "○");
            checks[i].setTextColor(selected
                ? getColor(R.color.neon_green)
                : getColor(R.color.text_secondary));
        }
    }

    private void loadConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
            .collection("configuracion").document("moneda")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String moneda = doc.getString("monedaPrincipal");
                    if (moneda != null) selectedMoneda = moneda;
                }
                updateUI();
            })
            .addOnFailureListener(e -> updateUI());
    }

    private void saveConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("monedaPrincipal", selectedMoneda);

        binding.btnGuardar.setEnabled(false);
        db.collection("users").document(uid)
            .collection("configuracion").document("moneda")
            .set(data)
            .addOnSuccessListener(v -> {
                binding.btnGuardar.setEnabled(true);
                Toast.makeText(this, R.string.msg_saved_success, Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                binding.btnGuardar.setEnabled(true);
                Toast.makeText(this, getString(R.string.msg_save_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
            });
    }
}
