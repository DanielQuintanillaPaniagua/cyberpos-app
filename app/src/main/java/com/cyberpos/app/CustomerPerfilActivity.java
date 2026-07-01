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

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityCustomerPerfilBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CustomerPerfilActivity extends AppCompatActivity {

    private ActivityCustomerPerfilBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean isEditMode = false;
    private String pendingAvatarBase64 = null;
    private static final int MAX_BASE64_CHARS = 700 * 1024;

    private static final String[] TEMAS = {"Oscuro", "Claro", "Sistema"};

    private final ActivityResultLauncher<Intent> avatarPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            Uri uri = result.getData().getData();
            if (uri == null) return;
            new Thread(() -> {
                String base64 = processAvatarImage(uri);
                if (base64 == null) {
                    runOnUiThread(() -> Toast.makeText(this,
                        "No se pudo procesar la imagen", Toast.LENGTH_SHORT).show());
                    return;
                }
                byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                runOnUiThread(() -> {
                    pendingAvatarBase64 = base64;
                    if (bmp != null) {
                        binding.ivAvatar.setImageBitmap(bmp);
                        binding.ivAvatar.setVisibility(View.VISIBLE);
                        binding.avatarInitialsBg.setVisibility(View.GONE);
                    }
                });
            }).start();
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerPerfilBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.ivAvatar.setClipToOutline(true);

        binding.btnBack.setOnClickListener(v -> finish());

        binding.avatarContainer.setOnClickListener(v -> {
            if (isEditMode) openImagePicker();
        });

        binding.rowTema.setOnClickListener(v -> showThemeDialog());

        binding.rowMonedaPrincipal.setOnClickListener(v ->
            startActivity(new Intent(this, CustomerMonedaActivity.class)));

        binding.btnEdit.setOnClickListener(v -> {
            if (!isEditMode) enterEditMode();
            else saveProfile();
        });

        setFieldsEnabled(false);
        loadProfile();
    }

    private void loadProfile() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        String email = auth.getCurrentUser().getEmail();
        binding.etEmail.setText(email != null ? email : "");
        binding.tvAvatarEmail.setText(email != null ? email : "");

        db.collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                String name = doc.exists() ? doc.getString("fullName") : null;
                if (name != null && !name.isEmpty()) {
                    binding.etNombre.setText(name);
                    binding.tvAvatarName.setText(name);
                    binding.tvAvatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
                }
            });

        db.collection("users").document(uid)
            .collection("perfil").document("datos")
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) return;

                String telefono = doc.getString("telefono");
                if (telefono != null) binding.etTelefono.setText(telefono);

                String pais = doc.getString("pais");
                if (pais != null) binding.etPais.setText(pais);

                String tema = doc.getString("tema");
                if (tema != null) binding.tvTemaValue.setText(tema);

                // ES: La moneda real de visualización vive en CurrencyPref (la fija el selector)
                // EN: The real display currency lives in CurrencyPref (set by the selector screen)
                binding.tvMonedaValue.setText(CurrencyPref.get(this));

                String avatarBase64 = doc.getString("avatarBase64");
                if (avatarBase64 != null && !avatarBase64.isEmpty()) {
                    new Thread(() -> {
                        byte[] bytes = Base64.decode(avatarBase64, Base64.NO_WRAP);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            runOnUiThread(() -> {
                                binding.ivAvatar.setImageBitmap(bmp);
                                binding.ivAvatar.setVisibility(View.VISIBLE);
                                binding.avatarInitialsBg.setVisibility(View.GONE);
                            });
                        }
                    }).start();
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ES: Refrescar al volver del selector de moneda / EN: Refresh when returning from the currency selector
        binding.tvMonedaValue.setText(CurrencyPref.get(this));
    }

    private void enterEditMode() {
        isEditMode = true;
        setFieldsEnabled(true);
        binding.cameraOverlay.setVisibility(View.VISIBLE);
        binding.btnEdit.setText("Guardar cambios");
        binding.btnEdit.setBackgroundTintList(
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.neon_green)));
        binding.btnEdit.setTextColor(ContextCompat.getColor(this, R.color.text_on_neon));
        binding.btnEdit.setStrokeWidth(0);
        binding.etNombre.requestFocus();
        showKeyboard();
    }

    private void exitEditMode() {
        isEditMode = false;
        setFieldsEnabled(false);
        binding.cameraOverlay.setVisibility(View.GONE);
        binding.btnEdit.setText("Editar perfil");
        binding.btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        binding.btnEdit.setTextColor(ContextCompat.getColor(this, R.color.neon_green));
        binding.btnEdit.setStrokeWidth(
            (int) (1.5f * getResources().getDisplayMetrics().density));
        hideKeyboard();
    }

    private void saveProfile() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        String nombre   = getText(binding.etNombre);
        String telefono = getText(binding.etTelefono);
        String pais     = getText(binding.etPais);

        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("fullName", nombre);

        Map<String, Object> perfilData = new HashMap<>();
        perfilData.put("telefono", telefono);
        perfilData.put("pais", pais);
        perfilData.put("tema", binding.tvTemaValue.getText().toString());
        perfilData.put("monedaPrincipal", binding.tvMonedaValue.getText().toString());
        if (pendingAvatarBase64 != null) {
            perfilData.put("avatarBase64", pendingAvatarBase64);
        }

        binding.btnEdit.setEnabled(false);
        db.collection("users").document(uid)
            .update(userUpdate)
            .addOnCompleteListener(task ->
                db.collection("users").document(uid)
                    .collection("perfil").document("datos")
                    .set(perfilData, SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        binding.btnEdit.setEnabled(true);
                        binding.tvAvatarName.setText(nombre);
                        if (!nombre.isEmpty()) {
                            binding.tvAvatarInitial.setText(
                                String.valueOf(nombre.charAt(0)).toUpperCase());
                        }
                        pendingAvatarBase64 = null;
                        exitEditMode();
                        Toast.makeText(this, R.string.msg_saved_success, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        binding.btnEdit.setEnabled(true);
                        Toast.makeText(this,
                            getString(R.string.msg_save_error, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                    }));
    }

    private void setFieldsEnabled(boolean enabled) {
        binding.tilNombre.setEnabled(enabled);
        binding.tilTelefono.setEnabled(enabled);
        binding.tilPais.setEnabled(enabled);
    }

    private void showThemeDialog() {
        String current = binding.tvTemaValue.getText().toString();
        int selected = 0;
        for (int i = 0; i < TEMAS.length; i++) {
            if (TEMAS[i].equals(current)) { selected = i; break; }
        }
        new AlertDialog.Builder(this)
            .setTitle("Seleccionar tema")
            .setSingleChoiceItems(TEMAS, selected, (dialog, which) -> {
                String tema = TEMAS[which];
                binding.tvTemaValue.setText(tema);
                getSharedPreferences(CyberPOSApp.PREFS_NAME, MODE_PRIVATE)
                    .edit().putString(CyberPOSApp.KEY_THEME, tema).apply();
                CyberPOSApp.applyTheme(tema);
                dialog.dismiss();
            })
            .show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        avatarPickerLauncher.launch(intent);
    }

    private String processAvatarImage(Uri uri) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, opts);
            }
            int sampleSize = 1;
            while (opts.outWidth / sampleSize > 512 || opts.outHeight / sampleSize > 512) {
                sampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            Bitmap sampled;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                sampled = BitmapFactory.decodeStream(is, null, opts);
            }
            if (sampled == null) return null;

            float scale = Math.min(256f / sampled.getWidth(), 256f / sampled.getHeight());
            Bitmap resized;
            if (scale < 1.0f) {
                resized = Bitmap.createScaledBitmap(sampled,
                    Math.round(sampled.getWidth() * scale),
                    Math.round(sampled.getHeight() * scale), true);
                sampled.recycle();
            } else {
                resized = sampled;
            }

            String base64 = compressToBase64(resized, 70);
            if (base64.length() > MAX_BASE64_CHARS) base64 = compressToBase64(resized, 50);
            if (base64.length() > MAX_BASE64_CHARS) base64 = compressToBase64(resized, 30);
            resized.recycle();
            return base64;
        } catch (Exception e) {
            return null;
        }
    }

    private String compressToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private String getText(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.etNombre, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
