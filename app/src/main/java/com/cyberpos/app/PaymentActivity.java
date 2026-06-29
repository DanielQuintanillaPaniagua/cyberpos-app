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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.util.Log;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityPaymentBinding;
import com.cyberpos.app.model.Payment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;

public class PaymentActivity extends AppCompatActivity {

    private static final int QR_SIZE = 600;
    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final String TAG = "BTCPay";
    private static final int REQ_BT_PRINT = 102;

    private static final String BTCPAY_API_KEY  = BuildConfig.BTCPAY_API_KEY;
    private static final String BTCPAY_STORE_ID = BuildConfig.BTCPAY_STORE_ID;
    private static final String BTCPAY_URL      = BuildConfig.BTCPAY_URL;

    private ActivityPaymentBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private double btcUsdRate = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile String currentInvoiceId;
    private volatile String firestoreDocId;
    private volatile boolean pollingStopped = true;

    // ES: Capturado cuando se genera un QR — usado para imprimir el recibo
    // EN: Captured when a QR is generated — used for receipt printing
    private double currentAmountUsd;
    private double currentAmountBtc;
    private String currentDescription;
    private String merchantBusinessName;

    // ES: NFC HCE (emulación de tarjeta por host)
    // EN: NFC HCE (host card emulation)
    private NfcAdapter nfcAdapter;
    private boolean nfcAvailable;

    private final Runnable pollRunnable = this::doPollOnThread;

