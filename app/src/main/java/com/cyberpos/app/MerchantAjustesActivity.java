package com.cyberpos.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityMerchantAjustesBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MerchantAjustesActivity extends AppCompatActivity {

    private ActivityMerchantAjustesBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantAjustesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadMerchantProfile();
        setupSettingsRows();
        setupBottomNav();
        binding.btnLogout.setOnClickListener(v -> logout());
    }

    private void loadMerchantProfile() {
        if (auth.getCurrentUser() == null) return;

        String email = auth.getCurrentUser().getEmail();
        binding.tvEmail.setText(email != null ? email : "");

        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = null;
                    if (doc.exists()) {
                        name = doc.getString("businessName");
                        if (name == null || name.isEmpty()) name = doc.getString("fullName");
                    }
                    if (name == null || name.isEmpty()) name = email;
                    binding.tvName.setText(name != null ? name : "");
                    if (name != null && !name.isEmpty()) {
                        binding.tvInitials.setText(String.valueOf(name.charAt(0)).toUpperCase());
                    }
                });
    }

    private void setupSettingsRows() {
        int[] rowIds = {
            R.id.rowBusinessInfo, R.id.rowBankAccounts, R.id.rowTaxes,
            R.id.rowMerchantCurrency, R.id.rowMerchantNotifications,
            R.id.rowPaymentScreen, R.id.rowConfirmations,
            R.id.rowMerchantPin, R.id.rowTwoFa, R.id.rowLinkedDevices,
            R.id.rowHelp, R.id.rowContact, R.id.rowAbout
        };
        for (int id : rowIds) {
            findViewById(id).setOnClickListener(v ->
                    Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show());
        }
    }

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_cobros) {
                startActivity(new Intent(this, PaymentActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_history) {
                startActivity(new Intent(this, MerchantHistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
        binding.bottomNav.setSelectedItemId(R.id.nav_merchant_settings);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_merchant_settings);
    }

    private void logout() {
        auth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
