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
                .addOnFailureListener(e -> navigateToMain(null));
    }

    // Called after a fresh login — checks 2FA before navigating
    private void routeAfterLogin() {
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.exists() ? doc.getString("role") : null;
                    check2FA(uid, role);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    navigateToMain(null);
                });
    }

    private void check2FA(String uid, String role) {
        db.collection("users").document(uid)
                .collection("configuracion").document("dos_fa")
                .get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);
                    Boolean habilitado = doc.exists() ? doc.getBoolean("habilitado") : null;
                    if (Boolean.TRUE.equals(habilitado)) {
                        String metodo = doc.getString("metodo");
                        Intent intent = new Intent(this, TwoFactorActivity.class);
                        intent.putExtra(TwoFactorActivity.EXTRA_ROLE, role);
                        intent.putExtra(TwoFactorActivity.EXTRA_METODO, metodo != null ? metodo : "email");
                        intent.putExtra(TwoFactorActivity.EXTRA_EMAIL, auth.getCurrentUser().getEmail());
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        navigateToMain(role);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    navigateToMain(role);
                });
    }

    private void navigateToMain(String role) {
        Class<?> dest = "customer".equals(role) ? CustomerHomeActivity.class : MainActivity.class;
        Intent intent = new Intent(this, dest);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
