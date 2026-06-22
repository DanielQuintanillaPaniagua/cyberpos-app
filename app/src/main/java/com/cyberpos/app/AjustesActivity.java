package com.cyberpos.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityAjustesBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AjustesActivity extends AppCompatActivity {

    private ActivityAjustesBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAjustesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadUserProfile();
        setupSettingsRows();
        setupBottomNav();
        binding.btnLogout.setOnClickListener(v -> logout());
    }

    private void loadUserProfile() {
        if (auth.getCurrentUser() == null) return;

        String email = auth.getCurrentUser().getEmail();
        binding.tvEmail.setText(email != null ? email : "");

        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.exists() ? doc.getString("fullName") : null;
                    if (name == null || name.isEmpty()) name = email;
                    binding.tvName.setText(name != null ? name : "");
                    if (name != null && !name.isEmpty()) {
                        binding.tvInitials.setText(String.valueOf(name.charAt(0)).toUpperCase());
                    }
                });
    }

    private void setupSettingsRows() {
        binding.rowPerfil.setOnClickListener(v ->
            startActivity(new Intent(this, CustomerPerfilActivity.class)));
        binding.rowMoneda.setOnClickListener(v ->
            startActivity(new Intent(this, CustomerMonedaActivity.class)));
        binding.rowPin.setOnClickListener(v ->
            startActivity(new Intent(this, CustomerPinActivity.class)));
        binding.rowBiometria.setOnClickListener(v ->
            startActivity(new Intent(this, CustomerBiometriaActivity.class)));
        binding.rowNotificaciones.setOnClickListener(v ->
            startActivity(new Intent(this, CustomerNotificacionesActivity.class)));
        binding.rowIdioma.setOnClickListener(v ->
            startActivity(new Intent(this, IdiomaActivity.class)));
        binding.rowVersion.setOnClickListener(v ->
            startActivity(new Intent(this, CustomerVersionActivity.class)));
    }

    private void setupBottomNav() {
        binding.bottomNav.setSelectedItemId(R.id.nav_settings);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_pay) {
                startActivity(new Intent(this, CustomerHomeActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
    }

    private void logout() {
        auth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
