package com.cyberpos.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
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

import java.util.Date;
import java.util.Locale;

public class PaymentActivity extends AppCompatActivity {

    private static final int QR_SIZE = 600;

    private ActivityPaymentBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private double btcUsdRate = 0;

    private final PriceService.Listener priceListener = price -> {
        btcUsdRate = price;
        binding.tvLivePrice.setText(
                String.format(Locale.US, "$%,.0f USD", price));
        updateBtcEquivalent();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        binding.btnGenerateQr.setOnClickListener(v -> generatePayment());
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
            double btc = usd / btcUsdRate;
            binding.tvBtcEquivalent.setText(
                    String.format(Locale.US, "≈ %.8f BTC", btc));
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        PriceService.get().removeListener(priceListener);
    }

    private void generatePayment() {
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

        String lightningUri = buildLightningUri(amount, description);
        generateQrCode(lightningUri);
        savePaymentToFirestore(amount, description, lightningUri);
    }

    private String buildLightningUri(double usdAmount, String description) {
        // Convert USD to satoshis using live rate when available
        long satoshis = btcUsdRate > 0
                ? Math.round((usdAmount / btcUsdRate) * 100_000_000L)
                : Math.round(usdAmount * 100);
        String safeDesc = description.isEmpty() ? "payment" : description.replace(" ", "%20");
        return "lightning:lnbc" + satoshis + "u1placeholder_" + safeDesc;
    }

    private void generateQrCode(String content) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            binding.ivQrCode.setImageBitmap(bitmap);
            binding.ivQrCode.setVisibility(View.VISIBLE);
            binding.tvQrInstructions.setVisibility(View.VISIBLE);
            setLoading(false);
        } catch (WriterException e) {
            setLoading(false);
            Toast.makeText(this, R.string.error_qr_generation, Toast.LENGTH_SHORT).show();
        }
    }

    private void savePaymentToFirestore(double amount, String description, String invoice) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";
        Payment payment = new Payment(uid, amount, description, invoice, new Date());

        db.collection("payments")
                .add(payment)
                .addOnSuccessListener(ref -> {})
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_save_payment, Toast.LENGTH_SHORT).show());
    }

    private void setLoading(boolean loading) {
        binding.btnGenerateQr.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
