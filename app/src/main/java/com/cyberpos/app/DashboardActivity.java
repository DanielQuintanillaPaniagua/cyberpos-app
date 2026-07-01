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
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityDashboardBinding;
import com.cyberpos.app.model.Payment;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private enum Period { TODAY, WEEK, MONTH }

    private static final int COLOR_NEON_GREEN  = 0xFF39FF14;
    private static final int COLOR_TEXT_PRI    = 0xFF121212;
    private static final int COLOR_TEXT_SEC    = 0xFF757575;
    private static final int COLOR_GRID        = 0xFFE8E8E8;

    private ActivityDashboardBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private Period currentPeriod = Period.WEEK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        binding.btnFilterHoy.setOnClickListener(v -> selectPeriod(Period.TODAY));
        binding.btnFilterSemana.setOnClickListener(v -> selectPeriod(Period.WEEK));
        binding.btnFilterMes.setOnClickListener(v -> selectPeriod(Period.MONTH));

        setupBottomNav();
        configureChart();
        selectPeriod(Period.WEEK);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_dashboard);
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_cobros) {
                startActivity(new Intent(this, CatalogoActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_history) {
                startActivity(new Intent(this, MerchantHistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_settings) {
                startActivity(new Intent(this, MerchantAjustesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
    }

    // ── Period selection ──────────────────────────────────────────────────────

    private void selectPeriod(Period period) {
        currentPeriod = period;
        int active   = COLOR_NEON_GREEN;
        int inactive = COLOR_TEXT_SEC;

        binding.btnFilterHoy.setTextColor(period == Period.TODAY  ? active : inactive);
        binding.btnFilterSemana.setTextColor(period == Period.WEEK  ? active : inactive);
        binding.btnFilterMes.setTextColor(period == Period.MONTH ? active : inactive);

        loadData(period);
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    private void loadData(Period period) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("payments")
                .whereEqualTo("merchantId", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Date startDate = getStartDate(period);
                    List<Payment> filtered = new ArrayList<>();
                    for (Payment p : snapshot.toObjects(Payment.class)) {
                        if ("settled".equals(p.getStatus())
                                && p.getCreatedAt() != null
                                && !p.getCreatedAt().before(startDate)) {
                            filtered.add(p);
                        }
                    }
                    updateMetrics(filtered);
                    updateChart(filtered, period);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_payments, Toast.LENGTH_SHORT).show());
    }

    private Date getStartDate(Period period) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (period == Period.WEEK) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        } else if (period == Period.MONTH) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTime();
    }

    // ── Metrics ───────────────────────────────────────────────────────────────

    private void updateMetrics(List<Payment> payments) {
        double totalUsd = 0, totalBtc = 0;
        Map<Integer, Integer> hourBuckets = new HashMap<>();
        Map<String, Integer> productCount = new HashMap<>();

        for (Payment p : payments) {
            totalUsd += p.getAmountUsd();
            totalBtc += p.getAmountBtc();

            if (p.getCreatedAt() != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(p.getCreatedAt());
                int hour = c.get(Calendar.HOUR_OF_DAY);
                hourBuckets.put(hour, hourBuckets.getOrDefault(hour, 0) + 1);
            }

            if (p.getCartItems() != null) {
                for (Map<String, Object> item : p.getCartItems()) {
                    String nombre = (String) item.get("nombre");
                    Number qty    = (Number) item.get("cantidad");
                    if (nombre != null && qty != null) {
                        productCount.put(nombre,
                                productCount.getOrDefault(nombre, 0) + qty.intValue());
                    }
                }
            }
        }

        String topProduct = "—";
        int topCount = 0;
        for (Map.Entry<String, Integer> e : productCount.entrySet()) {
            if (e.getValue() > topCount) {
                topCount = e.getValue();
                topProduct = e.getKey() + " ×" + e.getValue();
            }
        }

        int peakHour = -1, peakCount = 0;
        for (Map.Entry<Integer, Integer> e : hourBuckets.entrySet()) {
            if (e.getValue() > peakCount) {
                peakCount = e.getValue();
                peakHour  = e.getKey();
            }
        }
        String peakHourStr = peakHour >= 0
                ? String.format(Locale.US, "%02d:00 – %02d:00", peakHour, peakHour + 1)
                : "—";

        binding.tvTotalUsd.setText(String.format(Locale.US, "$%.2f", totalUsd));
        binding.tvTotalBtc.setText(String.format(Locale.US, "%.8f ₿", totalBtc));
        binding.tvNumTransactions.setText(String.valueOf(payments.size()));
        binding.tvTopProduct.setText(topProduct);
        binding.tvPeakHour.setText(peakHourStr);

        boolean empty = payments.isEmpty();
        binding.tvNoData.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.barChart.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ── Chart ─────────────────────────────────────────────────────────────────

    private void configureChart() {
        binding.barChart.getDescription().setEnabled(false);
        binding.barChart.getLegend().setEnabled(false);
        binding.barChart.setDrawGridBackground(false);
        binding.barChart.setTouchEnabled(true);
        binding.barChart.setDragEnabled(true);
        binding.barChart.setScaleEnabled(true);
        binding.barChart.setPinchZoom(false);
        binding.barChart.setExtraBottomOffset(8f);

        XAxis xAxis = binding.barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(COLOR_TEXT_SEC);
        xAxis.setTextSize(9f);
        xAxis.setLabelRotationAngle(-45f);

        binding.barChart.getAxisLeft().setTextColor(COLOR_TEXT_SEC);
        binding.barChart.getAxisLeft().setGridColor(COLOR_GRID);
        binding.barChart.getAxisLeft().setAxisMinimum(0f);
        binding.barChart.getAxisRight().setEnabled(false);
    }

    private void updateChart(List<Payment> payments, Period period) {
        float[] values;
        String[] labels;

        if (period == Period.TODAY) {
            values = new float[24];
            labels = new String[24];
            for (int i = 0; i < 24; i++) labels[i] = i % 3 == 0 ? i + ":00" : "";
            for (Payment p : payments) {
                if (p.getCreatedAt() == null) continue;
                Calendar c = Calendar.getInstance();
                c.setTime(p.getCreatedAt());
                int hour = c.get(Calendar.HOUR_OF_DAY);
                values[hour] += (float) p.getAmountUsd();
            }
        } else if (period == Period.WEEK) {
            values = new float[7];
            labels = getResources().getStringArray(R.array.week_days_short);
            for (Payment p : payments) {
                if (p.getCreatedAt() == null) continue;
                Calendar c = Calendar.getInstance();
                c.setTime(p.getCreatedAt());
                int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
                values[dow] += (float) p.getAmountUsd();
            }
        } else {
            Calendar now = Calendar.getInstance();
            int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
            values = new float[days];
            labels = new String[days];
            for (int i = 0; i < days; i++) labels[i] = (i % 5 == 0) ? String.valueOf(i + 1) : "";
            for (Payment p : payments) {
                if (p.getCreatedAt() == null) continue;
                Calendar c = Calendar.getInstance();
                c.setTime(p.getCreatedAt());
                int day = c.get(Calendar.DAY_OF_MONTH) - 1;
                if (day >= 0 && day < days) values[day] += (float) p.getAmountUsd();
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) entries.add(new BarEntry(i, values[i]));

        BarDataSet dataSet = new BarDataSet(entries, "USD");
        dataSet.setColor(COLOR_NEON_GREEN);
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.7f);

        binding.barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.barChart.setData(data);
        binding.barChart.invalidate();
    }
}
