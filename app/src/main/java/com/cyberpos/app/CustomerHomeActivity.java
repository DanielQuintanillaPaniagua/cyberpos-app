package com.cyberpos.app;

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
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cyberpos.app.databinding.ActivityCustomerHomeBinding;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomerHomeActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final double WALLET_BTC = 0.00150000;

    private enum PaymentType { LIGHTNING, BITCOIN_ONCHAIN, BTCPAY_INVOICE }

    // BOLT11 prefix pattern: ln + chain + amount_digits + optional_multiplier + "1"
    private static final Pattern BOLT11_AMOUNT =
            Pattern.compile("^ln(?:bc|tb|bcrt)(\\d+)([munp])?1", Pattern.CASE_INSENSITIVE);

    // BTCPay checkout URL: http(s)://host/i/INVOICEID
    private static final Pattern BTCPAY_CHECKOUT_URL =
            Pattern.compile("^https?://[^/]+/i/([^/?&#]+)", Pattern.CASE_INSENSITIVE);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityCustomerHomeBinding binding;
    private boolean scannerResumed = false;
    private double btcUsdRate = 0;
    private String scannedInvoice = null;
    private double scannedBtcAmount = 0;
    private PaymentType scannedType = null;
    // Holds the USD amount from a BTCPay invoice fetched before the live rate was ready
    private double pendingBtcPayUsd = 0;

    private final PriceService.Listener priceListener = price -> {
        btcUsdRate = price;
        binding.tvLivePrice.setText(
                String.format(Locale.US, "1 BTC = $%,.0f  ● LIVE", price));
        binding.tvLivePrice.setTextColor(getColor(R.color.neon_green));
        binding.tvWalletUsd.setText(
                String.format(Locale.US, "≈ $%.2f USD", WALLET_BTC * price));

        // BTCPay invoice arrived before the rate: compute BTC now
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

        setupTabs();
        setupAmountInput();
        setupPayButton();
        setupBottomNav();
        binding.btnScanAgain.setOnClickListener(v -> resetToScanning());
        binding.btnOpenSettings.setOnClickListener(v -> openAppSettings());
        requestCameraIfNeeded();
    }

    // ── Tabs ────────────────────────────────────────────────────────────────

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
                    // Result already scanned — keep details visible
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

    // ── Amount input (manual tab) ────────────────────────────────────────────

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

    // ── Pay button ───────────────────────────────────────────────────────────

    private void setupPayButton() {
        binding.btnPayNow.setOnClickListener(v ->
                Toast.makeText(this, R.string.processing_payment, Toast.LENGTH_SHORT).show());
    }

    // ── Bottom nav ───────────────────────────────────────────────────────────

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

    // ── Camera / scanner ─────────────────────────────────────────────────────

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

    // ── QR dispatch ──────────────────────────────────────────────────────────

    private void handleScannedQr(String raw) {
        Toast.makeText(this, "QR: " + raw, Toast.LENGTH_LONG).show();
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
        scannedType = PaymentType.BTCPAY_INVOICE;
        pendingBtcPayUsd = 0;

        // Show loading state immediately while the API call is in flight
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
                            // Rate not loaded yet — stash and let priceListener finish
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
                    // BTC amount is being calculated (rate not ready yet)
                    binding.tvInvoiceBtc.setText(R.string.label_price_loading);
                    break;
            }
            binding.tvInvoiceUsd.setText("");
        }

        binding.tvInvoiceId.setText(id);
        binding.layoutScan.setVisibility(View.GONE);
        binding.layoutInvoiceDetails.setVisibility(View.VISIBLE);
        // Enable pay only once we have an actual BTC amount
        binding.btnPayNow.setEnabled(btc != null && btc > 0);
    }

    private void resetToScanning() {
        scannedInvoice = null;
        scannedBtcAmount = 0;
        scannedType = null;
        pendingBtcPayUsd = 0;
        binding.layoutInvoiceDetails.setVisibility(View.GONE);
        binding.layoutScan.setVisibility(View.VISIBLE);
        binding.tvBtcAmount.setText(R.string.label_btc_amount);
        binding.tvUsdEquivalent.setText(R.string.label_usd_equivalent);
        binding.btnPayNow.setEnabled(false);
        resumeScannerIfPermitted();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_pay);
        if (binding.layoutScan.getVisibility() == View.VISIBLE) {
            resumeScannerIfPermitted();
        }
        PriceService.get().addListener(priceListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseScanner();
        PriceService.get().removeListener(priceListener);
    }
}
