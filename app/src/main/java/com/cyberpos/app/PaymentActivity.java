package com.cyberpos.app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
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

public class PaymentActivity extends AppCompatActivity {

    private static final int QR_SIZE = 600;

    private ActivityPaymentBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_new_payment);
        }

        binding.btnGenerateQr.setOnClickListener(v -> generatePayment());
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

        // Build a Lightning invoice URI (BOLT11 placeholder — replace with real BTCPay Server call)
        String lightningUri = buildLightningUri(amount, description);

        generateQrCode(lightningUri);
        savePaymentToFirestore(amount, description, lightningUri);
    }

    private String buildLightningUri(double amount, String description) {
        // TODO: Integrate with BTCPay Server API to generate a real BOLT11 invoice
        // Format: lightning:<BOLT11_INVOICE>
        // For now returns a placeholder URI for UI development
        String safeDesc = description.isEmpty() ? "payment" : description.replace(" ", "%20");
        return "lightning:lnbc" + (long)(amount * 100) + "n1placeholder_" + safeDesc;
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
                .addOnSuccessListener(ref -> {
                    // Payment saved — no UI action needed
                })
                .addOnFailureListener(e -> {
                    // Non-critical: QR is already shown; log silently
                    Toast.makeText(this, R.string.error_save_payment, Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.btnGenerateQr.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
