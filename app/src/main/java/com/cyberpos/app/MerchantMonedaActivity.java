package com.cyberpos.app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityMerchantMonedaBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MerchantMonedaActivity extends AppCompatActivity {

    private ActivityMerchantMonedaBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final String[] MONEDAS = {"USD ($)", "BTC (₿)"};

    private final PriceService.Listener priceListener = price ->
        binding.tvPrecioLive.setText(String.format(Locale.US, "$%,.0f USD", price));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantMonedaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, MONEDAS);
        binding.etMonedaPrincipal.setAdapter(adapter);
        binding.etMonedaPrincipal.setText(MONEDAS[0], false);

        loadConfig();

        binding.btnGuardar.setOnClickListener(v -> saveConfig());
    }

    @Override
    protected void onResume() {
        super.onResume();
        PriceService.get().addListener(priceListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PriceService.get().removeListener(priceListener);
    }

    private void loadConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
            .collection("configuracion").document("moneda")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String moneda = doc.getString("monedaPrincipal");
                    if (moneda != null) binding.etMonedaPrincipal.setText(moneda, false);
                    Boolean mostrarBtc = doc.getBoolean("mostrarBtcEquivalente");
                    if (mostrarBtc != null) binding.switchMostrarBtc.setChecked(mostrarBtc);
                    Boolean autoUpdate = doc.getBoolean("actualizacionAutomatica");
                    if (autoUpdate != null) binding.switchActualizacionAuto.setChecked(autoUpdate);
                }
            });
    }

    private void saveConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("monedaPrincipal", binding.etMonedaPrincipal.getText().toString());
        data.put("mostrarBtcEquivalente", binding.switchMostrarBtc.isChecked());
        data.put("actualizacionAutomatica", binding.switchActualizacionAuto.isChecked());

        binding.btnGuardar.setEnabled(false);
        db.collection("users").document(uid)
            .collection("configuracion").document("moneda")
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
