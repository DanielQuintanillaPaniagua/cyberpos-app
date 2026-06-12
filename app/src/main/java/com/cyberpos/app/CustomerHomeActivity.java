package com.cyberpos.app;

import android.Manifest;
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

public class CustomerHomeActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final double BTC_USD_RATE = 59500.0;

    private ActivityCustomerHomeBinding binding;
    private boolean scannerResumed = false;

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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    double btc = Double.parseDouble(s.toString());
                    double usd = btc * BTC_USD_RATE;
                    binding.tvAmountUsd.setText(getString(R.string.format_usd_equivalent, usd));
                    binding.tvBtcAmount.setText(s.toString());
                    binding.tvUsdEquivalent.setText(getString(R.string.format_usd_equivalent, usd));
                } catch (NumberFormatException e) {
                    binding.tvAmountUsd.setText(R.string.label_usd_equivalent);
                }
            }
        });
    }

    private void setupPayButton() {
        binding.btnPayNow.setOnClickListener(v ->
                Toast.makeText(this, R.string.processing_payment, Toast.LENGTH_SHORT).show());
    }

    private void setupBottomNav() {
        binding.bottomNav.setSelectedItemId(R.id.nav_pay);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history || id == R.id.nav_settings) {
                Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
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
        // TODO: Decode BOLT11 invoice and populate BTC/USD amounts
        binding.tvBtcAmount.setText(getString(R.string.label_btc_amount));
        Toast.makeText(this, getString(R.string.invoice_scanned, invoice.substring(0, Math.min(20, invoice.length()))), Toast.LENGTH_SHORT).show();
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
        if (binding.layoutScan.getVisibility() == View.VISIBLE) {
            resumeScannerIfPermitted();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseScanner();
    }
}
