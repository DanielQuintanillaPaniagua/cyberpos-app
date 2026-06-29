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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityCustomerBiometriaBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CustomerBiometriaActivity extends AppCompatActivity {

    private ActivityCustomerBiometriaBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isLoading = true;
    private String tipoBiometria = "huella";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerBiometriaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setupTypeSelector();
        loadConfig();

        binding.switchBiometria.setOnCheckedChangeListener((btn, checked) -> {
            if (isLoading) return;
            if (checked) {
                verifyBiometricThenEnable();
            } else {
                saveConfig(false);
            }
        });
    }

    private void setupTypeSelector() {
        binding.rowHuella.setOnClickListener(v -> selectType("huella"));
        binding.rowCara.setOnClickListener(v -> selectType("cara"));
    }

    private void selectType(String tipo) {
        tipoBiometria = tipo;
        updateTypeUI();
    }

    private void updateTypeUI() {
        boolean isHuella = "huella".equals(tipoBiometria);
        binding.checkHuella.setText(isHuella ? "✓" : "○");
        binding.checkHuella.setTextColor(isHuella
            ? getColor(R.color.neon_green) : getColor(R.color.text_secondary));
        binding.checkCara.setText(isHuella ? "○" : "✓");
        binding.checkCara.setTextColor(isHuella
            ? getColor(R.color.text_secondary) : getColor(R.color.neon_green));
    }

    private void loadConfig() {
        if (auth.getCurrentUser() == null) {
            isLoading = false;
            return;
        }
        String uid = auth.getCurrentUser().getUid();
        isLoading = true;
        db.collection("users").document(uid)
            .collection("configuracion").document("biometria")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Boolean habilitado = doc.getBoolean("habilitado");
                    binding.switchBiometria.setChecked(Boolean.TRUE.equals(habilitado));
                    String tipo = doc.getString("tipo");
                    if (tipo != null) tipoBiometria = tipo;
                }
                updateTypeUI();
                isLoading = false;
            })
            .addOnFailureListener(e -> {
                updateTypeUI();
                isLoading = false;
            });
    }

    private void verifyBiometricThenEnable() {
        BiometricManager manager = BiometricManager.from(this);
        int canAuth = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK);

        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            revertSwitch("Este dispositivo no tiene sensor biométrico.");
            return;
        }
        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            revertSwitch("No hay datos biométricos registrados. Configura tu huella en los ajustes del sistema.");
            return;
        }
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            revertSwitch("No es posible usar biometría en este dispositivo.");
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verificar identidad")
            .setSubtitle("Confirma para habilitar el acceso biométrico")
            .setNegativeButtonText("Cancelar")
            .build();

        BiometricPrompt prompt = new BiometricPrompt(this,
            ContextCompat.getMainExecutor(this),
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    saveConfig(true);
                }

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(CustomerBiometriaActivity.this,
                            "Error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                    revertSwitch(null);
                }

                @Override
                public void onAuthenticationFailed() {
                    Toast.makeText(CustomerBiometriaActivity.this,
                        "Biometría no reconocida", Toast.LENGTH_SHORT).show();
                }
            });

        prompt.authenticate(promptInfo);
    }

    private void saveConfig(boolean habilitado) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("habilitado", habilitado);
        data.put("tipo", tipoBiometria);

        db.collection("users").document(uid)
            .collection("configuracion").document("biometria")
            .set(data)
            .addOnSuccessListener(v ->
                Toast.makeText(this,
                    habilitado ? "Biometría habilitada" : "Biometría deshabilitada",
                    Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e ->
                Toast.makeText(this, getString(R.string.msg_save_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show());
    }

    private void revertSwitch(String message) {
        isLoading = true;
        binding.switchBiometria.setChecked(false);
        isLoading = false;
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}