    private final PriceService.Listener priceListener = price -> {
        btcUsdRate = price;
        binding.tvLivePrice.setText(String.format(Locale.US, "$%,.0f USD", price));
        updateBtcEquivalent();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        binding.btnGenerateQr.setOnClickListener(v -> handleGenerateQr());
        binding.btnNewPayment.setOnClickListener(v -> resetToForm());
        binding.btnPrintReceipt.setOnClickListener(v -> requestPrintWithPermission());
        loadMerchantName();

        nfcAdapter  = NfcAdapter.getDefaultAdapter(this);
        nfcAvailable = nfcAdapter != null;
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) { updateBtcEquivalent(); }
        });
        setupBottomNav();
    }

    private void updateBtcEquivalent() {
        String raw = binding.etAmount.getText() != null
                ? binding.etAmount.getText().toString().trim() : "";
        if (raw.isEmpty() || btcUsdRate <= 0) {
            binding.tvBtcEquivalent.setText("≈ — BTC");
            return;
        }
        try {
            double usd = Double.parseDouble(raw);
            binding.tvBtcEquivalent.setText(
                    String.format(Locale.US, "≈ %.8f BTC", usd / btcUsdRate));
        } catch (NumberFormatException e) {
            binding.tvBtcEquivalent.setText("≈ — BTC");
        }
    }

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_merchant_history) {
                startActivity(new Intent(this, MerchantHistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_settings) {
                startActivity(new Intent(this, MerchantAjustesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
        binding.bottomNav.setSelectedItemId(R.id.nav_cobros);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_cobros);
        PriceService.get().addListener(priceListener);
        if (currentInvoiceId != null && !pollingStopped) {
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        PriceService.get().removeListener(priceListener);
        handler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollingStopped = true;
        handler.removeCallbacks(pollRunnable);
    }

    // ── Validación del formulario / Form validation ──────────────────────────

    private void handleGenerateQr() {
        String amountStr = binding.etAmount.getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();

        if (TextUtils.isEmpty(amountStr)) {
            binding.tilAmount.setError(getString(R.string.error_amount_required));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0 || amount > 50_000) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            binding.tilAmount.setError(getString(R.string.error_amount_invalid));
            return;
        }
        binding.tilAmount.setError(null);
        setLoading(true);
        createBtcPayInvoice(amount, description);
    }

    // ── BTCPay API / BTCPay API ───────────────────────────────────────────────

    private void createBtcPayInvoice(double amount, String description) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = BTCPAY_URL + "/api/v1/stores/" + BTCPAY_STORE_ID + "/invoices";
                Log.d(TAG, "POST " + endpoint);

                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "token " + BTCPAY_API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(15_000);
                conn.setDoOutput(true);

                String safeDesc = description.isEmpty() ? "Pago CyberPOS" : description;
                String body = new JSONObject()
                        .put("amount", String.format(Locale.US, "%.2f", amount))
                        .put("currency", "USD")
                        .put("metadata", new JSONObject().put("itemDesc", safeDesc))
                        .toString();
                conn.getOutputStream().write(body.getBytes("UTF-8"));

                int code = conn.getResponseCode();
                Log.d(TAG, "Response code: " + code);

                if (code == 200 || code == 201) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    String invoiceId   = json.getString("id");
                    String checkoutUrl = json.optString("checkoutLink", "");
                    Log.d(TAG, "Invoice created: id=" + invoiceId + " checkoutLink=" + checkoutUrl);

                    handler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (!checkoutUrl.isEmpty()) {
                            currentInvoiceId = invoiceId;
                            pollingStopped = false;
                            showQrAndStartPolling(checkoutUrl, amount, description, invoiceId);
                        } else {
                            setLoading(false);
                            Toast.makeText(this, R.string.error_btcpay_invoice, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    InputStream errStream = conn.getErrorStream();
                    String errBody = errStream != null ? readStream(errStream) : "(empty)";
                    Log.e(TAG, "Error " + code + ": " + errBody);

                    String userMsg = extractErrorMessage(errBody, code);
                    handler.post(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            setLoading(false);
                            Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Request failed: " + e.getMessage(), e);
                String userMsg = e.getMessage() != null ? e.getMessage()
                        : getString(R.string.error_btcpay_invoice);
                handler.post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        setLoading(false);
                        Toast.makeText(this, userMsg, Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * ES: Extrae el mensaje legible del cuerpo de error JSON de BTCPay; usa el código HTTP como respaldo.
     * EN: Pulls the human-readable message out of a BTCPay JSON error body, falling back to HTTP code.
     */
    private static String extractErrorMessage(String body, int httpCode) {
        try {
            JSONObject json = new JSONObject(body);
            // ES: BTCPay usa "message" en el nivel superior, a veces anidado bajo "errors"
            // EN: BTCPay uses "message" at the top level, sometimes nested under "errors"
            if (json.has("message")) return json.getString("message");
        } catch (Exception ignored) {}
        return "HTTP " + httpCode + ": " + body;
    }

    // ── Generación de QR y sondeo / QR generation & polling ──────────────────

    private void showQrAndStartPolling(String checkoutUrl, double amount,
                                       String description, String invoiceId) {
        // ES: Capturar datos del pago para el recibo
        // EN: Capture payment data for receipt
        currentAmountUsd  = amount;
        currentAmountBtc  = btcUsdRate > 0 ? amount / btcUsdRate : 0;
        currentDescription = description;

        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(
                    checkoutUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            binding.ivQrCode.setImageBitmap(bitmap);
            binding.ivQrCode.setVisibility(View.VISIBLE);
            binding.tvQrInstructions.setVisibility(View.VISIBLE);
            setLoading(false);

            // ES: Activar NFC HCE para que el cliente pueda tocar y recibir la URI de pago
        // EN: Activate NFC HCE so customer can tap to receive the payment URI
            NfcHceService.setPaymentUri(checkoutUrl);
            if (nfcAvailable && nfcAdapter.isEnabled()) {
                binding.layoutNfcBadge.setVisibility(View.VISIBLE);
            }

            savePaymentToFirestore(amount, description, checkoutUrl, invoiceId);
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        } catch (WriterException e) {
            setLoading(false);
            Toast.makeText(this, R.string.error_qr_generation, Toast.LENGTH_SHORT).show();
        }
    }

    private void doPollOnThread() {
        if (pollingStopped || currentInvoiceId == null) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BTCPAY_URL + "/api/v1/stores/" + BTCPAY_STORE_ID
                        + "/invoices/" + currentInvoiceId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "token " + BTCPAY_API_KEY);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                String status = "";
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    status = json.optString("status", "");
                }
                final String finalStatus = status;
                handler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if ("Settled".equals(finalStatus)) {
                        onPaymentSettled();
                    } else if (!pollingStopped) {
                        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!pollingStopped && !isFinishing() && !isDestroyed()) {
                        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                    }
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ── Pago confirmado / Payment settled ────────────────────────────────────

    private void onPaymentSettled() {
        pollingStopped = true;
        handler.removeCallbacks(pollRunnable);

        // ES: Detener NFC HCE — el pago está completado
        // EN: Stop NFC HCE — payment is done
        NfcHceService.setPaymentUri(null);
        binding.layoutNfcBadge.setVisibility(View.GONE);

        if (firestoreDocId != null) {
            db.collection("payments").document(firestoreDocId)
                    .update("status", "settled");
        }

        binding.scrollViewForm.setVisibility(View.GONE);
        binding.layoutPaymentSuccess.setVisibility(View.VISIBLE);
    }

    private void resetToForm() {
        pollingStopped = true;
        handler.removeCallbacks(pollRunnable);
        currentInvoiceId = null;
        firestoreDocId = null;

        // ES: Limpiar NFC HCE
        // EN: Clear NFC HCE
        NfcHceService.setPaymentUri(null);
        binding.layoutNfcBadge.setVisibility(View.GONE);

        binding.layoutPaymentSuccess.setVisibility(View.GONE);
        binding.scrollViewForm.setVisibility(View.VISIBLE);
        binding.ivQrCode.setVisibility(View.GONE);
        binding.tvQrInstructions.setVisibility(View.GONE);
        binding.etAmount.setText("");
        binding.etDescription.setText("");
        binding.tvBtcEquivalent.setText("≈ — BTC");
    }

    // ── Firestore / Firestore ─────────────────────────────────────────────────

    private void savePaymentToFirestore(double amount, String description,
                                        String bolt11, String invoiceId) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";
        double amountBtc = btcUsdRate > 0 ? amount / btcUsdRate : 0;
        Payment payment = new Payment(uid, amount, amountBtc, "", description, bolt11, invoiceId);

        db.collection("payments")
                .add(payment)
                .addOnSuccessListener(ref -> firestoreDocId = ref.getId())
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_save_payment, Toast.LENGTH_SHORT).show());
    }

    // ── Nombre del comerciante / Merchant name ───────────────────────────────

    private void loadMerchantName() {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("businessName");
                        if (name == null || name.isEmpty()) name = doc.getString("fullName");
                        if (name != null && !name.isEmpty()) merchantBusinessName = name;
                    }
                });
    }

    // ── Impresión Bluetooth / Bluetooth printing ──────────────────────────────

    private void requestPrintWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_PRINT);
                return;
            }
        }
        doPrint();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                            @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_BT_PRINT && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            doPrint();
        }
    }

    private void doPrint() {
        if (PrinterManager.getSavedPrinterMac(this) == null) {
            Toast.makeText(this, R.string.msg_no_printer, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.msg_printing, Toast.LENGTH_SHORT).show();
        PrinterManager.printReceipt(
                this,
                merchantBusinessName,
                currentDescription,
                currentAmountUsd,
                currentAmountBtc,
                new PrinterManager.PrintCallback() {
                    @Override public void onSuccess() {
                        Toast.makeText(PaymentActivity.this,
                                R.string.msg_print_success, Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String msg) {
                        if ("no_printer".equals(msg)) {
                            Toast.makeText(PaymentActivity.this,
                                    R.string.msg_no_printer, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(PaymentActivity.this,
                                    getString(R.string.msg_print_error, msg),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // ── Utilidades / Helpers ─────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        binding.btnGenerateQr.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private static String readStream(InputStream is) {
        Scanner sc = new Scanner(is, "UTF-8").useDelimiter("\\A");
        String result = sc.hasNext() ? sc.next() : "";
        sc.close();
        return result;
    }
}
