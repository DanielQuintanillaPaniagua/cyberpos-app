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
import android.graphics.BitmapFactory;
import android.nfc.NfcAdapter;
import android.util.Base64;
import android.graphics.Bitmap;
import android.net.Uri;
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
import androidx.core.content.FileProvider;

import com.cyberpos.app.databinding.ActivityPaymentBinding;
import com.cyberpos.app.model.CartItem;
import com.cyberpos.app.model.CartTotals;
import com.cyberpos.app.model.Payment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT          = "extra_amount";
    public static final String EXTRA_CART_ITEMS      = "extra_cart_items";
    public static final String EXTRA_IVA_PERCENT     = "extra_iva_percent";
    public static final String EXTRA_ISR_PERCENT     = "extra_isr_percent";
    public static final String EXTRA_DISCOUNT_TYPE   = "extra_discount_type";
    public static final String EXTRA_DISCOUNT_VALUE  = "extra_discount_value";
    public static final String EXTRA_CASH_AMOUNT     = "extra_cash_amount";

    private static final int QR_SIZE = 600;
    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final int REQ_BT_PRINT = 102;

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

    private List<CartItem> cartItemsFromIntent = null;
    private double ivaPercent = 0;
    private double isrPercent = 0;
    private String discountType = "";
    private double discountValue = 0;
    private double cashAmountUsd = 0;
    private Bitmap merchantLogo = null;

    private static final int RC_WRITE_STORAGE = 43;

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
        binding.btnShareReceipt.setOnClickListener(v -> shareReceipt());
        binding.btnSaveReceipt.setOnClickListener(v -> saveReceipt());
        loadMerchantName();
        loadLogoFromFirestore();

        nfcAdapter  = NfcAdapter.getDefaultAdapter(this);
        nfcAvailable = nfcAdapter != null;
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) { updateBtcEquivalent(); }
        });
        setupBottomNav();
        handleCartExtras();
    }

    @SuppressWarnings("unchecked")
    private void handleCartExtras() {
        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_AMOUNT)) return;
        double amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0);
        cartItemsFromIntent = (List<CartItem>) intent.getSerializableExtra(EXTRA_CART_ITEMS);
        ivaPercent = intent.getDoubleExtra(EXTRA_IVA_PERCENT, 0);
        isrPercent = intent.getDoubleExtra(EXTRA_ISR_PERCENT, 0);
        discountType = intent.getStringExtra(EXTRA_DISCOUNT_TYPE);
        if (discountType == null) discountType = "";
        discountValue = intent.getDoubleExtra(EXTRA_DISCOUNT_VALUE, 0);
        cashAmountUsd = intent.getDoubleExtra(EXTRA_CASH_AMOUNT, 0);

        binding.etAmount.setText(String.format(Locale.US, "%.2f", amount));
        if (cartItemsFromIntent != null && !cartItemsFromIntent.isEmpty()) {
            binding.etDescription.setText(buildCartDescription(cartItemsFromIntent));
        }
        if (cashAmountUsd > 0) {
            binding.tvMixedInfo.setText(getString(R.string.label_mixed_payment_info, cashAmountUsd, amount));
            binding.tvMixedInfo.setVisibility(View.VISIBLE);
        }
        binding.getRoot().post(this::handleGenerateQr);
    }

    private static String buildCartDescription(List<CartItem> items) {
        if (items.size() == 1) return items.get(0).getNombre();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(items.size(), 3); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i).getNombre());
        }
        if (items.size() > 3) sb.append(" +").append(items.size() - 3).append(" más");
        return sb.toString();
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
            if (id == R.id.nav_cobros) {
                startActivity(new Intent(this, CatalogoActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_history) {
                startActivity(new Intent(this, MerchantHistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_dashboard) {
                startActivity(new Intent(this, DashboardActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_settings) {
                startActivity(new Intent(this, MerchantAjustesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        BtcPayClient.createInvoice(amount, description, new BtcPayClient.Callback<BtcPayClient.Invoice>() {
            @Override
            public void onSuccess(BtcPayClient.Invoice invoice) {
                if (isFinishing() || isDestroyed()) return;
                if (!invoice.checkoutLink.isEmpty()) {
                    currentInvoiceId = invoice.id;
                    pollingStopped = false;
                    showQrAndStartPolling(invoice.checkoutLink, amount, description, invoice.id);
                } else {
                    setLoading(false);
                    Toast.makeText(PaymentActivity.this, R.string.error_btcpay_invoice,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(BtcPayClient.BtcPayException e) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(PaymentActivity.this,
                        e.userMessage(getString(R.string.error_btcpay_invoice)),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Generación de QR y sondeo / QR generation & polling ──────────────────

    private void showQrAndStartPolling(String checkoutUrl, double amount,
                                       String description, String invoiceId) {
        // ES: Capturar datos del pago para el recibo
        // EN: Capture payment data for receipt
        currentAmountUsd  = amount + cashAmountUsd;
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
        BtcPayClient.getInvoice(currentInvoiceId, new BtcPayClient.Callback<BtcPayClient.Invoice>() {
            @Override
            public void onSuccess(BtcPayClient.Invoice invoice) {
                if (isFinishing() || isDestroyed()) return;
                if ("Settled".equals(invoice.status)) {
                    onPaymentSettled();
                } else if (!pollingStopped) {
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                }
            }

            @Override
            public void onError(BtcPayClient.BtcPayException e) {
                if (!pollingStopped && !isFinishing() && !isDestroyed()) {
                    handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
                }
            }
        });
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
        descontarStock();

        binding.scrollViewForm.setVisibility(View.GONE);
        binding.layoutPaymentSuccess.setVisibility(View.VISIBLE);
    }

    // ── Descuento de stock (F9) / Stock deduction (F9) ───────────────────────

    private void descontarStock() {
        if (cartItemsFromIntent == null || cartItemsFromIntent.isEmpty()
                || auth.getCurrentUser() == null) {
            return;
        }
        String uid = auth.getCurrentUser().getUid();
        for (CartItem item : cartItemsFromIntent) {
            String productoId = item.getProductoId();
            int cantidad = item.getCantidad();
            if (productoId == null || productoId.isEmpty() || cantidad <= 0) continue;

            com.google.firebase.firestore.DocumentReference ref = db.collection("users")
                    .document(uid).collection("productos").document(productoId);
            db.runTransaction(transaction -> {
                com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(ref);
                Long stock = snap.getLong("stock");
                if (stock != null && stock >= 0) {
                    long nuevoStock = Math.max(0, stock - cantidad);
                    transaction.update(ref, "stock", nuevoStock);
                }
                return null;
            });
        }
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
        // ES: "amount" es solo la porción facturada en Bitcoin (F10); el total de la venta
        //     incluye además el efectivo recibido.
        // EN: "amount" is only the BTC-invoiced portion (F10); the sale total also includes
        //     the cash received.
        double totalUsd = amount + cashAmountUsd;
        Payment payment = new Payment(uid, totalUsd, amountBtc, "", description, bolt11, invoiceId);
        payment.setMerchantName(merchantBusinessName);
        payment.setIvaPercent(ivaPercent);
        payment.setIsrPercent(isrPercent);
        payment.setDiscountType(discountType);
        payment.setDiscountValue(discountValue);
        payment.setCashAmountUsd(cashAmountUsd);
        payment.setPaymentType(cashAmountUsd > 0 ? "mixto" : "bitcoin");
        payment.setDiscountUsd(CartTotals.compute(
                cartItemsFromIntent, discountType, discountValue, ivaPercent, isrPercent).descuento);

        if (cartItemsFromIntent != null && !cartItemsFromIntent.isEmpty()) {
            List<Map<String, Object>> cartItemsMap = new ArrayList<>();
            for (CartItem item : cartItemsFromIntent) cartItemsMap.add(item.toMap());
            payment.setCartItems(cartItemsMap);
        }

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

    // ── Guardar recibo / Save receipt ────────────────────────────────────────

    private void saveReceipt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_WRITE_STORAGE);
            return;
        }
        doGenerateAndSave();
    }

    private void doGenerateAndSave() {
        binding.btnSaveReceipt.setEnabled(false);
        Toast.makeText(this, R.string.msg_generating_receipt, Toast.LENGTH_SHORT).show();

        final String biz    = merchantBusinessName;
        final String invId  = currentInvoiceId;
        final double usd    = currentAmountUsd;
        final double btc    = currentAmountBtc;
        final List<CartItem> items = cartItemsFromIntent;
        final double iva    = ivaPercent;
        final double isr    = isrPercent;
        final String discT  = discountType;
        final double discV   = discountValue;
        final double cashV   = cashAmountUsd;
        final Bitmap logo   = merchantLogo;

        new Thread(() -> {
            try {
                java.io.File pdf = ReceiptGenerator.generate(
                        this, biz, invId, usd, btc, items, iva, isr, discT, discV, cashV, logo);
                ReceiptGenerator.saveToDownloads(this, pdf);
                runOnUiThread(() -> {
                    binding.btnSaveReceipt.setEnabled(true);
                    Toast.makeText(this, R.string.msg_receipt_saved, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.btnSaveReceipt.setEnabled(true);
                    Toast.makeText(this, R.string.error_receipt_generation, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ── Logo del comerciante / Merchant logo ──────────────────────────────────

    private void loadLogoFromFirestore() {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("configuracion").document("pantalla_cobro")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String b64 = doc.getString("logo");
                    if (b64 == null) b64 = doc.getString("logo_base64");
                    if (b64 == null || b64.isEmpty()) return;
                    final String finalB64 = b64;
                    new Thread(() -> {
                        try {
                            byte[] bytes = Base64.decode(finalB64, Base64.DEFAULT);
                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bmp != null) {
                                merchantLogo = Bitmap.createScaledBitmap(bmp, 64, 64, true);
                            }
                        } catch (Exception ignored) {}
                    }).start();
                });
    }

    // ── Recibo digital / Digital receipt ─────────────────────────────────────

    private void shareReceipt() {
        binding.btnShareReceipt.setEnabled(false);
        Toast.makeText(this, R.string.msg_generating_receipt, Toast.LENGTH_SHORT).show();

        final String biz     = merchantBusinessName;
        final String invId   = currentInvoiceId;
        final double usd     = currentAmountUsd;
        final double btc     = currentAmountBtc;
        final List<CartItem> items = cartItemsFromIntent;
        final double iva     = ivaPercent;
        final double isr     = isrPercent;
        final String discT   = discountType;
        final double discV   = discountValue;
        final double cashV   = cashAmountUsd;
        final Bitmap logo    = merchantLogo;

        new Thread(() -> {
            try {
                java.io.File pdf = ReceiptGenerator.generate(
                        this, biz, invId, usd, btc, items, iva, isr, discT, discV, cashV, logo);
                runOnUiThread(() -> {
                    binding.btnShareReceipt.setEnabled(true);
                    Uri uri = FileProvider.getUriForFile(
                            this, "com.cyberpos.app.fileprovider", pdf);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/pdf");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(
                            share, getString(R.string.msg_share_receipt)));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.btnShareReceipt.setEnabled(true);
                    Toast.makeText(this, R.string.error_receipt_generation, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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
        } else if (code == RC_WRITE_STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                doGenerateAndSave();
            } else {
                Toast.makeText(this, R.string.error_receipt_generation, Toast.LENGTH_SHORT).show();
            }
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
                cashAmountUsd,
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
}
