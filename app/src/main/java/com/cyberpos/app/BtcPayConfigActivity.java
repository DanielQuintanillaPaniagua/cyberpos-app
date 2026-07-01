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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityBtcpayConfigBinding;

/**
 * ES: Pantalla donde el comerciante configura SU BTCPay Server (URL, Store ID,
 *     API key). Las credenciales se guardan cifradas en el dispositivo vía
 *     BtcPayConfig — nunca en Firestore ni dentro del APK. "Probar conexión"
 *     valida contra el servidor antes de guardar.
 * EN: Screen where the merchant configures THEIR BTCPay Server (URL, Store ID,
 *     API key). Credentials are stored device-encrypted via BtcPayConfig —
 *     never in Firestore nor inside the APK. "Test connection" validates
 *     against the server before saving.
 */
public class BtcPayConfigActivity extends AppCompatActivity {

    private ActivityBtcpayConfigBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBtcpayConfigBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnProbar.setOnClickListener(v -> testConnection());
        binding.btnGuardar.setOnClickListener(v -> save());

        prefillCurrentConfig();

        binding.etUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateHttpWarning(); }
        });
    }

    /**
     * ES: Precargar los valores efectivos actuales (guardados en el dispositivo,
     *     o el fallback de desarrollo de BuildConfig) para poder editarlos.
     * EN: Prefill the current effective values (device-saved, or the BuildConfig
     *     development fallback) so they can be edited.
     */
    private void prefillCurrentConfig() {
        binding.etUrl.setText(BtcPayConfig.getUrl());
        binding.etStoreId.setText(BtcPayConfig.getStoreId());
        binding.etApiKey.setText(BtcPayConfig.getApiKey());
        updateHttpWarning();
    }

    private void updateHttpWarning() {
        String url = text(binding.etUrl);
        binding.tvHttpWarning.setVisibility(
                url.startsWith("http://") ? View.VISIBLE : View.GONE);
    }

    private boolean validate() {
        clearErrors();
        String url = text(binding.etUrl);

        if (url.isEmpty()) {
            binding.tilUrl.setError(getString(R.string.error_btcpay_url_required));
            return false;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            binding.tilUrl.setError(getString(R.string.error_btcpay_url_scheme));
            return false;
        }
        if (text(binding.etStoreId).isEmpty()) {
            binding.tilStoreId.setError(getString(R.string.error_btcpay_store_required));
            return false;
        }
        if (text(binding.etApiKey).isEmpty()) {
            binding.tilApiKey.setError(getString(R.string.error_btcpay_key_required));
            return false;
        }
        return true;
    }

    private void testConnection() {
        if (!validate()) return;

        setLoading(true);
        Toast.makeText(this, R.string.msg_btcpay_testing, Toast.LENGTH_SHORT).show();

        BtcPayClient.testConnection(
                text(binding.etUrl), text(binding.etStoreId), text(binding.etApiKey),
                new BtcPayClient.Callback<String>() {
                    @Override
                    public void onSuccess(String storeName) {
                        if (isFinishing() || isDestroyed()) return;
                        setLoading(false);
                        Toast.makeText(BtcPayConfigActivity.this,
                                getString(R.string.msg_btcpay_test_ok, storeName),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(BtcPayClient.BtcPayException e) {
                        if (isFinishing() || isDestroyed()) return;
                        setLoading(false);
                        Toast.makeText(BtcPayConfigActivity.this,
                                getString(R.string.error_btcpay_test, e.userMessage("")),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void save() {
        if (!validate()) return;
        BtcPayConfig.save(text(binding.etUrl), text(binding.etStoreId), text(binding.etApiKey));
        Toast.makeText(this, R.string.msg_btcpay_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void clearErrors() {
        binding.tilUrl.setError(null);
        binding.tilStoreId.setError(null);
        binding.tilApiKey.setError(null);
    }

    private void setLoading(boolean loading) {
        binding.btnProbar.setEnabled(!loading);
        binding.btnGuardar.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String text(EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
