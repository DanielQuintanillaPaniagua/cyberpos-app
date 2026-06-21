package com.cyberpos.app;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityPinSeguridadBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class PinSeguridadActivity extends AppCompatActivity {

    private ActivityPinSeguridadBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String existingPinHash = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPinSeguridadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        loadExistingPin();
        binding.btnGuardarPin.setOnClickListener(v -> validateAndSave());
    }

    private void loadExistingPin() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
            .collection("configuracion").document("pin")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists() && doc.getString("pinHash") != null) {
                    existingPinHash = doc.getString("pinHash");
                    binding.tilPinActual.setVisibility(View.VISIBLE);
                } else {
                    existingPinHash = null;
                    binding.tilPinActual.setVisibility(View.GONE);
                }
            });
    }

    private void validateAndSave() {
        clearErrors();
        String pinActual = text(binding.etPinActual);
        String pinNuevo = text(binding.etPinNuevo);
        String pinConfirmar = text(binding.etPinConfirmar);

        if (existingPinHash != null) {
            if (pinActual.isEmpty()) {
                binding.tilPinActual.setError("Ingresa tu PIN actual");
                return;
            }
            if (!hashPin(pinActual).equals(existingPinHash)) {
                binding.tilPinActual.setError("PIN incorrecto");
                return;
            }
        }
        if (pinNuevo.length() < 4 || pinNuevo.length() > 6) {
            binding.tilPinNuevo.setError("El PIN debe tener entre 4 y 6 dígitos");
            return;
        }
        if (!pinNuevo.equals(pinConfirmar)) {
            binding.tilPinConfirmar.setError("Los PINs no coinciden");
            return;
        }

        savePin(hashPin(pinNuevo));
    }

    private void savePin(String hash) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("pinHash", hash);
        data.put("habilitado", true);

        binding.btnGuardarPin.setEnabled(false);
        db.collection("users").document(uid)
            .collection("configuracion").document("pin")
            .set(data)
            .addOnSuccessListener(v -> {
                binding.btnGuardarPin.setEnabled(true);
                existingPinHash = hash;
                binding.tilPinActual.setVisibility(View.VISIBLE);
                binding.etPinActual.setText("");
                binding.etPinNuevo.setText("");
                binding.etPinConfirmar.setText("");
                hideKeyboard();
                Toast.makeText(this, "PIN guardado correctamente", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                binding.btnGuardarPin.setEnabled(true);
                Toast.makeText(this, getString(R.string.msg_save_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
            });
    }

    private void clearErrors() {
        binding.tilPinActual.setError(null);
        binding.tilPinNuevo.setError(null);
        binding.tilPinConfirmar.setError(null);
    }

    private String hashPin(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(pin.getBytes());
            BigInteger number = new BigInteger(1, hashBytes);
            String hex = number.toString(16);
            while (hex.length() < 64) hex = "0" + hex;
            return hex;
        } catch (NoSuchAlgorithmException e) {
            return pin;
        }
    }

    private String text(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}
