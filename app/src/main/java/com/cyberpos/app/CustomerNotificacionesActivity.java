package com.cyberpos.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityCustomerNotificacionesBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CustomerNotificacionesActivity extends AppCompatActivity {

    private ActivityCustomerNotificacionesBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isLoading = true;

    private static final String[] LANG_NAMES = {"es","en","pt","fr","de","it"};
    private static final String[] LANG_LABELS = {"Español","English","Português","Français","Deutsch","Italiano"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerNotificacionesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setupSwitchListeners();
        loadPreferences();

        binding.rowIdioma.setOnClickListener(v ->
            startActivity(new Intent(this, IdiomaActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateIdiomaLabel();
    }

    private void updateIdiomaLabel() {
        String code = getSharedPreferences("cyberpos_prefs", MODE_PRIVATE)
            .getString("app_language", "es");
        for (int i = 0; i < LANG_NAMES.length; i++) {
            if (LANG_NAMES[i].equals(code)) {
                binding.tvIdiomaValue.setText(LANG_LABELS[i]);
                return;
            }
        }
        binding.tvIdiomaValue.setText("Español");
    }

    private void setupSwitchListeners() {
        binding.switchPagosRecibidos.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("pagosRecibidos", checked);
        });
        binding.switchRetiros.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("retiros", checked);
        });
        binding.switchCambiosCuenta.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("cambiosCuenta", checked);
        });
        binding.switchPromociones.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("promociones", checked);
        });
    }

    private void loadPreferences() {
        if (auth.getCurrentUser() == null) {
            isLoading = false;
            return;
        }
        String uid = auth.getCurrentUser().getUid();
        isLoading = true;
        db.collection("users").document(uid)
            .collection("configuracion").document("notificaciones")
            .get()
            .addOnSuccessListener(doc -> {
                binding.switchPagosRecibidos.setChecked(getBoolean(doc, "pagosRecibidos", true));
                binding.switchRetiros.setChecked(getBoolean(doc, "retiros", true));
                binding.switchCambiosCuenta.setChecked(getBoolean(doc, "cambiosCuenta", true));
                binding.switchPromociones.setChecked(getBoolean(doc, "promociones", false));
                isLoading = false;
            })
            .addOnFailureListener(e -> isLoading = false);
    }

    private void savePreference(String key, boolean value) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        db.collection("users").document(uid)
            .collection("configuracion").document("notificaciones")
            .update(data)
            .addOnFailureListener(e ->
                db.collection("users").document(uid)
                    .collection("configuracion").document("notificaciones")
                    .set(data)
                    .addOnFailureListener(ex ->
                        Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()));
    }

    private boolean getBoolean(DocumentSnapshot doc, String key, boolean def) {
        if (!doc.exists()) return def;
        Boolean val = doc.getBoolean(key);
        return val != null ? val : def;
    }
}
