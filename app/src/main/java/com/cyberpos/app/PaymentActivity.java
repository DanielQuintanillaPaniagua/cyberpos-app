package com.cyberpos.app;

import android.content.Intent;
import android.util.Log;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

    private static final String BTCPAY_API_KEY  = BuildConfig.BTCPAY_API_KEY;
    private static final String BTCPAY_STORE_ID = BuildConfig.BTCPAY_STORE_ID;
    private static final String BTCPAY_URL      = BuildConfig.BTCPAY_URL;

    private ActivityPaymentBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private double btcUsdRate = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentInvoiceId;
    private String firestoreDocId;
    private volatile boolean pollingStopped = true;

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

    // ── Form validation ──────────────────────────────────────────────────────

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
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            binding.tilAmount.setError(getString(R.string.error_amount_invalid));
            return;
        }
        binding.tilAmount.setError(null);
        setLoading(true);
        createBtcPayInvoice(amount, description);
    }

    // ── BTCPay API ────────────────────────────────────────────────────────────

    private void createBtcPayInvoice(double amount, String description) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = BTCPAY_URL + "/api/v1/stores/" + BTCPAY_STORE_ID + "/invoices";
                Log.d(TAG, "POST " + endpoint);
                Log.d(TAG, "Headers: Authorization=token " + BTCPAY_API_KEY
                        + ", Content-Type=application/json");

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
                Log.d(TAG, "Request body: " + body);
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

    /** Pulls the human-readable message out of a BTCPay JSON error body, falling back to HTTP code. */
    private static String extractErrorMessage(String body, int httpCode) {
        try {
            JSONObject json = new JSONObject(body);
            // BTCPay uses "message" at the top level, sometimes nested under "errors"
            if (json.has("message")) return json.getString("message");
        } catch (Exception ignored) {}
        return "HTTP " + httpCode + ": " + body;
    }

    // ── QR generation & polling ───────────────────────────────────────────────

    private void showQrAndStartPolling(String checkoutUrl, double amount,
                                       String description, String invoiceId) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(
                    checkoutUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            binding.ivQrCode.setImageBitmap(bitmap);
            binding.ivQrCode.setVisibility(View.VISIBLE);
            binding.tvQrInstructions.setVisibility(View.VISIBLE);
            setLoading(false);

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

    // ── Payment settled ───────────────────────────────────────────────────────

    private void onPaymentSettled() {
        pollingStopped = true;
        handler.removeCallbacks(pollRunnable);

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

        binding.layoutPaymentSuccess.setVisibility(View.GONE);
        binding.scrollViewForm.setVisibility(View.VISIBLE);
        binding.ivQrCode.setVisibility(View.GONE);
        binding.tvQrInstructions.setVisibility(View.GONE);
        binding.etAmount.setText("");
        binding.etDescription.setText("");
        binding.tvBtcEquivalent.setText("≈ — BTC");
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

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

    // ── Helpers ───────────────────────────────────────────────────────────────

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
