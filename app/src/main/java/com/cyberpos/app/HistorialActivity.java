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

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyberpos.app.databinding.ActivityHistorialBinding;
import com.cyberpos.app.model.Payment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistorialActivity extends AppCompatActivity {

    private ActivityHistorialBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private CustomerTxAdapter adapter;
    private final List<ListItem> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistorialBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new CustomerTxAdapter(items);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_history);
        loadPayments();
    }

    // ── Firestore / Firestore ─────────────────────────────────────────────────

    private void loadPayments() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("payments")
                .whereEqualTo("payerUid", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Payment> payments = snapshot.toObjects(Payment.class);
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
        binding.tvTotalUsd.setText(String.format(Locale.US, "≈ $%.2f USD", totalUsd));
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
                    payment.getStatus())));
        }
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

        CustomerTx(String displayName, String time,
                   double btcAmount, double usdAmount, String status) {
            this.displayName = displayName;
            this.time = time;
            this.btcAmount = btcAmount;
            this.usdAmount = usdAmount;
            this.status = status;
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

    static class CustomerTxAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<ListItem> items;

        CustomerTxAdapter(List<ListItem> items) { this.items = items; }

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
                vh.tvUsdAmount.setText(
                        String.format(Locale.US, "≈ $%.2f USD", tx.usdAmount));
                if ("settled".equals(tx.status)) {
                    vh.tvStatus.setText("✓ COMPLETADO");
                    vh.tvStatus.setTextColor(
                            holder.itemView.getContext().getColor(R.color.neon_green));
                } else {
                    vh.tvStatus.setText("⏳ PENDIENTE");
                    vh.tvStatus.setTextColor(
                            holder.itemView.getContext().getColor(R.color.error));
                }
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
            final TextView tvStoreName, tvDate, tvBtcAmount, tvUsdAmount, tvStatus;

            TxViewHolder(@NonNull View itemView) {
                super(itemView);
                tvStoreName = itemView.findViewById(R.id.tvStoreName);
                tvDate      = itemView.findViewById(R.id.tvDate);
                tvBtcAmount = itemView.findViewById(R.id.tvBtcAmount);
                tvUsdAmount = itemView.findViewById(R.id.tvUsdAmount);
                tvStatus    = itemView.findViewById(R.id.tvStatus);
            }
        }
    }
}
