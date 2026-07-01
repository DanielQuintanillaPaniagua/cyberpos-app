/*
 * CyberPOS — Bitcoin POS para pequeños negocios
 * Copyright (C) 2026 Daniel Quintanilla Paniagua
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.cyberpos.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityInformacionNegocioBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class InformacionNegocioActivity extends AppCompatActivity {

    private ActivityInformacionNegocioBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isEditMode = false;

    private static final String[] CATEGORIAS = {
        "Restaurante / Comida", "Tienda / Retail", "Servicios profesionales",
        "Tecnología", "Salud y bienestar", "Educación",
        "Entretenimiento", "Transporte", "Construcción", "Otro"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInformacionNegocioBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setupCategoryDropdown();
        setFieldsEnabled(false);
        loadNegocioData();

        binding.btnEdit.setOnClickListener(v -> {
            if (!isEditMode) {
                enterEditMode();
            } else {
                saveNegocioData();
            }
        });
    }

    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, CATEGORIAS);
        binding.etCategoria.setAdapter(adapter);
    }

    private void loadNegocioData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).collection("negocio").document("datos")
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    setText(binding.etNombreNegocio, doc.getString("nombreNegocio"));
                    setText(binding.etNombreComercial, doc.getString("nombreComercial"));
                    setText(binding.etDescripcion, doc.getString("descripcion"));
                    setText(binding.etHorario, doc.getString("horario"));
                    binding.etCategoria.setText(doc.getString("categoria"), false);
                    setText(binding.etTelefono, doc.getString("telefono"));
                    setText(binding.etEmailNegocio, doc.getString("email"));
                    setText(binding.etPais, doc.getString("pais"));
                    setText(binding.etDepartamento, doc.getString("departamento"));
                    setText(binding.etCiudad, doc.getString("ciudad"));
                    setText(binding.etDireccion, doc.getString("direccion"));
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
        binding.etNombreNegocio.requestFocus();
        showKeyboard();
    }

    private void exitEditMode() {
        isEditMode = false;
        setFieldsEnabled(false);
        binding.btnEdit.setText(R.string.btn_edit_info);
        binding.btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        binding.btnEdit.setTextColor(ContextCompat.getColor(this, R.color.neon_green));
        binding.btnEdit.setStrokeWidth(
            (int) (1.5f * getResources().getDisplayMetrics().density));
        hideKeyboard();
    }

    private void saveNegocioData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("nombreNegocio", getInputText(binding.etNombreNegocio.getText()));
        data.put("nombreComercial", getInputText(binding.etNombreComercial.getText()));
        data.put("descripcion", getInputText(binding.etDescripcion.getText()));
        data.put("horario", getInputText(binding.etHorario.getText()));
        data.put("categoria", binding.etCategoria.getText().toString().trim());
        data.put("telefono", getInputText(binding.etTelefono.getText()));
        data.put("email", getInputText(binding.etEmailNegocio.getText()));
        data.put("pais", getInputText(binding.etPais.getText()));
        data.put("departamento", getInputText(binding.etDepartamento.getText()));
        data.put("ciudad", getInputText(binding.etCiudad.getText()));
        data.put("direccion", getInputText(binding.etDireccion.getText()));

        binding.btnEdit.setEnabled(false);
        db.collection("users").document(uid).collection("negocio").document("datos")
            .set(data)
            .addOnSuccessListener(aVoid -> {
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

    private void setFieldsEnabled(boolean enabled) {
        binding.tilNombreNegocio.setEnabled(enabled);
        binding.tilNombreComercial.setEnabled(enabled);
        binding.tilDescripcion.setEnabled(enabled);
        binding.tilHorario.setEnabled(enabled);
        binding.tilCategoria.setEnabled(enabled);
        binding.tilTelefono.setEnabled(enabled);
        binding.tilEmailNegocio.setEnabled(enabled);
        binding.tilPais.setEnabled(enabled);
        binding.tilDepartamento.setEnabled(enabled);
        binding.tilCiudad.setEnabled(enabled);
        binding.tilDireccion.setEnabled(enabled);
    }

    private void setText(android.widget.EditText et, String value) {
        et.setText(value != null ? value : "");
    }

    private String getInputText(android.text.Editable editable) {
        return editable != null ? editable.toString().trim() : "";
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.etNombreNegocio, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}
