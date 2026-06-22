package com.cyberpos.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnScanQr.setOnClickListener(v ->
                Toast.makeText(this, R.string.scan_coming_soon, Toast.LENGTH_SHORT).show());
        binding.btnLogout.setOnClickListener(v -> logout());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }
        loadUserName();
    }

    private void loadUserName() {
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = null;
                    if (doc.exists()) name = doc.getString("fullName");
                    if (name == null) name = auth.getCurrentUser().getEmail();
                    binding.tvWelcome.setText(getString(R.string.welcome_user, name));
                });
    }

    private void logout() {
        auth.signOut();
        Toast.makeText(this, R.string.logged_out, Toast.LENGTH_SHORT).show();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
