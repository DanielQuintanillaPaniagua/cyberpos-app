package com.cyberpos.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityPantallaCobroBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class PantallaCobroActivity extends AppCompatActivity {

    private ActivityPantallaCobroBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final int[] PALETTE_COLORS = {
        0xFF39FF14, 0xFF2196F3, 0xFF9C27B0,
        0xFFFF9800, 0xFFE91E63, 0xFFFF5252
    };
    private static final String[] EXPIRACION_OPTIONS = {
        "1 min", "2 min", "5 min", "10 min"
    };

    // Límite conservador: 700 KB en caracteres Base64
    private static final int MAX_BASE64_CHARS = 700 * 1024;

    private int selectedColor = PALETTE_COLORS[0];
    // null → el usuario no eligió imagen nueva en esta sesión
    private String pendingLogoBase64 = null;

    private final Handler qrHandler = new Handler(Looper.getMainLooper());
    private final Runnable qrRunnable = this::updateQrPreview;

    private final ActivityResultLauncher<Intent> logoPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            Uri uri = result.getData().getData();
            if (uri == null) return;

            // Procesamiento en background para no bloquear UI
            new Thread(() -> {
                String base64 = processLogoImage(uri);
                if (base64 == null) {
                    runOnUiThread(() ->
                        Toast.makeText(this, "No se pudo procesar la imagen", Toast.LENGTH_SHORT).show());
                    return;
                }
                // Decodificamos el resultado para mostrarlo en el ImageView
                byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
                Bitmap preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                runOnUiThread(() -> {
                    pendingLogoBase64 = base64;
                    if (preview != null) {
                        binding.ivLogo.setImageBitmap(preview);
                        binding.tvLogoPlaceholder.setVisibility(View.GONE);
                    }
                });
            }).start();
        });

    // ── Procesamiento de imagen ──────────────────────────────────────────────

    /**
     * Carga la imagen desde la URI, la redimensiona a ≤256×256 manteniendo
     * proporción, la comprime a JPEG y devuelve el string Base64 resultante.
     * Si el resultado supera MAX_BASE64_CHARS reduce la calidad progresivamente.
     * Devuelve null en caso de error.
     */
    private String processLogoImage(Uri uri) {
        try {
            // Paso 1 – leer dimensiones sin cargar píxeles
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, opts);
            }

            // Paso 2 – inSampleSize para no cargar imágenes enormes completas en RAM
            int sampleSize = 1;
            while (opts.outWidth / sampleSize > 512 || opts.outHeight / sampleSize > 512) {
                sampleSize *= 2;
            }

            // Paso 3 – cargar con submuestreo
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            Bitmap sampled;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                sampled = BitmapFactory.decodeStream(is, null, opts);
            }
            if (sampled == null) return null;

            // Paso 4 – escalar a máximo 256×256 manteniendo proporción
            float scale = Math.min(256f / sampled.getWidth(), 256f / sampled.getHeight());
            Bitmap resized;
            if (scale < 1.0f) {
                resized = Bitmap.createScaledBitmap(
                    sampled,
                    Math.round(sampled.getWidth() * scale),
                    Math.round(sampled.getHeight() * scale),
                    true);
                sampled.recycle();
            } else {
                resized = sampled;
            }

            // Paso 5 – comprimir a JPEG; bajar calidad si supera el límite
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

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPantallaCobroBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        setupColorPicker();
        setupExpiracionDropdown();
        setupMensajeWatcher();

        binding.btnSeleccionarLogo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            logoPickerLauncher.launch(intent);
        });

        binding.btnGuardar.setOnClickListener(v -> saveConfig());
        loadConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        qrHandler.removeCallbacks(qrRunnable);
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private void setupColorPicker() {
        int sizePx  = (int) (40 * getResources().getDisplayMetrics().density);
        int marginPx = (int) (8 * getResources().getDisplayMetrics().density);
        for (int color : PALETTE_COLORS) {
            FrameLayout circle = new FrameLayout(this);
            android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd(marginPx);
            circle.setLayoutParams(lp);
            circle.setTag(color);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            circle.setBackground(d);
            circle.setOnClickListener(v -> selectColor(color));
            binding.colorPickerContainer.addView(circle);
        }
        selectColor(selectedColor);
    }

    private void selectColor(int color) {
        selectedColor = color;
        int strokePx = (int) (3 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < binding.colorPickerContainer.getChildCount(); i++) {
            View child = binding.colorPickerContainer.getChildAt(i);
            int childColor = (int) child.getTag();
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(childColor);
            if (childColor == color) d.setStroke(strokePx, Color.WHITE);
            child.setBackground(d);
        }
    }

    private void setupExpiracionDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, EXPIRACION_OPTIONS);
        binding.etExpiracion.setAdapter(adapter);
        binding.etExpiracion.setText(EXPIRACION_OPTIONS[1], false);
    }

    private void setupMensajeWatcher() {
        binding.etMensaje.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String txt = s.toString().trim();
                binding.tvMensajePreview.setText(txt.isEmpty() ? "Vista previa del mensaje" : txt);
                qrHandler.removeCallbacks(qrRunnable);
                qrHandler.postDelayed(qrRunnable, 400);
            }
        });
        updateQrPreview();
    }

    private void updateQrPreview() {
        String msg = binding.etMensaje.getText() != null
            ? binding.etMensaje.getText().toString().trim() : "";
        String content = msg.isEmpty() ? "cyberpos://cobro" : "cyberpos://cobro?msg=" + msg;
        try {
            Bitmap qr = new BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 400, 400);
            binding.ivQrPreview.setImageBitmap(qr);
        } catch (WriterException ignored) {}
    }

    // ── Firestore ────────────────────────────────────────────────────────────

    private void loadConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
            .collection("configuracion").document("pantalla_cobro")
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) return;

                String colorHex = doc.getString("colorPrincipal");
                if (colorHex != null) {
                    try { selectColor(Color.parseColor(colorHex)); } catch (Exception ignored) {}
                }

                String msg = doc.getString("mensajePersonalizado");
                if (msg != null) binding.etMensaje.setText(msg);

                Boolean montoEditable = doc.getBoolean("montoEditable");
                if (montoEditable != null) binding.switchMontoEditable.setChecked(montoEditable);
                Boolean mostrarUsd = doc.getBoolean("mostrarUsd");
                if (mostrarUsd != null) binding.switchMostrarUsd.setChecked(mostrarUsd);

                String exp = doc.getString("expiracion");
                if (exp != null) binding.etExpiracion.setText(exp, false);

                // Decodificar logo desde Base64
                String logoBase64 = doc.getString("logoBase64");
                if (logoBase64 != null && !logoBase64.isEmpty()) {
                    new Thread(() -> {
                        byte[] bytes = Base64.decode(logoBase64, Base64.NO_WRAP);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            runOnUiThread(() -> {
                                binding.ivLogo.setImageBitmap(bmp);
                                binding.tvLogoPlaceholder.setVisibility(View.GONE);
                            });
                        }
                    }).start();
                }
            });
    }

    private void saveConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        String mensaje    = binding.etMensaje.getText() != null
            ? binding.etMensaje.getText().toString().trim() : "";
        String expiracion = binding.etExpiracion.getText().toString();
        String colorHex   = String.format("#%06X", 0xFFFFFF & selectedColor);

        Map<String, Object> data = new HashMap<>();
        data.put("colorPrincipal",      colorHex);
        data.put("mensajePersonalizado", mensaje);
        data.put("montoEditable",        binding.switchMontoEditable.isChecked());
        data.put("mostrarUsd",           binding.switchMostrarUsd.isChecked());
        data.put("expiracion",           expiracion);

        // Solo incluimos logoBase64 si el usuario eligió una imagen nueva en esta sesión;
        // merge() preserva el valor anterior si no lo incluimos.
        if (pendingLogoBase64 != null) {
            data.put("logoBase64", pendingLogoBase64);
        }

        binding.btnGuardar.setEnabled(false);
        db.collection("users").document(uid)
            .collection("configuracion").document("pantalla_cobro")
            .set(data, SetOptions.merge())
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
