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

    private final PriceService.Listener priceListener = price -> updatePreview();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerMonedaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());

        selectedMoneda = CurrencyPref.get(this);

        binding.rowUsd.setOnClickListener(v -> selectMoneda(CurrencyPref.USD));
        binding.rowBtc.setOnClickListener(v -> selectMoneda(CurrencyPref.BTC));
        binding.rowSat.setOnClickListener(v -> selectMoneda(CurrencyPref.SAT));
        binding.rowEur.setOnClickListener(v -> selectMoneda(CurrencyPref.EUR));

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
        String[] codigos = {"USD", "BTC", "SAT", "EUR"};
        TextView[] checks = {
            binding.checkUsd, binding.checkBtc,
            binding.checkSat, binding.checkEur
        };
        for (int i = 0; i < codigos.length; i++) {
            boolean selected = codigos[i].equals(selectedMoneda);
            checks[i].setText(selected ? "✓" : "○");
            checks[i].setTextColor(selected
                ? getColor(R.color.neon_green)
                : getColor(R.color.text_secondary));
        }
        updatePreview();
    }

    /**
     * ES: Muestra el precio de 1 BTC en la moneda actualmente seleccionada (aún sin guardar).
     * EN: Shows the price of 1 BTC in the currently selected (not yet saved) currency.
     */
    private void updatePreview() {
        double usd = PriceService.get().getUsdRate();
        double eur = PriceService.get().getEurRate();
        String text;
        switch (selectedMoneda) {
            case CurrencyPref.EUR:
                text = eur > 0 ? String.format(Locale.US, "1 BTC = €%,.0f EUR", eur) : "—";
                break;
            case CurrencyPref.BTC:
                text = "1 BTC";
                break;
            case CurrencyPref.SAT:
                text = "100,000,000 sats";
                break;
            case CurrencyPref.USD:
            default:
                text = usd > 0 ? String.format(Locale.US, "1 BTC = $%,.0f USD", usd) : "—";
        }
        binding.tvPrecioLive.setText(text);
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
                    // ES: Ignorar GTQ heredado de una versión anterior / EN: Ignore GTQ left over from an older version
                    if (moneda != null && !"GTQ".equals(moneda)) {
                        selectedMoneda = moneda;
                        CurrencyPref.set(this, moneda);
                    }
                }
                updateUI();
            })
            .addOnFailureListener(e -> updateUI());
    }

    private void saveConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // ES: Guardar localmente para lectura síncrona al formatear precios
        // EN: Persist locally for synchronous reads when formatting prices
        CurrencyPref.set(this, selectedMoneda);

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
