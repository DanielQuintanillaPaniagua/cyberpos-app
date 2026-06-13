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

import com.cyberpos.app.databinding.ActivityHistorialBinding;

import java.util.Arrays;
import java.util.List;

public class HistorialActivity extends AppCompatActivity {

    private ActivityHistorialBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistorialBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRecyclerView();
        setupBottomNav();
    }

    private void setupRecyclerView() {
        List<Transaction> transactions = buildMockTransactions();

        double totalBtc = 0;
        double totalUsd = 0;
        for (Transaction t : transactions) {
            totalBtc += t.btcAmount;
            totalUsd += t.usdAmount;
        }

        binding.tvTotalBtc.setText(String.format("%.8f BTC", totalBtc));
        binding.tvTotalUsd.setText(String.format("≈ $%.2f USD", totalUsd));
        binding.tvTxCount.setText(String.valueOf(transactions.size()));

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(new TransactionAdapter(transactions));
    }

    private List<Transaction> buildMockTransactions() {
        return Arrays.asList(
            new Transaction("El Zonte Beach Bar",   "10 Jun 2026 · 14:32", 0.00042000, 24.99, true),
            new Transaction("La Pupusería Central", "09 Jun 2026 · 11:05", 0.00021000, 12.50, true),
            new Transaction("Bitcoin Beach Market", "08 Jun 2026 · 09:47", 0.00085000, 50.58, true),
            new Transaction("Surf Coffee Sopoto",   "07 Jun 2026 · 16:20", 0.00190000, 113.05, true),
            new Transaction("El Zonte Beach Bar",   "06 Jun 2026 · 19:53", 0.00030000, 17.85, true)
        );
    }

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

    // ── Model ────────────────────────────────────────────────────────────────

    static class Transaction {
        final String storeName;
        final String date;
        final double btcAmount;
        final double usdAmount;
        final boolean completed;

        Transaction(String storeName, String date, double btcAmount,
                    double usdAmount, boolean completed) {
            this.storeName = storeName;
            this.date = date;
            this.btcAmount = btcAmount;
            this.usdAmount = usdAmount;
            this.completed = completed;
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    static class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

        private final List<Transaction> items;

        TransactionAdapter(List<Transaction> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction t = items.get(position);
            holder.tvStoreName.setText(t.storeName);
            holder.tvDate.setText(t.date);
            holder.tvBtcAmount.setText(String.format("-%.8f BTC", t.btcAmount));
            holder.tvUsdAmount.setText(String.format("≈ $%.2f USD", t.usdAmount));
            holder.tvStatus.setText(t.completed ? "✓ COMPLETADO" : "● PENDIENTE");
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(
                    t.completed ? R.color.neon_green : R.color.error));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvStoreName;
            final TextView tvDate;
            final TextView tvBtcAmount;
            final TextView tvUsdAmount;
            final TextView tvStatus;

            ViewHolder(@NonNull View itemView) {
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
