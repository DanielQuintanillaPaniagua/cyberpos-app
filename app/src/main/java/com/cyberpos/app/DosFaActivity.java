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
import androidx.biometric.BiometricManager;

import com.cyberpos.app.databinding.ActivityDosFaBinding;
import com.google.firebase.auth.FirebaseAuth;

/**
 * ES: Pantalla del candado biométrico del comerciante. Reemplaza al antiguo 2FA
 *     por email/SMS (que era teatro). El estado se guarda cifrado en el
 *     dispositivo vía BiometricLock; la verificación la hace el sistema Android.
 * EN: Merchant biometric-lock screen. Replaces the old email/SMS 2FA (which was
 *     theatre). State is stored device-encrypted via BiometricLock; the check is
 *     performed by Android.
 */
public class DosFaActivity extends AppCompatActivity {

    private ActivityDosFaBinding binding;
    private FirebaseAuth auth;
    private boolean isLoading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDosFaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        // ES: Ocultar el antiguo selector de método (email/SMS) — ya no aplica
        // EN: Hide the old method selector (email/SMS) — no longer applies
        binding.labelMetodo.setVisibility(View.GONE);
        binding.cardMetodo.setVisibility(View.GONE);

        binding.btnBack.setOnClickListener(v -> finish());

        String uid = uid();
        isLoading = true;
        binding.switchDosFa.setChecked(BiometricLock.isEnabled(uid));
        updateStateLabel(BiometricLock.isEnabled(uid));
        isLoading = false;

        binding.switchDosFa.setOnCheckedChangeListener((btn, checked) -> {
            if (isLoading) return;
            if (checked) {
                enableWithBiometricConfirmation();
            } else {
                BiometricLock.setEnabled(uid(), false);
                updateStateLabel(false);
            }
        });
    }

    private void enableWithBiometricConfirmation() {
        int status = BiometricLock.status(this);
        if (status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            revert(getString(R.string.error_biometric_no_hardware));
            return;
        }
        if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            revert(getString(R.string.error_biometric_none_enrolled));
            return;
        }
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            revert(getString(R.string.error_biometric_unavailable));
            return;
        }

        BiometricLock.prompt(this, getString(R.string.biometric_confirm_subtitle),
                () -> {
                    BiometricLock.setEnabled(uid(), true);
                    updateStateLabel(true);
                },
                () -> revert(null));
    }

    private void updateStateLabel(boolean enabled) {
        binding.tvEstado2fa.setText(enabled
                ? R.string.label_biometric_enabled
                : R.string.label_biometric_disabled);
    }

    private void revert(String message) {
        isLoading = true;
        binding.switchDosFa.setChecked(false);
        updateStateLabel(false);
        isLoading = false;
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String uid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
}
