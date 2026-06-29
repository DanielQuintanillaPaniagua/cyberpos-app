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

import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityDispositivosVinculadosBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ServerTimestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DispositivosVinculadosActivity extends AppCompatActivity {

    private ActivityDispositivosVinculadosBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String currentDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDispositivosVinculadosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        currentDeviceId = Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ANDROID_ID);

        binding.btnBack.setOnClickListener(v -> finish());

        registerCurrentDevice();
        loadDispositivos();
    }

    private void registerCurrentDevice() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        String nombre = Build.MANUFACTURER + " " + Build.MODEL;
        String plataforma = "Android " + Build.VERSION.RELEASE;

        Map<String, Object> data = new HashMap<>();
        data.put("nombre", nombre);
        data.put("plataforma", plataforma);
        data.put("ultimoAcceso", new Date());
        data.put("esActual", true);

        db.collection("users").document(uid)
            .collection("dispositivos").document(currentDeviceId)
            .set(data);
    }

    private void loadDispositivos() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("dispositivos")
            .get()
            .addOnSuccessListener(snapshot -> {
                binding.containerDispositivos.removeAllViews();
                if (snapshot.isEmpty()) {
                    binding.layoutEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                binding.layoutEmpty.setVisibility(View.GONE);

                LayoutInflater inflater = LayoutInflater.from(this);
                boolean first = true;
                for (QueryDocumentSnapshot doc : snapshot) {
                    String docId = doc.getId();
                    String nombre = doc.getString("nombre");
                    String plataforma = doc.getString("plataforma");
                    Date ultimoAcceso = doc.getDate("ultimoAcceso");
                    boolean esActual = docId.equals(currentDeviceId);

                    View card = inflater.inflate(
                        R.layout.item_dispositivo, binding.containerDispositivos, false);

                    ((TextView) card.findViewById(R.id.tvNombreDispositivo))
                        .setText(nombre != null ? nombre : "Dispositivo desconocido");
                    ((TextView) card.findViewById(R.id.tvPlataforma))
                        .setText(plataforma != null ? plataforma : "");

                    String fechaStr = ultimoAcceso != null
                        ? "Último acceso: " + new SimpleDateFormat(
                            "dd/MM/yyyy HH:mm", Locale.getDefault()).format(ultimoAcceso)
                        : "Último acceso: desconocido";
                    ((TextView) card.findViewById(R.id.tvUltimoAcceso)).setText(fechaStr);

                    View badge = card.findViewById(R.id.tvBadgeActual);
                    badge.setVisibility(esActual ? View.VISIBLE : View.GONE);

                    View btnDesvincular = card.findViewById(R.id.btnDesvincular);
                    if (esActual) {
                        btnDesvincular.setVisibility(View.INVISIBLE);
                    } else {
                        btnDesvincular.setOnClickListener(v ->
                            confirmarDesvinculacion(uid, docId, nombre));
                    }

                    if (!first) {
                        View spacer = new View(this);
                        android.widget.LinearLayout.LayoutParams p =
                            new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 8);
                        spacer.setLayoutParams(p);
                        binding.containerDispositivos.addView(spacer);
                    }
                    binding.containerDispositivos.addView(card);
                    first = false;
                }
            })
            .addOnFailureListener(e ->
                Toast.makeText(this, "Error al cargar dispositivos", Toast.LENGTH_SHORT).show());
    }

    private void confirmarDesvinculacion(String uid, String docId, String nombre) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Desvincular dispositivo")
            .setMessage("¿Desvincular \"" + nombre + "\"? Este dispositivo perderá el acceso.")
            .setPositiveButton("Desvincular", (d, w) ->
                db.collection("users").document(uid)
                    .collection("dispositivos").document(docId)
                    .delete()
                    .addOnSuccessListener(v -> loadDispositivos())
                    .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al desvincular", Toast.LENGTH_SHORT).show()))
            .setNegativeButton("Cancelar", null)
            .show();
    }
}
