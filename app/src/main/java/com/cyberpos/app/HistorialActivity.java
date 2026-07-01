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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyberpos.app.databinding.ActivityHistorialBinding;
import com.cyberpos.app.model.CartItem;
import com.cyberpos.app.model.Payment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorialActivity extends AppCompatActivity {

    private ActivityHistorialBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private CustomerTxAdapter adapter;
    private final List<ListItem> items = new ArrayList<>();

    private CustomerTx pendingSaveTx = null;
    private static final int RC_WRITE_STORAGE = 43;

    // ES: Puntos de lealtad del cliente, por merchantId (F13)
    // EN: Customer's loyalty points, by merchantId (F13)
    private final Map<String, Long> pointsByMerchant = new HashMap<>();

    // ES: Últimos pagos cargados, para re-formatear cuando cambie la tasa (p. ej. al llegar EUR)
    // EN: Last loaded payments, to re-format when the rate changes (e.g. once EUR arrives)
    private List<Payment> lastPayments = null;
    private final PriceService.Listener priceListener = price -> {
        if (lastPayments != null) updateUI(lastPayments);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistorialBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new CustomerTxAdapter(items, pointsByMerchant, this::onReceiptButtonClick);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_history);
        PriceService.get().addListener(priceListener);
        loadPayments();
        loadLoyaltyPoints();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PriceService.get().removeListener(priceListener);
    }

    // ── Firestore / Firestore ─────────────────────────────────────────────────

    private void loadPayments() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("payments")
                .whereEqualTo("payerUid", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Payment> payments = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Payment p = doc.toObject(Payment.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            payments.add(p);
                        }
                    }
                    payments.sort((a, b) -> {
                        Date da = a.getCreatedAt(), db2 = b.getCreatedAt();
                        if (da == null) return 1;
                        if (db2 == null) return -1;
                        return db2.compareTo(da);
                    });
                    updateUI(payments);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_payments, Toast.LENGTH_SHORT).show());
    }

    private void updateUI(List<Payment> payments) {
        lastPayments = payments;
        double totalBtc = 0, totalUsd = 0;
        int txThisMonth = 0;
        Calendar now = Calendar.getInstance();
        int thisMonth = now.get(Calendar.MONTH);
        int thisYear  = now.get(Calendar.YEAR);

        for (Payment p : payments) {
            if ("settled".equals(p.getStatus())) {
                totalBtc += p.getAmountBtc();
                totalUsd += p.getAmountUsd();
                if (p.getCreatedAt() != null) {
                    Calendar c = Calendar.getInstance();
                    c.setTime(p.getCreatedAt());
                    if (c.get(Calendar.MONTH) == thisMonth && c.get(Calendar.YEAR) == thisYear) {
                        txThisMonth++;
                    }
                }
            }
        }

        binding.tvTotalBtc.setText(String.format(Locale.US, "-%.8f BTC", totalBtc));
        binding.tvTotalUsd.setText(MoneyFormatter.historyEquivalent(this, totalBtc, totalUsd));
        binding.tvTxCount.setText(String.valueOf(txThisMonth));

        items.clear();

        if (payments.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
            buildGroupedList(payments);
        }

        adapter.notifyDataSetChanged();
    }

    private void buildGroupedList(List<Payment> payments) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy", new Locale("es", "MX"));
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

        String currentHeader = null;

        for (Payment payment : payments) {
            Date ts = payment.getCreatedAt();
            if (ts == null) continue;

            Calendar payDate = Calendar.getInstance();
            payDate.setTime(ts);
            payDate.set(Calendar.HOUR_OF_DAY, 0);
            payDate.set(Calendar.MINUTE, 0);
            payDate.set(Calendar.SECOND, 0);
            payDate.set(Calendar.MILLISECOND, 0);

            String headerLabel;
            if (payDate.equals(today)) {
                headerLabel = "HOY";
            } else if (payDate.equals(yesterday)) {
                headerLabel = "AYER";
            } else {
                headerLabel = dateFmt.format(ts).toUpperCase(Locale.getDefault());
            }

            if (!headerLabel.equals(currentHeader)) {
                items.add(ListItem.header(headerLabel));
                currentHeader = headerLabel;
            }

            String displayName = payment.getDescription();
            if (displayName == null || displayName.isEmpty()) {
                displayName = payment.getBtcPayInvoiceId();
            }
            if (displayName == null || displayName.isEmpty()) {
                displayName = "Pago Bitcoin";
            }

            items.add(ListItem.tx(new CustomerTx(
                    displayName,
                    timeFmt.format(ts),
                    payment.getAmountBtc(),
                    payment.getAmountUsd(),
                    payment.getStatus(),
                    payment.getBtcPayInvoiceId(),
                    payment.getMerchantName(),
                    payment.getCartItems(),
                    payment.getIvaPercent(),
                    payment.getIsrPercent(),
                    payment.getDiscountType(),
                    payment.getDiscountValue(),
                    payment.getId(),
                    payment.getMerchantId(),
                    payment.isRated())));
        }
    }

    // ── Puntos de lealtad (F13) / Loyalty points (F13) ───────────────────────

    private void loadLoyaltyPoints() {
        if (auth.getCurrentUser() == null) return;
        db.collection("customers").document(auth.getCurrentUser().getUid())
                .collection("puntos")
                .get()
                .addOnSuccessListener(snapshot -> {
                    pointsByMerchant.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Long puntos = doc.getLong("puntos");
                        if (puntos != null) pointsByMerchant.put(doc.getId(), puntos);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    // ── Recibo / Receipt actions ──────────────────────────────────────────────

    private void onReceiptButtonClick(CustomerTx tx, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.menu_share_receipt));
        popup.getMenu().add(0, 2, 1, getString(R.string.menu_save_receipt));
        boolean canRate = "settled".equals(tx.status) && !tx.rated
                && tx.merchantId != null && !tx.merchantId.isEmpty()
                && tx.paymentId != null && !tx.paymentId.isEmpty();
        if (canRate) {
            popup.getMenu().add(0, 3, 2, getString(R.string.menu_rate_business));
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                shareReceipt(tx);
            } else if (item.getItemId() == 2) {
                saveReceipt(tx);
            } else if (item.getItemId() == 3) {
                RatingHelper.showRatingDialog(this, db, auth, tx.merchantId, tx.paymentId,
                        this::loadPayments);
            }
            return true;
        });
        popup.show();
    }

    private void shareReceipt(CustomerTx tx) {
        Toast.makeText(this, R.string.msg_generating_receipt, Toast.LENGTH_SHORT).show();
        List<CartItem> cartItems = convertCartItems(tx.cartItemMaps);

        new Thread(() -> {
            try {
                File pdf = ReceiptGenerator.generate(
                        this, tx.merchantName, tx.invoiceId,
                        tx.usdAmount, tx.btcAmount, cartItems,
                        tx.ivaPercent, tx.isrPercent,
                        tx.discountType, tx.discountValue, null);
                runOnUiThread(() -> {
                    Uri uri = FileProvider.getUriForFile(
                            this, "com.cyberpos.app.fileprovider", pdf);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/pdf");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, getString(R.string.msg_share_receipt)));
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.error_receipt_generation, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void saveReceipt(CustomerTx tx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            pendingSaveTx = tx;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_WRITE_STORAGE);
            return;
        }
        doSave(tx);
    }

    private void doSave(CustomerTx tx) {
        Toast.makeText(this, R.string.msg_generating_receipt, Toast.LENGTH_SHORT).show();
        List<CartItem> cartItems = convertCartItems(tx.cartItemMaps);

        new Thread(() -> {
            try {
                File pdf = ReceiptGenerator.generate(
                        this, tx.merchantName, tx.invoiceId,
                        tx.usdAmount, tx.btcAmount, cartItems,
                        tx.ivaPercent, tx.isrPercent,
                        tx.discountType, tx.discountValue, null);
                ReceiptGenerator.saveToDownloads(this, pdf);
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.msg_receipt_saved, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.error_receipt_generation, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && pendingSaveTx != null) {
                doSave(pendingSaveTx);
            } else {
                Toast.makeText(this, R.string.error_receipt_generation, Toast.LENGTH_SHORT).show();
            }
            pendingSaveTx = null;
        }
    }

    private static List<CartItem> convertCartItems(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) return Collections.emptyList();
        List<CartItem> result = new ArrayList<>();
        for (Map<String, Object> m : maps) {
            String nombre     = (String) m.get("nombre");
            String productoId = (String) m.get("productoId");
            String categoria  = (String) m.get("categoria");
            double precio = 0;
            // ES: toMap() guarda la clave "precio" (se aceptaba "precioUsd" por compatibilidad)
            // EN: toMap() stores the "precio" key ("precioUsd" kept for backward compatibility)
            Object pObj = m.get("precio");
            if (pObj == null) pObj = m.get("precioUsd");
            if (pObj instanceof Number) precio = ((Number) pObj).doubleValue();
            int cantidad = 1;
            Object cObj = m.get("cantidad");
            if (cObj instanceof Number) cantidad = ((Number) cObj).intValue();
            CartItem ci = new CartItem(productoId, nombre, precio, categoria);
            ci.setCantidad(cantidad);
            // ES: Restaurar descuento por producto (F8) si estaba guardado
            // EN: Restore per-product discount (F8) if it was stored
            String dTipo = (String) m.get("descuentoTipo");
            if (dTipo != null) ci.setDescuentoTipo(dTipo);
            Object dValObj = m.get("descuentoValor");
            if (dValObj instanceof Number) ci.setDescuentoValor(((Number) dValObj).doubleValue());
            String dAlc = (String) m.get("descuentoAlcance");
            if (dAlc != null) ci.setDescuentoAlcance(dAlc);
            Object dMinObj = m.get("descuentoMinCant");
            if (dMinObj instanceof Number) ci.setDescuentoMinCant(((Number) dMinObj).intValue());
            result.add(ci);
        }
        return result;
    }

    // ── Navegación inferior / Bottom nav ─────────────────────────────────────

    private void setupBottomNav() {
        binding.bottomNav.setSelectedItemId(R.id.nav_history);
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_pay) {
                startActivity(new Intent(this, CustomerHomeActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, AjustesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
    }

    // ── Modelo / Model ───────────────────────────────────────────────────────

    static class CustomerTx {
        final String displayName;
        final String time;
        final double btcAmount;
        final double usdAmount;
        final String status;
        final String invoiceId;
        final String merchantName;
        final List<Map<String, Object>> cartItemMaps;
        final double ivaPercent;
        final double isrPercent;
        final String discountType;
        final double discountValue;
        final String paymentId;
        final String merchantId;
        final boolean rated;

        CustomerTx(String displayName, String time,
                   double btcAmount, double usdAmount, String status,
                   String invoiceId, String merchantName,
                   List<Map<String, Object>> cartItemMaps,
                   double ivaPercent, double isrPercent,
                   String discountType, double discountValue,
                   String paymentId, String merchantId, boolean rated) {
            this.displayName  = displayName;
            this.time         = time;
            this.btcAmount    = btcAmount;
            this.usdAmount    = usdAmount;
            this.status       = status;
            this.invoiceId    = invoiceId;
            this.merchantName = merchantName;
            this.cartItemMaps = cartItemMaps;
            this.ivaPercent   = ivaPercent;
            this.isrPercent   = isrPercent;
            this.discountType  = discountType != null ? discountType : "";
            this.discountValue = discountValue;
            this.paymentId  = paymentId;
            this.merchantId = merchantId;
            this.rated      = rated;
        }
    }

    static class ListItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_TX     = 1;

        final int type;
        final String headerLabel;
        final CustomerTx tx;

        private ListItem(int type, String headerLabel, CustomerTx tx) {
            this.type = type;
            this.headerLabel = headerLabel;
            this.tx = tx;
        }

        static ListItem header(String label) { return new ListItem(TYPE_HEADER, label, null); }
        static ListItem tx(CustomerTx t)     { return new ListItem(TYPE_TX, null, t); }
    }

    // ── Adaptador / Adapter ──────────────────────────────────────────────────

    interface OnReceiptClickListener {
        void onClick(CustomerTx tx, View anchor);
    }

    static class CustomerTxAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<ListItem> items;
        private final Map<String, Long> pointsByMerchant;
        private final OnReceiptClickListener receiptListener;

        CustomerTxAdapter(List<ListItem> items, Map<String, Long> pointsByMerchant,
                           OnReceiptClickListener receiptListener) {
            this.items = items;
            this.pointsByMerchant = pointsByMerchant;
            this.receiptListener = receiptListener;
        }

        @Override
        public int getItemViewType(int position) { return items.get(position).type; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == ListItem.TYPE_HEADER) {
                return new HeaderViewHolder(
                        inflater.inflate(R.layout.item_date_header, parent, false));
            }
            return new TxViewHolder(
                    inflater.inflate(R.layout.item_transaction, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem item = items.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvDateHeader.setText(item.headerLabel);
            } else {
                TxViewHolder vh = (TxViewHolder) holder;
                CustomerTx tx = item.tx;
                vh.tvStoreName.setText(tx.displayName);
                vh.tvDate.setText(tx.time);
                vh.tvBtcAmount.setText(
                        String.format(Locale.US, "-%.8f BTC", tx.btcAmount));
                vh.tvUsdAmount.setText(MoneyFormatter.historyEquivalent(
                        holder.itemView.getContext(), tx.btcAmount, tx.usdAmount));
                if ("settled".equals(tx.status)) {
                    vh.tvStatus.setText("✓ COMPLETADO");
                    vh.tvStatus.setTextColor(
                            holder.itemView.getContext().getColor(R.color.neon_green));
                } else {
                    vh.tvStatus.setText("⏳ PENDIENTE");
                    vh.tvStatus.setTextColor(
                            holder.itemView.getContext().getColor(R.color.error));
                }
                Long puntos = tx.merchantId != null ? pointsByMerchant.get(tx.merchantId) : null;
                if (puntos != null && puntos > 0) {
                    vh.tvLoyaltyPoints.setText(holder.itemView.getContext()
                            .getString(R.string.label_loyalty_points_history, puntos, tx.merchantName));
                    vh.tvLoyaltyPoints.setVisibility(View.VISIBLE);
                } else {
                    vh.tvLoyaltyPoints.setVisibility(View.GONE);
                }
                vh.btnShareReceipt.setOnClickListener(v -> receiptListener.onClick(tx, v));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            final TextView tvDateHeader;
            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            }
        }

        static class TxViewHolder extends RecyclerView.ViewHolder {
            final TextView tvStoreName, tvDate, tvBtcAmount, tvUsdAmount, tvStatus, tvLoyaltyPoints;
            final MaterialButton btnShareReceipt;

            TxViewHolder(@NonNull View itemView) {
                super(itemView);
                tvStoreName    = itemView.findViewById(R.id.tvStoreName);
                tvDate         = itemView.findViewById(R.id.tvDate);
                tvBtcAmount    = itemView.findViewById(R.id.tvBtcAmount);
                tvUsdAmount    = itemView.findViewById(R.id.tvUsdAmount);
                tvStatus       = itemView.findViewById(R.id.tvStatus);
                tvLoyaltyPoints = itemView.findViewById(R.id.tvLoyaltyPoints);
                btnShareReceipt = itemView.findViewById(R.id.btnShareReceipt);
            }
        }
    }
}
