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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityLoginBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            routeByRole();
            return;
        }

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.tvForgotPassword.setOnClickListener(v -> sendPasswordReset());
        binding.tvCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        if (!validateInputs(email, password)) return;

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> routeAfterLogin())
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.login_failed_generic, Toast.LENGTH_LONG).show();
                });
    }

    // Called when user is already logged in (session restored) — skips 2FA
    private void routeByRole() {
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.exists() ? doc.getString("role") : null;
                    navigateToMain(role);
                })
                // ES: Fail-closed: sin rol verificado no se entra — ofrecer reintento
                // EN: Fail-closed: no verified role, no entry — offer a retry
                .addOnFailureListener(e -> showRetry(this::routeByRole));
    }

    // Called after a fresh login
    private void routeAfterLogin() {
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);
                    String role = doc.exists() ? doc.getString("role") : null;
                    navigateToMain(role);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showRetry(this::routeAfterLogin);
                });
    }

    private void navigateToMain(String role) {
        // ES: Rol desconocido/ausente → menor privilegio (cliente), nunca comerciante
        // EN: Unknown/missing role → least privilege (customer), never merchant
        Class<?> dest = "merchant".equals(role) ? MainActivity.class : CustomerHomeActivity.class;
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        // ES: Candado biométrico local (si el usuario lo activó en este dispositivo)
        // EN: Local biometric lock (if the user enabled it on this device)
        BiometricLock.gate(this, uid, new BiometricLock.Callback() {
            @Override public void onUnlocked() {
                Intent intent = new Intent(LoginActivity.this, dest);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
            @Override public void onCancelled() {
                // ES: No se superó el candado — no entrar
                // EN: Lock not passed — don't enter
                finishAffinity();
            }
        });
    }

    private void showRetry(Runnable action) {
        com.google.android.material.snackbar.Snackbar
                .make(binding.getRoot(), R.string.error_role_fetch,
                        com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.btn_retry, v -> action.run())
                .show();
    }

    private void sendPasswordReset() {
        String email = binding.etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.forgot_password_enter_email));
            return;
        }
        if (!isValidEmail(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_invalid));
            return;
        }
        binding.tilEmail.setError(null);

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, R.string.forgot_password_sent, Toast.LENGTH_LONG).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.forgot_password_sent, Toast.LENGTH_LONG).show());
    }

    private boolean validateInputs(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_required));
            return false;
        }
        if (!isValidEmail(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_invalid));
            return false;
        }
        binding.tilEmail.setError(null);

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_length));
            return false;
        }
        binding.tilPassword.setError(null);

        return true;
    }

    /**
     * ES: Valida solo el FORMATO del correo (sintaxis), no si la cuenta existe.
     *     Correos de prueba bien formados (p. ej. test123@gmail.com) pasan sin problema.
     * EN: Validates only the email FORMAT (syntax), not whether the account exists.
     *     Well-formed test emails (e.g. test123@gmail.com) pass fine.
     */
    private static boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
