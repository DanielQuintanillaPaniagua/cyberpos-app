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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.cyberpos.app.model.Rating;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

/**
 * ES: Perfil del negocio visible para el cliente antes de pagar (F12): logo, nombre,
 *     descripción, rating promedio y horario. Solo lectura — el cliente no necesita
 *     registrarse, únicamente lee datos públicos del comerciante.
 * EN: Business profile shown to the customer before paying (F12): logo, name,
 *     description, average rating and hours. Read-only — no customer registration
 *     needed, it just reads the merchant's public data.
 */
final class MerchantProfileHelper {

    private MerchantProfileHelper() {}

    static void show(Context ctx, FirebaseFirestore db, String merchantId) {
        if (merchantId == null || merchantId.isEmpty()) return;

        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_merchant_profile, null, false);
        ImageView ivLogo         = view.findViewById(R.id.ivMerchantLogo);
        TextView  tvName         = view.findViewById(R.id.tvMerchantName);
        TextView  tvRating       = view.findViewById(R.id.tvMerchantRating);
        TextView  tvDescription  = view.findViewById(R.id.tvMerchantDescription);
        TextView  tvSchedule     = view.findViewById(R.id.tvMerchantSchedule);

        new AlertDialog.Builder(ctx)
                .setView(view)
                .setPositiveButton(R.string.btn_close, null)
                .show();

        db.collection("users").document(merchantId)
                .collection("negocio").document("datos")
                .get()
                .addOnSuccessListener(doc -> {
                    String nombre = doc.getString("nombreComercial");
                    if (nombre == null || nombre.isEmpty()) nombre = doc.getString("nombreNegocio");
                    tvName.setText(nombre != null && !nombre.isEmpty()
                            ? nombre : ctx.getString(R.string.label_business_default_name));

                    String descripcion = doc.getString("descripcion");
                    tvDescription.setText(descripcion != null && !descripcion.isEmpty()
                            ? descripcion : ctx.getString(R.string.label_no_description));

                    String horario = doc.getString("horario");
                    tvSchedule.setText(horario != null && !horario.isEmpty()
                            ? horario : ctx.getString(R.string.label_no_schedule));
                });

        db.collection("users").document(merchantId)
                .collection("configuracion").document("pantalla_cobro")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String b64 = doc.getString("logo");
                    if (b64 == null) b64 = doc.getString("logo_base64");
                    if (b64 == null || b64.isEmpty()) return;
                    try {
                        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) ivLogo.setImageBitmap(bmp);
                    } catch (Exception ignored) {}
                });

        db.collection("merchants").document(merchantId).collection("ratings")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Rating> ratings = snapshot.toObjects(Rating.class);
                    if (ratings.isEmpty()) {
                        tvRating.setText(R.string.label_business_no_ratings);
                        return;
                    }
                    double sum = 0;
                    for (Rating r : ratings) sum += r.getStars();
                    double avg = sum / ratings.size();
                    tvRating.setText(String.format(Locale.US, "★ %.1f  (%d)", avg, ratings.size()));
                });
    }
}
