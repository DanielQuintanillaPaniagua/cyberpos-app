package com.cyberpos.app;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityNotificacionesMerchantBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class NotificacionesMerchantActivity extends AppCompatActivity {

    private ActivityNotificacionesMerchantBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isLoading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificacionesMerchantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setupSwitchListeners();
        loadPreferences();
    }

    private void setupSwitchListeners() {
        binding.switchCobrosRecibidos.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("cobrosRecibidos", checked);
        });
        binding.switchPagosGrandes.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("pagosGrandes", checked);
        });
        binding.switchCobrosFailidos.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("cobrosFailidos", checked);
        });
        binding.switchRetiros.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("retiros", checked);
        });
        binding.switchCambiosCuenta.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("cambiosCuenta", checked);
        });
        binding.switchNoticias.setOnCheckedChangeListener((btn, checked) -> {
            if (!isLoading) savePreference("noticias", checked);
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
                binding.switchCobrosRecibidos.setChecked(getBoolean(doc, "cobrosRecibidos", true));
                binding.switchPagosGrandes.setChecked(getBoolean(doc, "pagosGrandes", true));
                binding.switchCobrosFailidos.setChecked(getBoolean(doc, "cobrosFailidos", true));
                binding.switchRetiros.setChecked(getBoolean(doc, "retiros", true));
                binding.switchCambiosCuenta.setChecked(getBoolean(doc, "cambiosCuenta", true));
                binding.switchNoticias.setChecked(getBoolean(doc, "noticias", false));
                binding.switchPromociones.setChecked(getBoolean(doc, "promociones", false));
                isLoading = false;
            })
            .addOnFailureListener(e -> {
                isLoading = false;
            });
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
                // Document may not exist yet — create it
                db.collection("users").document(uid)
                    .collection("configuracion").document("notificaciones")
                    .set(data)
                    .addOnFailureListener(ex ->
                        Toast.makeText(this, "Error al guardar preferencia", Toast.LENGTH_SHORT).show()));
    }

    private boolean getBoolean(DocumentSnapshot doc, String key, boolean defaultValue) {
        if (!doc.exists()) return defaultValue;
        Boolean val = doc.getBoolean(key);
        return val != null ? val : defaultValue;
    }
}
