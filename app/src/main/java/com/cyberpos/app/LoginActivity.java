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
                .addOnSuccessListener(result -> routeByRole())
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.login_failed_generic, Toast.LENGTH_LONG).show();
                });
    }

    private void routeByRole() {
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.exists() ? doc.getString("role") : null;
                    Class<?> dest = "customer".equals(role) ? CustomerHomeActivity.class : MainActivity.class;
                    Intent intent = new Intent(this, dest);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void sendPasswordReset() {
        String email = binding.etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.forgot_password_enter_email));
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
        binding.tilEmail.setError(null);

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_length));
            return false;
        }
        binding.tilPassword.setError(null);

        return true;
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
