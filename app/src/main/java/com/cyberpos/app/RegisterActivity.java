package com.cyberpos.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String ROLE_MERCHANT = "merchant";
    private static final String ROLE_CUSTOMER = "customer";

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String selectedRole = ROLE_MERCHANT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.toggleRole.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnRoleMerchant) {
                selectedRole = ROLE_MERCHANT;
                binding.tilBusinessName.setVisibility(View.VISIBLE);
                binding.tilFullName.setVisibility(View.GONE);
            } else {
                selectedRole = ROLE_CUSTOMER;
                binding.tilBusinessName.setVisibility(View.GONE);
                binding.tilFullName.setVisibility(View.VISIBLE);
            }
        });

        binding.btnCreateAccount.setOnClickListener(v -> attemptRegister());
        binding.tvHaveAccount.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();
        String confirmPassword = binding.etConfirmPassword.getText().toString();

        String displayName;
        if (ROLE_MERCHANT.equals(selectedRole)) {
            displayName = binding.etBusinessName.getText().toString().trim();
        } else {
            displayName = binding.etFullName.getText().toString().trim();
        }

        if (!validateInputs(displayName, email, password, confirmPassword)) return;

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();
                    saveUserProfile(uid, displayName, email);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.register_failed_generic, Toast.LENGTH_LONG).show();
                });
    }

    private void saveUserProfile(String uid, String displayName, String email) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("role", selectedRole);
        profile.put("email", email);
        profile.put("createdAt", FieldValue.serverTimestamp());

        if (ROLE_MERCHANT.equals(selectedRole)) {
            profile.put("businessName", displayName);
        } else {
            profile.put("fullName", displayName);
        }

        db.collection("users").document(uid)
                .set(profile)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                    goToDestination();
                })
                .addOnFailureListener(e -> {
                    // Auth succeeded — still route the user in
                    setLoading(false);
                    goToDestination();
                });
    }

    private boolean validateInputs(String displayName, String email,
                                   String password, String confirmPassword) {
        boolean valid = true;

        if (TextUtils.isEmpty(displayName)) {
            if (ROLE_MERCHANT.equals(selectedRole)) {
                binding.tilBusinessName.setError(getString(R.string.error_business_name_required));
            } else {
                binding.tilFullName.setError(getString(R.string.error_full_name_required));
            }
            valid = false;
        } else {
            binding.tilBusinessName.setError(null);
            binding.tilFullName.setError(null);
        }

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_required));
            valid = false;
        } else {
            binding.tilEmail.setError(null);
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_length));
            valid = false;
        } else {
            binding.tilPassword.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError(getString(R.string.error_passwords_dont_match));
            valid = false;
        } else {
            binding.tilConfirmPassword.setError(null);
        }

        return valid;
    }

    private void goToDestination() {
        Class<?> dest = ROLE_MERCHANT.equals(selectedRole)
                ? MainActivity.class
                : HomeActivity.class;
        Intent intent = new Intent(this, dest);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        binding.btnCreateAccount.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
