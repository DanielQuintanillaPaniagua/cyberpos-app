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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityMerchantAjustesBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MerchantAjustesActivity extends AppCompatActivity {

    private ActivityMerchantAjustesBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantAjustesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadMerchantProfile();
        setupSettingsRows();
        setupBottomNav();
        binding.btnLogout.setOnClickListener(v -> logout());
    }

    private void loadMerchantProfile() {
        if (auth.getCurrentUser() == null) return;

        String email = auth.getCurrentUser().getEmail();
        binding.tvEmail.setText(email != null ? email : "");

        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = null;
                    if (doc.exists()) {
                        name = doc.getString("businessName");
                        if (name == null || name.isEmpty()) name = doc.getString("fullName");
                    }
                    if (name == null || name.isEmpty()) name = email;
                    binding.tvName.setText(name != null ? name : "");
                    if (name != null && !name.isEmpty()) {
                        binding.tvInitials.setText(String.valueOf(name.charAt(0)).toUpperCase());
                    }
                });
    }

    private void setupSettingsRows() {
        binding.rowBusinessInfo.setOnClickListener(v ->
            startActivity(new Intent(this, InformacionNegocioActivity.class)));
        binding.rowDashboard.setOnClickListener(v ->
            startActivity(new Intent(this, DashboardActivity.class)));
        binding.rowProductos.setOnClickListener(v ->
            startActivity(new Intent(this, GestionProductosActivity.class)));
        binding.rowReportes.setOnClickListener(v ->
            startActivity(new Intent(this, ReportesActivity.class)));
        binding.rowBankAccounts.setOnClickListener(v ->
            startActivity(new Intent(this, CuentasBancariasActivity.class)));
        binding.rowTaxes.setOnClickListener(v ->
            startActivity(new Intent(this, ImpuestosTarifasActivity.class)));
        binding.rowPuntosLealtad.setOnClickListener(v ->
            startActivity(new Intent(this, PuntosLealtadActivity.class)));
        binding.rowMerchantNotifications.setOnClickListener(v ->
            startActivity(new Intent(this, NotificacionesMerchantActivity.class)));
        binding.rowPaymentScreen.setOnClickListener(v ->
            startActivity(new Intent(this, PantallaCobroActivity.class)));
        binding.rowBtcpay.setOnClickListener(v ->
            startActivity(new Intent(this, BtcPayConfigActivity.class)));

        binding.rowLanguage.setOnClickListener(v ->
            startActivity(new Intent(this, IdiomaActivity.class)));
        binding.rowMerchantCurrency.setOnClickListener(v ->
            startActivity(new Intent(this, MerchantMonedaActivity.class)));
        binding.rowImpresora.setOnClickListener(v ->
            startActivity(new Intent(this, BluetoothPrinterActivity.class)));
        binding.rowConfirmations.setOnClickListener(v ->
            startActivity(new Intent(this, ConfirmacionesActivity.class)));
        binding.rowMerchantPin.setOnClickListener(v ->
            startActivity(new Intent(this, PinSeguridadActivity.class)));
        binding.rowTwoFa.setOnClickListener(v ->
            startActivity(new Intent(this, DosFaActivity.class)));
        binding.rowLinkedDevices.setOnClickListener(v ->
            startActivity(new Intent(this, DispositivosVinculadosActivity.class)));
        binding.rowHelp.setOnClickListener(v ->
            startActivity(new Intent(this, AyudaContactoActivity.class)));
        binding.rowContact.setOnClickListener(v ->
            startActivity(new Intent(this, AyudaContactoActivity.class)));
        binding.rowAbout.setOnClickListener(v ->
            startActivity(new Intent(this, AyudaContactoActivity.class)));
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
            }
            return true;
        });
        binding.bottomNav.setSelectedItemId(R.id.nav_merchant_settings);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_merchant_settings);
    }

    private void logout() {
        auth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
