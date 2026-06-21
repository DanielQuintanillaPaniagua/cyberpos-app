package com.cyberpos.app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityConfirmacionesBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ConfirmacionesActivity extends AppCompatActivity {

    private ActivityConfirmacionesBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final String[] OPCIONES_CONFIRMACIONES = {
        "1 confirmación", "2 confirmaciones", "3 confirmaciones", "6 confirmaciones"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConfirmacionesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, OPCIONES_CONFIRMACIONES);
        binding.etConfirmaciones.setAdapter(adapter);
        binding.etConfirmaciones.setText(OPCIONES_CONFIRMACIONES[2], false); // default: 3

        loadConfig();
        binding.btnGuardar.setOnClickListener(v -> saveConfig());
    }

    private void loadConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
            .collection("configuracion").document("confirmaciones")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Boolean confAuto = doc.getBoolean("confirmacionAutomaticaLightning");
                    if (confAuto != null) binding.switchConfirmacionAuto.setChecked(confAuto);
                    Boolean sonido = doc.getBoolean("sonido");
                    if (sonido != null) binding.switchSonido.setChecked(sonido);
                    Boolean vibracion = doc.getBoolean("vibracion");
                    if (vibracion != null) binding.switchVibracion.setChecked(vibracion);
                    String confOnchain = doc.getString("confirmacionesOnchain");
                    if (confOnchain != null) binding.etConfirmaciones.setText(confOnchain, false);
                }
            });
    }

    private void saveConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("confirmacionAutomaticaLightning", binding.switchConfirmacionAuto.isChecked());
        data.put("sonido", binding.switchSonido.isChecked());
        data.put("vibracion", binding.switchVibracion.isChecked());
        data.put("confirmacionesOnchain", binding.etConfirmaciones.getText().toString());

        binding.btnGuardar.setEnabled(false);
        db.collection("users").document(uid)
            .collection("configuracion").document("confirmaciones")
            .set(data)
            .addOnSuccessListener(v -> {
                binding.btnGuardar.setEnabled(true);
                Toast.makeText(this, R.string.msg_saved_success, Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                binding.btnGuardar.setEnabled(true);
                Toast.makeText(this, getString(R.string.msg_save_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
            });
    }
}
