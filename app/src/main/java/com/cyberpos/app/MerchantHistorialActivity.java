package com.cyberpos.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyberpos.app.databinding.ActivityMerchantHistorialBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MerchantHistorialActivity extends AppCompatActivity {

    private ActivityMerchantHistorialBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantHistorialBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRecyclerView();
        setupBottomNav();
    }

    private void setupRecyclerView() {
        List<MerchantTx> allTx = buildMockTransactions();

        double totalBtc = 0;
        double totalUsd = 0;
        for (MerchantTx tx : allTx) {
            totalBtc += tx.btcAmount;
            totalUsd += tx.usdAmount;
        }

        binding.tvTotalBtc.setText(String.format("+%.8f BTC", totalBtc));
        binding.tvTotalUsd.setText(String.format("≈ $%.2f USD", totalUsd));
        binding.tvTxCount.setText(String.valueOf(allTx.size()));

        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.header("HOY"));
        items.add(ListItem.tx(allTx.get(0)));
        items.add(ListItem.tx(allTx.get(1)));
        items.add(ListItem.tx(allTx.get(2)));
        items.add(ListItem.header("AYER"));
        items.add(ListItem.tx(allTx.get(3)));
        items.add(ListItem.tx(allTx.get(4)));
        items.add(ListItem.header("11 Jun 2026"));
        items.add(ListItem.tx(allTx.get(5)));
        items.add(ListItem.tx(allTx.get(6)));

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(new MerchantTxAdapter(items));
    }

    private List<MerchantTx> buildMockTransactions() {
        return Arrays.asList(
            new MerchantTx("Carlos Mendoza",  "14:32", 0.00042000, 24.99),
            new MerchantTx("Laura Guerrero",  "11:15", 0.00021000, 12.50),
            new MerchantTx("Javier Ramos",    "09:03", 0.00170000, 101.15),
            new MerchantTx("María López",     "18:45", 0.00085000, 50.58),
            new MerchantTx("Pedro Alvarado",  "13:20", 0.00030000, 17.85),
            new MerchantTx("Ana Cortez",      "20:11", 0.00190000, 113.05),
            new MerchantTx("Diego Flores",    "16:37", 0.00062000, 36.90)
        );
    }

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_cobros) {
                startActivity(new Intent(this, PaymentActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_settings) {
                startActivity(new Intent(this, MerchantAjustesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
        binding.bottomNav.setSelectedItemId(R.id.nav_merchant_history);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_merchant_history);
    }

    // ── Model ────────────────────────────────────────────────────────────────

    static class MerchantTx {
        final String customerName;
        final String time;
        final double btcAmount;
        final double usdAmount;

        MerchantTx(String customerName, String time, double btcAmount, double usdAmount) {
            this.customerName = customerName;
            this.time = time;
            this.btcAmount = btcAmount;
            this.usdAmount = usdAmount;
        }
    }

    static class ListItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_TX = 1;

        final int type;
        final String headerLabel;
        final MerchantTx tx;

        private ListItem(int type, String headerLabel, MerchantTx tx) {
            this.type = type;
            this.headerLabel = headerLabel;
            this.tx = tx;
        }

        static ListItem header(String label) {
            return new ListItem(TYPE_HEADER, label, null);
        }

        static ListItem tx(MerchantTx t) {
            return new ListItem(TYPE_TX, null, t);
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    static class MerchantTxAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<ListItem> items;

        MerchantTxAdapter(List<ListItem> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == ListItem.TYPE_HEADER) {
                View view = inflater.inflate(R.layout.item_date_header, parent, false);
                return new HeaderViewHolder(view);
            }
            View view = inflater.inflate(R.layout.item_merchant_transaction, parent, false);
            return new TxViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem item = items.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvDateHeader.setText(item.headerLabel);
            } else {
                TxViewHolder vh = (TxViewHolder) holder;
                MerchantTx tx = item.tx;
                vh.tvInitials.setText(String.valueOf(tx.customerName.charAt(0)));
                vh.tvCustomerName.setText(tx.customerName);
                vh.tvTime.setText(tx.time);
                vh.tvBtcAmount.setText(String.format("+%.8f BTC", tx.btcAmount));
                vh.tvUsdAmount.setText(String.format("≈ $%.2f USD", tx.usdAmount));
                vh.tvStatus.setText("✓ COMPLETADO");
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            final TextView tvDateHeader;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            }
        }

        static class TxViewHolder extends RecyclerView.ViewHolder {
            final TextView tvInitials;
            final TextView tvCustomerName;
            final TextView tvTime;
            final TextView tvBtcAmount;
            final TextView tvUsdAmount;
            final TextView tvStatus;

            TxViewHolder(@NonNull View itemView) {
                super(itemView);
                tvInitials     = itemView.findViewById(R.id.tvInitials);
                tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
                tvTime         = itemView.findViewById(R.id.tvTime);
                tvBtcAmount    = itemView.findViewById(R.id.tvBtcAmount);
                tvUsdAmount    = itemView.findViewById(R.id.tvUsdAmount);
                tvStatus       = itemView.findViewById(R.id.tvStatus);
            }
        }
    }
}
