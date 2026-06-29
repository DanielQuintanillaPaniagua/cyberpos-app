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
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyberpos.app.databinding.ActivityReportesBinding;
import com.cyberpos.app.model.Payment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportesActivity extends AppCompatActivity {

    private static final int FILTER_TODAY  = 0;
    private static final int FILTER_WEEK   = 1;
    private static final int FILTER_MONTH  = 2;
    private static final int FILTER_CUSTOM = 3;

    private static final int REQ_STORAGE = 101;

    private ActivityReportesBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String businessName = "";

    private final List<Payment> allPayments      = new ArrayList<>();
    private final List<Payment> filteredPayments = new ArrayList<>();

    private int activeFilter = FILTER_MONTH;
    private Calendar customStart;
    private Calendar customEnd;
    private boolean pendingIsPdf;

    private final ExecutorService executor   = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private ReportTxAdapter adapter;

    private final SimpleDateFormat labelFmt =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new ReportTxAdapter(filteredPayments);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        setupFilterButtons();
        setupCustomDateButtons();
        setupExportButtons();
        setupBottomNav();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_merchant_settings);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ── Datos / Data ─────────────────────────────────────────────────────────

    private void loadData() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("businessName");
                        if (name == null || name.isEmpty()) name = doc.getString("fullName");
                        if (name != null && !name.isEmpty()) businessName = name;
                    }
                });

        db.collection("payments")
                .whereEqualTo("merchantId", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    allPayments.clear();
                    allPayments.addAll(snapshot.toObjects(Payment.class));
                    allPayments.sort((a, b) -> {
                        Date da = a.getCreatedAt(), db2 = b.getCreatedAt();
                        if (da == null) return 1;
                        if (db2 == null) return -1;
                        return db2.compareTo(da);
                    });
                    applyFilter();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_payments, Toast.LENGTH_SHORT).show());
    }

    // ── Lógica de filtros / Filter Logic ─────────────────────────────────────

    private void selectFilter(int filter) {
        activeFilter = filter;
        updateFilterStyles();
        binding.rowCustomDates.setVisibility(filter == FILTER_CUSTOM ? View.VISIBLE : View.GONE);

        if (filter == FILTER_CUSTOM && (customStart == null || customEnd == null)) {
            initCustomDates();
            showDatePicker(true);
            return;
        }
        applyFilter();
    }

    private void initCustomDates() {
        customEnd = Calendar.getInstance();
        customStart = Calendar.getInstance();
        customStart.set(Calendar.DAY_OF_MONTH, 1);
        customStart.set(Calendar.HOUR_OF_DAY, 0);
        customStart.set(Calendar.MINUTE, 0);
        customStart.set(Calendar.SECOND, 0);
        customStart.set(Calendar.MILLISECOND, 0);
        updateDateButtonLabels();
    }

    private void applyFilter() {
        Date[] range = getFilterRange();
        if (range == null) return;

        Date start = range[0], end = range[1];
        filteredPayments.clear();
        for (Payment p : allPayments) {
            Date ts = p.getCreatedAt();
            if (ts != null && !ts.before(start) && !ts.after(end)) {
                filteredPayments.add(p);
            }
        }

        updateSummary();

        if (filteredPayments.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.recyclerView.setVisibility(View.GONE);
        } else {
            binding.tvEmptyState.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private Date[] getFilterRange() {
        Calendar now   = Calendar.getInstance();
        Calendar start = Calendar.getInstance();

        switch (activeFilter) {
            case FILTER_TODAY:
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                break;

            case FILTER_WEEK:
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                // ES: Retroceder al lunes (la semana inicia el lunes en El Salvador)
                // EN: Roll back to Monday (week starts Mon in El Salvador)
                int dow = start.get(Calendar.DAY_OF_WEEK);
                int daysBack = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
                start.add(Calendar.DAY_OF_YEAR, -daysBack);
                break;

            case FILTER_MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                break;

            case FILTER_CUSTOM:
                if (customStart == null || customEnd == null) return null;
                Calendar endOfDay = (Calendar) customEnd.clone();
                endOfDay.set(Calendar.HOUR_OF_DAY, 23);
                endOfDay.set(Calendar.MINUTE, 59);
                endOfDay.set(Calendar.SECOND, 59);
                return new Date[]{customStart.getTime(), endOfDay.getTime()};
        }

        return new Date[]{start.getTime(), now.getTime()};
    }

    private void updateSummary() {
        double totalBtc = 0, totalUsd = 0;
        for (Payment p : filteredPayments) {
            if ("settled".equals(p.getStatus())) {
                totalBtc += p.getAmountBtc();
                totalUsd += p.getAmountUsd();
            }
        }
        binding.tvTxCount.setText(String.valueOf(filteredPayments.size()));
        binding.tvTotalBtc.setText(String.format(Locale.US, "+%.8f BTC", totalBtc));
        binding.tvTotalUsd.setText(String.format(Locale.US, "≈ $%.2f USD", totalUsd));
    }

    // ── Configuración de UI / UI Setup ───────────────────────────────────────

    private void setupFilterButtons() {
        binding.btnFilterToday.setOnClickListener(v -> selectFilter(FILTER_TODAY));
        binding.btnFilterWeek.setOnClickListener(v -> selectFilter(FILTER_WEEK));
        binding.btnFilterMonth.setOnClickListener(v -> selectFilter(FILTER_MONTH));
        binding.btnFilterCustom.setOnClickListener(v -> selectFilter(FILTER_CUSTOM));
        selectFilter(FILTER_MONTH);
    }

    private void updateFilterStyles() {
        int[] filterIds  = {FILTER_TODAY, FILTER_WEEK, FILTER_MONTH, FILTER_CUSTOM};
        MaterialButton[] btns = {
                binding.btnFilterToday,
                binding.btnFilterWeek,
                binding.btnFilterMonth,
                binding.btnFilterCustom
        };
        for (int i = 0; i < btns.length; i++) {
            boolean selected = (activeFilter == filterIds[i]);
            btns[i].setBackgroundTintList(ColorStateList.valueOf(
                    selected ? Color.parseColor("#00FF88") : Color.TRANSPARENT));
            btns[i].setTextColor(selected ? Color.BLACK : Color.parseColor("#888888"));
        }
    }

    private void setupCustomDateButtons() {
        binding.btnDateStart.setOnClickListener(v -> showDatePicker(true));
        binding.btnDateEnd.setOnClickListener(v -> showDatePicker(false));
    }

    private void showDatePicker(boolean isStart) {
        Calendar initial = isStart
                ? (customStart != null ? customStart : Calendar.getInstance())
                : (customEnd   != null ? customEnd   : Calendar.getInstance());

        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, day, isStart ? 0 : 23, isStart ? 0 : 59, isStart ? 0 : 59);
            chosen.set(Calendar.MILLISECOND, 0);

            if (isStart) {
                customStart = chosen;
                if (customEnd != null && customEnd.before(customStart)) {
                    customEnd = (Calendar) customStart.clone();
                    customEnd.set(Calendar.HOUR_OF_DAY, 23);
                    customEnd.set(Calendar.MINUTE, 59);
                    customEnd.set(Calendar.SECOND, 59);
                }
                updateDateButtonLabels();
                showDatePicker(false);
            } else {
                if (customStart != null && chosen.before(customStart)) {
                    Toast.makeText(this,
                            "La fecha final debe ser después del inicio", Toast.LENGTH_SHORT).show();
                    return;
                }
                customEnd = chosen;
                updateDateButtonLabels();
                applyFilter();
            }
        }, initial.get(Calendar.YEAR),
           initial.get(Calendar.MONTH),
           initial.get(Calendar.DAY_OF_MONTH)).show();
    }

    @SuppressLint("SetTextI18n")
    private void updateDateButtonLabels() {
        binding.btnDateStart.setText(getString(R.string.btn_from) + ": " +
                (customStart != null ? labelFmt.format(customStart.getTime()) : "—"));
        binding.btnDateEnd.setText(getString(R.string.btn_to) + ": " +
                (customEnd != null ? labelFmt.format(customEnd.getTime()) : "—"));
    }

    private void setupExportButtons() {
        binding.btnExportPdf.setOnClickListener(v -> exportFile(true));
        binding.btnExportCsv.setOnClickListener(v -> exportFile(false));
    }

    private void exportFile(boolean isPdf) {
        if (filteredPayments.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_report_transactions, Toast.LENGTH_SHORT).show();
            return;
        }
        // ES: En API 26-28 se necesita WRITE_EXTERNAL_STORAGE para guardar en Descargas
        // EN: On API 26-28 we need WRITE_EXTERNAL_STORAGE to save to Downloads
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            pendingIsPdf = isPdf;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        doExport(isPdf);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                            @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                doExport(pendingIsPdf);
            } else {
                Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void doExport(boolean isPdf) {
        Toast.makeText(this, R.string.msg_generating_report, Toast.LENGTH_SHORT).show();

        Date[] range = getFilterRange();
        Date start = (range != null) ? range[0] : new Date(0);
        Date end   = (range != null) ? range[1] : new Date();

        List<Payment> snapshot = new ArrayList<>(filteredPayments);
        String biz  = businessName;
        String mime = isPdf ? "application/pdf" : "text/csv";
        String ts   = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "cyberpos_reporte_" + ts + (isPdf ? ".pdf" : ".csv");

        executor.execute(() -> {
            try {
                File tempFile = isPdf
                        ? ReportExporter.generatePdf(this, snapshot, start, end, biz)
                        : ReportExporter.generateCsv(this, snapshot);

                ReportExporter.saveToDownloads(this, tempFile, name, mime);

                mainHandler.post(() -> showSavedSnackbar(tempFile, mime));
            } catch (IOException e) {
                mainHandler.post(() ->
                        Toast.makeText(this, R.string.error_export_report, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showSavedSnackbar(File fileForSharing, String mimeType) {
        Snackbar.make(binding.getRoot(), R.string.msg_saved_to_downloads, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_share, v -> shareFile(fileForSharing, mimeType))
                .setActionTextColor(Color.parseColor("#00FF88"))
                .show();
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(this, "com.cyberpos.app.fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.msg_share_report)));
    }

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_cobros) {
                startActivity(new Intent(this, PaymentActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_history) {
                startActivity(new Intent(this, MerchantHistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
    }

    // ── Adaptador / Adapter ──────────────────────────────────────────────────

    static class ReportTxAdapter extends RecyclerView.Adapter<ReportTxAdapter.VH> {

        private final List<Payment> payments;
        private final SimpleDateFormat dateFmt =
                new SimpleDateFormat("dd MMM  HH:mm", new Locale("es", "MX"));

        ReportTxAdapter(List<Payment> payments) { this.payments = payments; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_report_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Payment p = payments.get(position);

            String name = (p.getCustomerName() != null && !p.getCustomerName().isEmpty())
                    ? p.getCustomerName() : "Cliente";
            h.tvCustomer.setText(name);
            h.tvDate.setText(p.getCreatedAt() != null ? dateFmt.format(p.getCreatedAt()) : "—");
            h.tvUsd.setText(String.format(Locale.US, "$%.2f", p.getAmountUsd()));
            h.tvBtc.setText(String.format(Locale.US, "%.6f BTC", p.getAmountBtc()));

            boolean settled = "settled".equals(p.getStatus());
            h.tvStatus.setText(settled ? "✓" : "⏳");
            h.tvStatus.setTextColor(Color.parseColor(settled ? "#00FF88" : "#FFC107"));
        }

        @Override
        public int getItemCount() { return payments.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvCustomer, tvDate, tvUsd, tvBtc, tvStatus;

            VH(@NonNull View v) {
                super(v);
                tvCustomer = v.findViewById(R.id.tvCustomer);
                tvDate     = v.findViewById(R.id.tvDate);
                tvUsd      = v.findViewById(R.id.tvUsd);
                tvBtc      = v.findViewById(R.id.tvBtc);
                tvStatus   = v.findViewById(R.id.tvStatus);
            }
        }
    }
}
