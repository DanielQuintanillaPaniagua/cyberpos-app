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

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cyberpos.app.databinding.ActivityGestionProductosBinding;
import com.cyberpos.app.databinding.DialogProductoBinding;
import com.cyberpos.app.model.CartTotals;
import com.cyberpos.app.model.Producto;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GestionProductosActivity extends AppCompatActivity {

    private static final String[] DEFAULT_CATEGORIES =
            {"Comida", "Bebida", "Servicio", "Otro"};
    private static final int MAX_IMAGE_PX = 256;

    private ActivityGestionProductosBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final List<Producto> productos = new ArrayList<>();
    private GestionProductosAdapter adapter;

    private DialogProductoBinding dialogBinding;
    private String pendingImageBase64 = null;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) encodeImage(uri);
            });

    // ES: Cámara — devuelve una miniatura Bitmap directamente (sin FileProvider)
    // EN: Camera — returns a thumbnail Bitmap directly (no FileProvider)
    private final ActivityResultLauncher<Void> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) encodeBitmap(bitmap);
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    takePictureLauncher.launch(null);
                } else {
                    Toast.makeText(this, R.string.error_camera_permission, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGestionProductosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new GestionProductosAdapter(productos, new GestionProductosAdapter.OnItemAction() {
            @Override public void onEdit(Producto p, int position) { showProductoDialog(p, position); }
            @Override public void onDelete(Producto p, int position) { deleteProducto(p, position); }
        });

        binding.recyclerProductos.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerProductos.setAdapter(adapter);
        binding.recyclerProductos.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        binding.fabAgregarProducto.setOnClickListener(v -> showProductoDialog(null, -1));
        binding.btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProductos();
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    private void loadProductos() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("productos")
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    productos.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Producto p = doc.toObject(Producto.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            productos.add(p);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    boolean empty = productos.isEmpty();
                    binding.tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                    binding.recyclerProductos.setVisibility(empty ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_products, Toast.LENGTH_SHORT).show());
    }

    private void saveProducto(Producto p, String docId) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        if (docId == null) {
            db.collection("users").document(uid).collection("productos")
                    .add(p)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, R.string.msg_product_saved, Toast.LENGTH_SHORT).show();
                        loadProductos();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, R.string.error_save_product, Toast.LENGTH_SHORT).show());
        } else {
            db.collection("users").document(uid).collection("productos").document(docId)
                    .set(p)
                    .addOnSuccessListener(v -> {
                        Toast.makeText(this, R.string.msg_product_saved, Toast.LENGTH_SHORT).show();
                        loadProductos();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, R.string.error_save_product, Toast.LENGTH_SHORT).show());
        }
    }

    private void deleteProducto(Producto p, int position) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        new AlertDialog.Builder(this)
                .setTitle(p.getNombre())
                .setMessage(getString(R.string.msg_confirm_delete_product))
                .setPositiveButton(getString(R.string.btn_delete), (d, w) ->
                        db.collection("users").document(uid).collection("productos")
                                .document(p.getId())
                                .update("activo", false)
                                .addOnSuccessListener(v -> {
                                    productos.remove(position);
                                    adapter.notifyItemRemoved(position);
                                    boolean empty = productos.isEmpty();
                                    binding.tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                                    binding.recyclerProductos.setVisibility(empty ? View.GONE : View.VISIBLE);
                                    Toast.makeText(this, R.string.msg_product_deleted, Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, R.string.error_save_product, Toast.LENGTH_SHORT).show()))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    // ── Dialog add / edit ─────────────────────────────────────────────────────

    private void showProductoDialog(Producto existing, int position) {
        pendingImageBase64 = existing != null ? existing.getImagenBase64() : null;

        dialogBinding = DialogProductoBinding.inflate(LayoutInflater.from(this));

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, DEFAULT_CATEGORIES);
        dialogBinding.actCategoria.setAdapter(catAdapter);

        String[] discLabels = {
                getString(R.string.discount_none),
                getString(R.string.discount_percent),
                getString(R.string.discount_fixed)
        };
        dialogBinding.actDescuentoTipo.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, discLabels));
        dialogBinding.actDescuentoTipo.setText(discLabels[0], false);

        String[] scopeLabels = {
                getString(R.string.scope_all),
                getString(R.string.scope_first),
                getString(R.string.scope_threshold)
        };
        dialogBinding.actDescuentoAlcance.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, scopeLabels));
        dialogBinding.actDescuentoAlcance.setText(scopeLabels[0], false);
        // ES: Mostrar el campo "mínimo de unidades" solo para el alcance "a partir de N"
        // EN: Show the "minimum units" field only for the "from N units" scope
        dialogBinding.actDescuentoAlcance.setOnItemClickListener((p, v, pos, id) ->
                dialogBinding.tilDescuentoMinCant.setVisibility(pos == 2 ? View.VISIBLE : View.GONE));

        if (existing != null) {
            dialogBinding.etNombre.setText(existing.getNombre());
            dialogBinding.etPrecio.setText(String.format(Locale.US, "%.2f", existing.getPrecioUsd()));
            dialogBinding.actCategoria.setText(existing.getCategoria(), false);
            if (existing.getStock() >= 0) {
                dialogBinding.etStock.setText(String.valueOf(existing.getStock()));
            }
            dialogBinding.actDescuentoTipo.setText(discLabelForCode(existing.getDescuentoTipo()), false);
            if (existing.getDescuentoValor() > 0) {
                dialogBinding.etDescuentoValor.setText(
                        String.format(Locale.US, "%.2f", existing.getDescuentoValor()));
            }
            dialogBinding.actDescuentoAlcance.setText(scopeLabelForCode(existing.getDescuentoAlcance()), false);
            if (CartTotals.ALC_THRESHOLD.equals(existing.getDescuentoAlcance())) {
                dialogBinding.tilDescuentoMinCant.setVisibility(View.VISIBLE);
                dialogBinding.etDescuentoMinCant.setText(
                        String.valueOf(Math.max(2, existing.getDescuentoMinCant())));
            }
            if (existing.getImagenBase64() != null && !existing.getImagenBase64().isEmpty()) {
                showImagePreview(existing.getImagenBase64());
            }
        }

        dialogBinding.btnPickImage.setOnClickListener(v -> showImageSourceChooser());
        dialogBinding.btnRemoveImage.setOnClickListener(v -> {
            pendingImageBase64 = null;
            dialogBinding.ivProductoPreview.setVisibility(View.GONE);
            dialogBinding.btnRemoveImage.setVisibility(View.GONE);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null
                        ? getString(R.string.title_add_product)
                        : getString(R.string.title_edit_product))
                .setView(dialogBinding.getRoot())
                .setPositiveButton(getString(R.string.btn_save_product), null)
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nombre = dialogBinding.etNombre.getText() != null
                    ? dialogBinding.etNombre.getText().toString().trim() : "";
            String precioStr = dialogBinding.etPrecio.getText() != null
                    ? dialogBinding.etPrecio.getText().toString().trim() : "";
            String categoria = dialogBinding.actCategoria.getText() != null
                    ? dialogBinding.actCategoria.getText().toString().trim() : "";

            if (TextUtils.isEmpty(nombre)) {
                dialogBinding.etNombre.setError(getString(R.string.error_product_name_required));
                return;
            }
            double precio;
            try {
                precio = Double.parseDouble(precioStr);
                if (precio <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                dialogBinding.etPrecio.setError(getString(R.string.error_product_price_invalid));
                return;
            }
            if (TextUtils.isEmpty(categoria)) categoria = "Otro";

            // ── Stock (F9) ──
            String stockStr = dialogBinding.etStock.getText() != null
                    ? dialogBinding.etStock.getText().toString().trim() : "";
            int stock = -1;
            if (!TextUtils.isEmpty(stockStr)) {
                try {
                    stock = Integer.parseInt(stockStr);
                    if (stock < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    dialogBinding.etStock.setError(getString(R.string.error_product_stock_invalid));
                    return;
                }
            }

            // ── Descuento (F8) ──
            String discCode = discCodeForLabel(dialogBinding.actDescuentoTipo.getText().toString());
            double discValor = 0;
            if (!CartTotals.DISC_NONE.equals(discCode)) {
                String discStr = dialogBinding.etDescuentoValor.getText() != null
                        ? dialogBinding.etDescuentoValor.getText().toString().trim() : "";
                try {
                    discValor = Double.parseDouble(discStr);
                } catch (NumberFormatException e) {
                    discValor = 0;
                }
                if (discValor <= 0) {
                    dialogBinding.etDescuentoValor.setError(getString(R.string.error_discount_value));
                    return;
                }
                if (CartTotals.DISC_PERCENT.equals(discCode) && discValor > 100) {
                    dialogBinding.etDescuentoValor.setError(getString(R.string.error_discount_percent));
                    return;
                }
                if (CartTotals.DISC_FIXED.equals(discCode) && discValor >= precio) {
                    dialogBinding.etDescuentoValor.setError(getString(R.string.error_discount_fixed));
                    return;
                }
            }

            // ── Alcance del descuento (F8) ──
            String alcCode = CartTotals.ALC_ALL;
            int minCant = 1;
            if (!CartTotals.DISC_NONE.equals(discCode)) {
                alcCode = scopeCodeForLabel(dialogBinding.actDescuentoAlcance.getText().toString());
                if (CartTotals.ALC_THRESHOLD.equals(alcCode)) {
                    String minStr = dialogBinding.etDescuentoMinCant.getText() != null
                            ? dialogBinding.etDescuentoMinCant.getText().toString().trim() : "";
                    try {
                        minCant = Integer.parseInt(minStr);
                    } catch (NumberFormatException e) {
                        minCant = 0;
                    }
                    if (minCant < 2) {
                        dialogBinding.etDescuentoMinCant.setError(
                                getString(R.string.error_discount_min_units));
                        return;
                    }
                }
            }

            Producto p = new Producto(nombre, precio, categoria,
                    pendingImageBase64 != null ? pendingImageBase64 : "");
            p.setStock(stock);
            p.setDescuentoTipo(discCode);
            p.setDescuentoValor(discValor);
            p.setDescuentoAlcance(alcCode);
            p.setDescuentoMinCant(minCant);

            if (existing != null) p.setId(existing.getId());

            saveProducto(p, existing != null ? existing.getId() : null);
            dialog.dismiss();
        });
    }

    // ── Mapeo etiqueta ↔ código de descuento / Discount label ↔ code mapping ──

    private String discLabelForCode(String code) {
        if (CartTotals.DISC_PERCENT.equals(code)) return getString(R.string.discount_percent);
        if (CartTotals.DISC_FIXED.equals(code))   return getString(R.string.discount_fixed);
        return getString(R.string.discount_none);
    }

    private String discCodeForLabel(String label) {
        if (getString(R.string.discount_percent).equals(label)) return CartTotals.DISC_PERCENT;
        if (getString(R.string.discount_fixed).equals(label))   return CartTotals.DISC_FIXED;
        return CartTotals.DISC_NONE;
    }

    private String scopeLabelForCode(String code) {
        if (CartTotals.ALC_FIRST.equals(code))     return getString(R.string.scope_first);
        if (CartTotals.ALC_THRESHOLD.equals(code)) return getString(R.string.scope_threshold);
        return getString(R.string.scope_all);
    }

    private String scopeCodeForLabel(String label) {
        if (getString(R.string.scope_first).equals(label))     return CartTotals.ALC_FIRST;
        if (getString(R.string.scope_threshold).equals(label)) return CartTotals.ALC_THRESHOLD;
        return CartTotals.ALC_ALL;
    }

    // ── Selección de imagen: cámara o galería / Image source: camera or gallery ──

    private void showImageSourceChooser() {
        CharSequence[] options = {
                getString(R.string.option_camera),
                getString(R.string.option_gallery)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_image_source)
                .setItems(options, (d, which) -> {
                    if (which == 0) launchCamera();
                    else pickImageLauncher.launch("image/*");
                })
                .show();
    }

    private void launchCamera() {
        // ES: La app declara el permiso CAMERA (escáner QR), así que ACTION_IMAGE_CAPTURE lo exige
        // EN: The app declares the CAMERA permission (QR scanner), so ACTION_IMAGE_CAPTURE requires it
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            takePictureLauncher.launch(null);
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void showImagePreview(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            dialogBinding.ivProductoPreview.setImageBitmap(bmp);
            dialogBinding.ivProductoPreview.setVisibility(View.VISIBLE);
            dialogBinding.btnRemoveImage.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {}
    }

    private void encodeImage(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            encodeBitmap(BitmapFactory.decodeStream(is));
        } catch (Exception e) {
            Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ES: Escala, comprime y codifica un Bitmap a Base64. Usado tanto por la galería
     *     (decodificado de un Uri) como por la cámara (miniatura directa).
     * EN: Scales, compresses and encodes a Bitmap to Base64. Used by both the gallery
     *     (decoded from a Uri) and the camera (direct thumbnail).
     */
    private void encodeBitmap(Bitmap original) {
        if (original == null) {
            Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int w = original.getWidth(), h = original.getHeight();
            float scale = Math.min((float) MAX_IMAGE_PX / w, (float) MAX_IMAGE_PX / h);
            if (scale < 1f) {
                original = Bitmap.createScaledBitmap(
                        original, (int)(w * scale), (int)(h * scale), true);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            original.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            pendingImageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            if (dialogBinding != null) showImagePreview(pendingImageBase64);
        } catch (Exception e) {
            Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show();
        }
    }
}
