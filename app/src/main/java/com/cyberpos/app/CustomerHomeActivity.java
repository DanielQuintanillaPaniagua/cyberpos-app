package com.cyberpos.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

public class CustomerHomeActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final double WALLET_BTC = 0.00150000;

    private ActivityCustomerHomeBinding binding;
    private boolean scannerResumed = false;
    private double btcUsdRate = 0;

    private final PriceService.Listener priceListener = price -> {
        btcUsdRate = price;
        binding.tvLivePrice.setText(
                String.format(Locale.US, "1 BTC = $%,.0f  ● LIVE", price));
        binding.tvLivePrice.setTextColor(getColor(R.color.neon_green));
        binding.tvWalletUsd.setText(
                String.format(Locale.US, "≈ $%.2f USD", WALLET_BTC * price));
        refreshAmountUsd();
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
        requestCameraIfNeeded();
    }

    private void setupTabs() {
        binding.toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean isScan = checkedId == R.id.tabScan;
            binding.layoutScan.setVisibility(isScan ? View.VISIBLE : View.GONE);
            binding.layoutAmount.setVisibility(isScan ? View.GONE : View.VISIBLE);
            if (isScan) {
                resumeScannerIfPermitted();
            } else {
                pauseScanner();
            }
        });
        binding.toggleGroup.check(R.id.tabScan);
    }

    private void setupAmountInput() {
        binding.etBtcAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override
            public void afterTextChanged(Editable s) {
                String raw = s.toString();
                binding.tvBtcAmount.setText(raw.isEmpty() ? getString(R.string.label_btc_amount) : raw);
                refreshAmountUsd();
            }
        });
    }

    private void refreshAmountUsd() {
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

    private void setupPayButton() {
        binding.btnPayNow.setOnClickListener(v ->
                Toast.makeText(this, R.string.processing_payment, Toast.LENGTH_SHORT).show());
    }

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

    private void handleScannedInvoice(String invoice) {
        binding.tvBtcAmount.setText(getString(R.string.label_btc_amount));
        Toast.makeText(this,
                getString(R.string.invoice_scanned,
                        invoice.substring(0, Math.min(20, invoice.length()))),
                Toast.LENGTH_SHORT).show();
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
