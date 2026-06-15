package com.cyberpos.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomerHomeActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final double WALLET_BTC = 0.00150000;

    // BOLT11 prefix pattern: ln + chain + amount_digits + optional_multiplier + "1"
    private static final Pattern BOLT11_AMOUNT =
            Pattern.compile("^ln(?:bc|tb|bcrt)(\\d+)([munp])?1",
                    Pattern.CASE_INSENSITIVE);

    private ActivityCustomerHomeBinding binding;
    private boolean scannerResumed = false;
    private double btcUsdRate = 0;
    private String scannedInvoice = null;
    private double scannedBtcAmount = 0;

    private final PriceService.Listener priceListener = price -> {
        btcUsdRate = price;
        binding.tvLivePrice.setText(
                String.format(Locale.US, "1 BTC = $%,.0f  ● LIVE", price));
        binding.tvLivePrice.setTextColor(getColor(R.color.neon_green));
        binding.tvWalletUsd.setText(
                String.format(Locale.US, "≈ $%.2f USD", WALLET_BTC * price));
        // Keep invoice USD in sync if one is already displayed
        if (scannedInvoice != null && scannedBtcAmount > 0) {
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
                    // Invoice was already scanned — keep details visible
                    binding.layoutScan.setVisibility(View.GONE);
                    binding.layoutInvoiceDetails.setVisibility(View.VISIBLE);
                    binding.btnPayNow.setEnabled(true);
                }
            } else {
                pauseScanner();
                binding.layoutScan.setVisibility(View.GONE);
                binding.layoutInvoiceDetails.setVisibility(View.GONE);
                binding.btnPayNow.setEnabled(true);
            }
        });
        binding.btnPayNow.setEnabled(false); // disabled until scan tab produces a valid invoice
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
        if (scannedInvoice != null) return; // scan result owns the top display
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
                handleScannedInvoice(result.getText());
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

    // ── BOLT11 invoice handling ───────────────────────────────────────────────

    private void handleScannedInvoice(String raw) {
        String invoice = raw.trim();
        if (invoice.toLowerCase(Locale.US).startsWith("lightning:")) {
            invoice = invoice.substring(10);
        }

        if (!invoice.toLowerCase(Locale.US).startsWith("ln")) {
            Toast.makeText(this, R.string.error_invalid_invoice, Toast.LENGTH_SHORT).show();
            resumeScannerIfPermitted();
            return;
        }

        Double btc = parseBolt11Amount(invoice);
        scannedInvoice = invoice;
        scannedBtcAmount = btc != null ? btc : 0;

        // Update top display
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
            // Amount-less invoice: show placeholder
            binding.tvInvoiceBtc.setText(R.string.error_invoice_no_amount);
            binding.tvInvoiceUsd.setText("");
        }

        binding.tvInvoiceId.setText(invoice);

        // Transition: hide camera area, show invoice panel
        binding.layoutScan.setVisibility(View.GONE);
        binding.layoutInvoiceDetails.setVisibility(View.VISIBLE);
        binding.btnPayNow.setEnabled(true);
    }

    // Returns BTC amount parsed from a BOLT11 prefix, or null if not parseable.
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

    private void resetToScanning() {
        scannedInvoice = null;
        scannedBtcAmount = 0;
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
