package com.cyberpos.app;

/*
 * Payment flow: customer scans a BTCPay checkout URL → app fetches invoice details
 * and payment-methods → opens the paymentLink URI in a Bitcoin wallet app via
 * Intent.ACTION_VIEW → polls BTCPay every 3 s until status == Settled.
 *
 * If no wallet is installed, shows a dialog offering Muun or Phoenix on Play Store.
 */

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityCustomerHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomerHomeActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final double WALLET_BTC = 0.00150000;
    private static final long POLL_INTERVAL_MS = 3_000L;

    private static final String PLAY_STORE_MUUN   = "market://details?id=net.muun.apollo";
    private static final String PLAY_STORE_PHOENIX = "market://details?id=fr.acinq.phoenix.mainnet";

    private enum PaymentType { LIGHTNING, BITCOIN_ONCHAIN, BTCPAY_INVOICE }

    private static final Pattern BOLT11_AMOUNT =
            Pattern.compile("^ln(?:bc|tb|bcrt)(\\d+)([munp])?1", Pattern.CASE_INSENSITIVE);
    private static final Pattern BTCPAY_CHECKOUT_URL =
            Pattern.compile("^https?://[^/]+/i/([^/?&#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVOICE_ID_VALID =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityCustomerHomeBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // ── Scanner state ────────────────────────────────────────────────────────
    private boolean scannerResumed = false;
    private double btcUsdRate = 0;
    private volatile String scannedInvoice = null;  // read from polling thread
    private double scannedBtcAmount = 0;
    private PaymentType scannedType = null;
    private double pendingBtcPayUsd = 0;

    // ── Payment flow state ───────────────────────────────────────────────────
    private String paymentLink = null;
    private volatile boolean pollingStopped = true;
    private String paymentFirestoreDocId = null;
    private final Runnable invoicePollRunnable = this::pollInvoiceStatusOnThread;

    private final PriceService.Listener priceListener = price -> {
        btcUsdRate = price;
        binding.tvLivePrice.setText(
                String.format(Locale.US, "1 BTC = $%,.0f  ● LIVE", price));
        binding.tvLivePrice.setTextColor(getColor(R.color.neon_green));
        binding.tvWalletUsd.setText(
                String.format(Locale.US, "≈ $%.2f USD", WALLET_BTC * price));

        if (scannedType == PaymentType.BTCPAY_INVOICE && pendingBtcPayUsd > 0 && scannedBtcAmount == 0) {
            double btc = pendingBtcPayUsd / price;
            scannedBtcAmount = btc;
            pendingBtcPayUsd = 0;
            binding.tvBtcAmount.setText(String.format(Locale.US, "%.8f", btc));
            binding.tvInvoiceBtc.setText(String.format(Locale.US, "%.8f BTC", btc));
            String usdStr = getString(R.string.format_usd_equivalent, btc * price);
            binding.tvUsdEquivalent.setText(usdStr);
            binding.tvInvoiceUsd.setText(usdStr);
            binding.btnPayNow.setEnabled(true);
        } else if (scannedInvoice != null && scannedBtcAmount > 0) {
            double usd = scannedBtcAmount * price;
            binding.tvInvoiceUsd.setText(getString(R.string.format_usd_equivalent, usd));
            binding.tvUsdEquivalent.setText(getString(R.string.format_usd_equivalent, usd));
        } else {
            refreshAmountUsd();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupTabs();
        setupAmountInput();
        setupPayButton();
        setupPaymentConfirmScreen();
        setupBottomNav();
        binding.btnScanAgain.setOnClickListener(v -> resetToScanning());
        binding.btnOpenSettings.setOnClickListener(v -> openAppSettings());
        requestCameraIfNeeded();
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private void setupTabs() {
        binding.toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean isScan = checkedId == R.id.tabScan;
            binding.layoutAmount.setVisibility(isScan ? View.GONE : View.VISIBLE);
            if (isScan) {
                if (scannedInvoice == null) {
                    binding.layoutScan.setVisibility(View.VISIBLE);
                    binding.layoutInvoiceDetails.setVisibility(View.GONE);
                    binding.btnPayNow.setEnabled(false);
                    resumeScannerIfPermitted();
                } else {
                    binding.layoutScan.setVisibility(View.GONE);
                    binding.layoutInvoiceDetails.setVisibility(View.VISIBLE);
                    binding.btnPayNow.setEnabled(scannedBtcAmount > 0);
                }
            } else {
                pauseScanner();
                binding.layoutScan.setVisibility(View.GONE);
                binding.layoutInvoiceDetails.setVisibility(View.GONE);
                binding.btnPayNow.setEnabled(true);
            }
        });
        binding.btnPayNow.setEnabled(false);
        binding.toggleGroup.check(R.id.tabScan);
    }

    // ── Amount input (manual tab) ─────────────────────────────────────────────

    private void setupAmountInput() {
        binding.etBtcAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override
            public void afterTextChanged(Editable s) {
                String raw = s.toString();
                binding.tvBtcAmount.setText(
                        raw.isEmpty() ? getString(R.string.label_btc_amount) : raw);
                refreshAmountUsd();
            }
        });
    }

    private void refreshAmountUsd() {
        if (scannedInvoice != null) return;
        String raw = binding.etBtcAmount.getText() != null
                ? binding.etBtcAmount.getText().toString().trim() : "";
        if (raw.isEmpty() || btcUsdRate <= 0) {
            binding.tvAmountUsd.setText(R.string.label_usd_equivalent);
            binding.tvUsdEquivalent.setText(R.string.label_usd_equivalent);
            return;
        }
        try {
            double btc = Double.parseDouble(raw);
            double usd = btc * btcUsdRate;
            String formatted = getString(R.string.format_usd_equivalent, usd);
            binding.tvAmountUsd.setText(formatted);
            binding.tvUsdEquivalent.setText(formatted);
        } catch (NumberFormatException e) {
            binding.tvAmountUsd.setText(R.string.label_usd_equivalent);
        }
    }

    // ── Pay button ────────────────────────────────────────────────────────────

    private void setupPayButton() {
        binding.btnPayNow.setOnClickListener(v -> initiatePayment());
    }

    // ── Payment confirm / sent screens ────────────────────────────────────────

    private void setupPaymentConfirmScreen() {
        // Abrir wallet: re-open the wallet app if user closed it before paying
        binding.btnConfirmTestPayment.setOnClickListener(v -> openWalletIntent());
        binding.btnCancelPayment.setOnClickListener(v -> onCancelPayment());
        binding.btnNewScan.setOnClickListener(v -> resetToScanning());
    }

    // ── Payment initiation ────────────────────────────────────────────────────

    private void initiatePayment() {
        if (scannedInvoice == null || scannedBtcAmount <= 0
                || scannedType != PaymentType.BTCPAY_INVOICE) {
            Toast.makeText(this, R.string.processing_payment, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnPayNow.setEnabled(false);
        binding.btnPayNow.setText(R.string.label_fetching_address);

        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            fetchPaymentLink(scannedInvoice, "Cliente", "");
            return;
        }

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("fullName");
                    if (name == null || name.isEmpty()) name = "Cliente";
                    fetchPaymentLink(scannedInvoice, name, uid);
                })
                .addOnFailureListener(e ->
                        fetchPaymentLink(scannedInvoice, "Cliente", uid));
    }

    private void fetchPaymentLink(String invoiceId, String customerName, String payerUid) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = BuildConfig.BTCPAY_URL + "/api/v1/stores/"
                        + BuildConfig.BTCPAY_STORE_ID + "/invoices/"
                        + invoiceId + "/payment-methods";
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestProperty("Authorization", "token " + BuildConfig.BTCPAY_API_KEY);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    JSONArray methods = new JSONArray(readStream(conn.getInputStream()));
                    String destAddress = null;
                    String link = null;

                    for (int i = 0; i < methods.length(); i++) {
                        JSONObject m = methods.getJSONObject(i);
                        if ("BTC-CHAIN".equalsIgnoreCase(m.optString("paymentMethodId", ""))) {
                            String addr = m.optString("destination", null);
                            if (addr != null && !addr.isEmpty()) {
                                destAddress = addr;
                                link = m.optString("paymentLink", null);
                                break;
                            }
                        }
                    }

                    // Fallback: build paymentLink from destination address if API didn't return one
                    if ((link == null || link.isEmpty()) && destAddress != null) {
                        link = "bitcoin:" + destAddress;
                        if (scannedBtcAmount > 0) {
                            link += "?amount=" + String.format(Locale.US, "%.8f", scannedBtcAmount);
                        }
                    }

                    final String finalLink = link;
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (finalLink != null) {
                            paymentLink = finalLink;
                            savePayerInfoToFirestore(invoiceId, customerName, payerUid);
                            showWaitingForPayment(scannedBtcAmount);
                            openWalletIntent();
                            startPolling();
                        } else {
                            resetPayButton();
                            Toast.makeText(this, R.string.error_no_btc_address,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    final int finalCode = code;
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        resetPayButton();
                        Toast.makeText(this,
                                getString(R.string.error_payment_methods_fetch, finalCode),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e("BTCPay", "fetchPaymentLink exception", e);
                final String msg = e.getMessage();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    resetPayButton();
                    Toast.makeText(this,
                            msg != null ? msg : getString(R.string.error_btcpay_invoice),
                            Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ── Wallet intent ─────────────────────────────────────────────────────────

    private void openWalletIntent() {
        if (paymentLink == null) return;
        Uri uri;
        try {
            uri = Uri.parse(paymentLink);
        } catch (Exception e) {
            Toast.makeText(this, "Enlace de pago inválido", Toast.LENGTH_SHORT).show();
            return;
        }
        // Only allow the bitcoin: scheme — reject intent://, javascript:, etc.
        String scheme = uri.getScheme();
        if (!"bitcoin".equalsIgnoreCase(scheme)) {
            Toast.makeText(this, "Enlace de pago inválido", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException e) {
            showNoWalletDialog();
        }
    }

    private void showNoWalletDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Wallet no encontrada")
                .setMessage("Necesitás una wallet Bitcoin instalada para completar el pago. "
                        + "Te recomendamos Muun Wallet o Phoenix.")
                .setPositiveButton("Instalar Muun", (d, w) -> openPlayStore(PLAY_STORE_MUUN))
                .setNeutralButton("Instalar Phoenix", (d, w) -> openPlayStore(PLAY_STORE_PHOENIX))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void openPlayStore(String uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (android.content.ActivityNotFoundException e) {
            // Fallback: web Play Store URL
            String webUrl = uri.replace("market://details?id=",
                    "https://play.google.com/store/apps/details?id=");
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
            } catch (Exception ex) {
                Toast.makeText(this, "No se pudo abrir la Play Store", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    private void savePayerInfoToFirestore(String invoiceId, String customerName, String payerUid) {
        db.collection("payments")
                .whereEqualTo("btcPayInvoiceId", invoiceId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) return;
                    String docId = snapshot.getDocuments().get(0).getId();
                    paymentFirestoreDocId = docId;
                    Map<String, Object> update = new HashMap<>();
                    update.put("customerName", customerName);
                    update.put("payerUid", payerUid);
                    db.collection("payments").document(docId).update(update);
                });
    }

    // ── Waiting overlay ───────────────────────────────────────────────────────

    private void showWaitingForPayment(double btcAmount) {
        binding.tvConfirmBtcAmount.setText(
                String.format(Locale.US, "%.8f BTC", btcAmount));
        if (btcUsdRate > 0) {
            binding.tvConfirmUsd.setText(
                    getString(R.string.format_usd_equivalent, btcAmount * btcUsdRate));
        } else {
            binding.tvConfirmUsd.setText("");
        }
        binding.tvPaymentStatus.setText("Esperando confirmación de pago...");
        binding.btnConfirmTestPayment.setText("Abrir wallet");
        binding.btnPayNow.setText(R.string.btn_pay_now);
        binding.btnPayNow.setEnabled(true);
        binding.layoutPaymentConfirm.setVisibility(View.VISIBLE);
    }

    private void onCancelPayment() {
        stopPolling();
        paymentLink = null;
        binding.layoutPaymentConfirm.setVisibility(View.GONE);
    }

    // ── Invoice status polling ─────────────────────────────────────────────────

    private void startPolling() {
        pollingStopped = false;
        mainHandler.postDelayed(invoicePollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        pollingStopped = true;
        mainHandler.removeCallbacks(invoicePollRunnable);
    }

    private void pollInvoiceStatusOnThread() {
        if (pollingStopped || scannedInvoice == null) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BuildConfig.BTCPAY_URL + "/api/v1/stores/"
                        + BuildConfig.BTCPAY_STORE_ID + "/invoices/" + scannedInvoice);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "token " + BuildConfig.BTCPAY_API_KEY);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                String status = "";
                if (conn.getResponseCode() == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    status = json.optString("status", "");
                }
                final String finalStatus = status;
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if ("Settled".equals(finalStatus) || "Complete".equals(finalStatus)) {
                        onPaymentSettled();
                    } else if (!pollingStopped) {
                        mainHandler.postDelayed(invoicePollRunnable, POLL_INTERVAL_MS);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!pollingStopped && !isFinishing() && !isDestroyed()) {
                        mainHandler.postDelayed(invoicePollRunnable, POLL_INTERVAL_MS);
                    }
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void onPaymentSettled() {
        stopPolling();
        if (paymentFirestoreDocId != null) {
            db.collection("payments").document(paymentFirestoreDocId)
                    .update("status", "settled");
        }
        binding.layoutPaymentConfirm.setVisibility(View.GONE);
        binding.layoutPaymentSent.setVisibility(View.VISIBLE);
    }

    private void resetPayButton() {
        binding.btnPayNow.setEnabled(scannedBtcAmount > 0);
        binding.btnPayNow.setText(R.string.btn_pay_now);
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, AjustesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
        binding.bottomNav.setSelectedItemId(R.id.nav_pay);
    }

    // ── Camera / scanner ──────────────────────────────────────────────────────

    private void requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void startScanner() {
        binding.layoutPermissionDenied.setVisibility(View.GONE);
        binding.barcodeView.setStatusText("");
        binding.barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() == null) return;
                binding.barcodeView.pause();
                scannerResumed = false;
                handleScannedQr(result.getText());
            }
            @Override
            public void possibleResultPoints(List<com.google.zxing.ResultPoint> points) {}
        });
        binding.barcodeView.resume();
        scannerResumed = true;
    }

    private void resumeScannerIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED && !scannerResumed) {
            binding.barcodeView.resume();
            scannerResumed = true;
        }
    }

    private void pauseScanner() {
        if (scannerResumed) {
            binding.barcodeView.pause();
            scannerResumed = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner();
            } else {
                binding.layoutPermissionDenied.setVisibility(View.VISIBLE);
            }
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    // ── QR dispatch ───────────────────────────────────────────────────────────

    private void handleScannedQr(String raw) {
        String input = raw.trim();
        String lower = input.toLowerCase(Locale.US);

        Matcher btcpayMatcher = BTCPAY_CHECKOUT_URL.matcher(input);
        if (btcpayMatcher.find()) {
            handleBtcPayUrl(btcpayMatcher.group(1));
        } else if (lower.startsWith("lightning:") || lower.startsWith("ln")) {
            handleLightningInvoice(input);
        } else if (lower.startsWith("bitcoin:") || lower.startsWith("bc1")
                || lower.startsWith("1") || lower.startsWith("3")) {
            handleBitcoinAddress(input);
        } else {
            Toast.makeText(this, R.string.error_invalid_qr, Toast.LENGTH_SHORT).show();
            resumeScannerIfPermitted();
        }
    }

    // ── Lightning BOLT11 ──────────────────────────────────────────────────────

    private void handleLightningInvoice(String input) {
        String invoice = input;
        if (invoice.toLowerCase(Locale.US).startsWith("lightning:")) {
            invoice = invoice.substring(10);
        }
        Double btc = parseBolt11Amount(invoice);
        scannedType = PaymentType.LIGHTNING;
        showPaymentDetails(invoice, btc, PaymentType.LIGHTNING);
    }

    private static Double parseBolt11Amount(String bolt11) {
        Matcher m = BOLT11_AMOUNT.matcher(bolt11);
        if (!m.find()) return null;
        String digits = m.group(1);
        String mult = m.group(2);
        if (digits == null || digits.isEmpty()) return null;
        double amount = Double.parseDouble(digits);
        if (mult != null) {
            switch (mult.toLowerCase(Locale.US)) {
                case "m": amount *= 1e-3;  break;
                case "u": amount *= 1e-6;  break;
                case "n": amount *= 1e-9;  break;
                case "p": amount *= 1e-12; break;
            }
        }
        return amount;
    }

    // ── Bitcoin on-chain / BIP21 ──────────────────────────────────────────────

    private void handleBitcoinAddress(String input) {
        String address;
        Double btc = null;

        if (input.toLowerCase(Locale.US).startsWith("bitcoin:")) {
            String withoutScheme = input.substring(8);
            int queryStart = withoutScheme.indexOf('?');
            if (queryStart >= 0) {
                address = withoutScheme.substring(0, queryStart);
                btc = parseBip21Amount(withoutScheme.substring(queryStart + 1));
            } else {
                address = withoutScheme;
            }
        } else {
            address = input;
        }

        scannedType = PaymentType.BITCOIN_ONCHAIN;
        showPaymentDetails(address, btc, PaymentType.BITCOIN_ONCHAIN);
    }

    private static Double parseBip21Amount(String query) {
        for (String param : query.split("&")) {
            if (param.toLowerCase(Locale.US).startsWith("amount=")) {
                try {
                    return Double.parseDouble(param.substring(7));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    // ── BTCPay checkout URL ───────────────────────────────────────────────────

    private void handleBtcPayUrl(String invoiceId) {
        if (!INVOICE_ID_VALID.matcher(invoiceId).matches()) {
            Toast.makeText(this, R.string.error_invalid_qr, Toast.LENGTH_SHORT).show();
            resumeScannerIfPermitted();
            return;
        }
        scannedType = PaymentType.BTCPAY_INVOICE;
        pendingBtcPayUsd = 0;

        binding.tvInvoiceTypeLabel.setText(R.string.label_btcpay_fetching);
        binding.tvNetworkValue.setText(R.string.value_network);
        binding.tvInvoiceBtc.setText(R.string.label_price_loading);
        binding.tvInvoiceUsd.setText("");
        binding.tvInvoiceId.setText(invoiceId);
        binding.layoutScan.setVisibility(View.GONE);
        binding.layoutInvoiceDetails.setVisibility(View.VISIBLE);
        binding.btnPayNow.setEnabled(false);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = BuildConfig.BTCPAY_URL + "/api/v1/stores/"
                        + BuildConfig.BTCPAY_STORE_ID + "/invoices/" + invoiceId;
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestProperty("Authorization", "token " + BuildConfig.BTCPAY_API_KEY);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    JSONObject json = new JSONObject(readStream(conn.getInputStream()));
                    double invoiceAmount = json.getDouble("amount");
                    String currency = json.optString("currency", "USD");

                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Double btc = null;
                        if ("BTC".equalsIgnoreCase(currency)) {
                            btc = invoiceAmount;
                        } else if ("USD".equalsIgnoreCase(currency) && btcUsdRate > 0) {
                            btc = invoiceAmount / btcUsdRate;
                        } else if ("USD".equalsIgnoreCase(currency)) {
                            pendingBtcPayUsd = invoiceAmount;
                        }
                        showPaymentDetails(invoiceId, btc, PaymentType.BTCPAY_INVOICE);
                    });
                } else {
                    InputStream errStream = conn.getErrorStream();
                    String errBody = errStream != null ? readStream(errStream) : "";
                    final int finalCode = code;
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this,
                                getString(R.string.error_btcpay_fetch, finalCode),
                                Toast.LENGTH_SHORT).show();
                        resetToScanning();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this,
                            e.getMessage() != null ? e.getMessage()
                                    : getString(R.string.error_btcpay_invoice),
                            Toast.LENGTH_SHORT).show();
                    resetToScanning();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static String readStream(InputStream is) {
        Scanner sc = new Scanner(is, "UTF-8").useDelimiter("\\A");
        String result = sc.hasNext() ? sc.next() : "";
        sc.close();
        return result;
    }

    // ── Shared payment details UI ─────────────────────────────────────────────

    private void showPaymentDetails(String id, Double btc, PaymentType type) {
        scannedInvoice = id;
        scannedBtcAmount = btc != null ? btc : 0;

        switch (type) {
            case LIGHTNING:
                binding.tvInvoiceTypeLabel.setText(R.string.label_invoice_valid);
                binding.tvNetworkValue.setText(R.string.value_network);
                break;
            case BITCOIN_ONCHAIN:
                binding.tvInvoiceTypeLabel.setText(R.string.label_address_valid);
                binding.tvNetworkValue.setText(R.string.value_network_onchain);
                break;
            case BTCPAY_INVOICE:
                binding.tvInvoiceTypeLabel.setText(R.string.label_btcpay_invoice_valid);
                binding.tvNetworkValue.setText(R.string.value_network);
                break;
        }

        if (btc != null && btc > 0) {
            binding.tvBtcAmount.setText(String.format(Locale.US, "%.8f", btc));
            binding.tvInvoiceBtc.setText(String.format(Locale.US, "%.8f BTC", btc));
            if (btcUsdRate > 0) {
                double usd = btc * btcUsdRate;
                String usdStr = getString(R.string.format_usd_equivalent, usd);
                binding.tvUsdEquivalent.setText(usdStr);
                binding.tvInvoiceUsd.setText(usdStr);
            } else {
                binding.tvInvoiceUsd.setText("");
            }
        } else {
            switch (type) {
                case LIGHTNING:
                    binding.tvInvoiceBtc.setText(R.string.error_invoice_no_amount);
                    break;
                case BITCOIN_ONCHAIN:
                    binding.tvInvoiceBtc.setText(R.string.error_address_no_amount);
                    break;
                case BTCPAY_INVOICE:
                    binding.tvInvoiceBtc.setText(R.string.label_price_loading);
                    break;
            }
            binding.tvInvoiceUsd.setText("");
        }

        binding.tvInvoiceId.setText(id);
        binding.layoutScan.setVisibility(View.GONE);
        binding.layoutInvoiceDetails.setVisibility(View.VISIBLE);
        binding.btnPayNow.setEnabled(btc != null && btc > 0);
    }

    private void resetToScanning() {
        stopPolling();
        paymentLink = null;
        paymentFirestoreDocId = null;

        scannedInvoice = null;
        scannedBtcAmount = 0;
        scannedType = null;
        pendingBtcPayUsd = 0;

        binding.layoutPaymentConfirm.setVisibility(View.GONE);
        binding.layoutPaymentSent.setVisibility(View.GONE);
        binding.layoutInvoiceDetails.setVisibility(View.GONE);
        binding.layoutScan.setVisibility(View.VISIBLE);
        binding.tvBtcAmount.setText(R.string.label_btc_amount);
        binding.tvUsdEquivalent.setText(R.string.label_usd_equivalent);
        binding.btnPayNow.setText(R.string.btn_pay_now);
        binding.btnPayNow.setEnabled(false);
        resumeScannerIfPermitted();
    }

    // ── Back navigation through overlays ─────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (binding.layoutPaymentSent.getVisibility() == View.VISIBLE) {
            resetToScanning();
        } else if (binding.layoutPaymentConfirm.getVisibility() == View.VISIBLE) {
            onCancelPayment();
        } else {
            super.onBackPressed();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_pay);
        if (binding.layoutScan.getVisibility() == View.VISIBLE) {
            resumeScannerIfPermitted();
        }
        PriceService.get().addListener(priceListener);
        // Resume polling if user comes back from wallet app and payment is still pending
        if (!pollingStopped && scannedInvoice != null) {
            mainHandler.postDelayed(invoicePollRunnable, POLL_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseScanner();
        PriceService.get().removeListener(priceListener);
        mainHandler.removeCallbacks(invoicePollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollingStopped = true;
        mainHandler.removeCallbacks(invoicePollRunnable);
    }
}
