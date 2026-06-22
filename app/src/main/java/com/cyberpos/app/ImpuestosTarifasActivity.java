package com.cyberpos.app;

import android.graphics.Color;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityImpuestosTarifasBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ImpuestosTarifasActivity extends AppCompatActivity {

    private ActivityImpuestosTarifasBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImpuestosTarifasBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setFieldsEnabled(false);
        loadImpuestosData();

        binding.btnEdit.setOnClickListener(v -> {
            if (!isEditMode) enterEditMode();
            else saveImpuestosData();
        });
    }

    private void loadImpuestosData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
            .collection("configuracion").document("impuestos")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String iva = doc.getString("iva");
                    String isr = doc.getString("isr");
                    binding.etIva.setText(iva != null ? iva : "");
                    binding.etIsr.setText(isr != null ? isr : "");
                }
            });
    }

    private void enterEditMode() {
        isEditMode = true;
        setFieldsEnabled(true);
        binding.btnEdit.setText(R.string.btn_save_changes);
        binding.btnEdit.setBackgroundTintList(
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.neon_green)));
        binding.btnEdit.setTextColor(ContextCompat.getColor(this, R.color.text_on_neon));
        binding.btnEdit.setStrokeWidth(0);
        binding.etIva.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.etIva, InputMethodManager.SHOW_IMPLICIT);
    }

    private void exitEditMode() {
        isEditMode = false;
        setFieldsEnabled(false);
        binding.btnEdit.setText(R.string.btn_edit_info);
        binding.btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        binding.btnEdit.setTextColor(ContextCompat.getColor(this, R.color.neon_green));
        binding.btnEdit.setStrokeWidth(
            (int) (1.5f * getResources().getDisplayMetrics().density));
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void setFieldsEnabled(boolean enabled) {
        binding.tilIva.setEnabled(enabled);
        binding.tilIsr.setEnabled(enabled);
    }

    private void saveImpuestosData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        String iva = binding.etIva.getText() != null ? binding.etIva.getText().toString().trim() : "0";
        String isr = binding.etIsr.getText() != null ? binding.etIsr.getText().toString().trim() : "0";

        Map<String, Object> data = new HashMap<>();
        data.put("iva", iva);
        data.put("isr", isr);

        binding.btnEdit.setEnabled(false);
        db.collection("users").document(uid)
            .collection("configuracion").document("impuestos")
            .set(data)
            .addOnSuccessListener(v -> {
                binding.btnEdit.setEnabled(true);
                exitEditMode();
                Toast.makeText(this, R.string.msg_saved_success, Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                binding.btnEdit.setEnabled(true);
                Toast.makeText(this,
                    getString(R.string.msg_save_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
            });
    }
}
