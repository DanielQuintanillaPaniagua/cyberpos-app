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
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.cyberpos.app.model.Rating;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * ES: Diálogo de calificación del comerciante (F11), usado tanto al completar un pago
 *     (CustomerHomeActivity) como desde el historial de transacciones (HistorialActivity).
 * EN: Merchant rating dialog (F11), used both right after a payment settles
 *     (CustomerHomeActivity) and from past transactions (HistorialActivity).
 */
final class RatingHelper {

    interface OnRated {
        void onRated();
    }

    private RatingHelper() {}

    static void showRatingDialog(Context ctx, FirebaseFirestore db, FirebaseAuth auth,
                                  String merchantId, String paymentId, OnRated callback) {
        if (merchantId == null || merchantId.isEmpty() || paymentId == null || paymentId.isEmpty()) {
            return;
        }

        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, 0);

        RatingBar ratingBar = new RatingBar(ctx);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(5f);
        LinearLayout.LayoutParams rbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rbParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.addView(ratingBar, rbParams);

        EditText etComment = new EditText(ctx);
        etComment.setHint(R.string.hint_rating_comment);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.topMargin = pad / 2;
        layout.addView(etComment, etParams);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.title_rate_business)
                .setView(layout)
                .setPositiveButton(R.string.btn_submit_rating, (d, w) -> {
                    int stars = Math.round(ratingBar.getRating());
                    if (stars < 1) stars = 1;
                    String comment = etComment.getText() != null
                            ? etComment.getText().toString().trim() : "";
                    submitRating(ctx, db, auth, merchantId, paymentId, stars, comment, callback);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private static void submitRating(Context ctx, FirebaseFirestore db, FirebaseAuth auth,
                                      String merchantId, String paymentId, int stars, String comment,
                                      OnRated callback) {
        String customerId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        Rating rating = new Rating(customerId, paymentId, stars, comment);

        // ES: El ID del doc de calificación = ID del pago → un pago solo puede calificarse una vez.
        // EN: Rating doc ID = payment ID → a payment can only be rated once.
        db.collection("merchants").document(merchantId)
                .collection("ratings").document(paymentId)
                .set(rating)
                .addOnSuccessListener(v -> {
                    db.collection("payments").document(paymentId).update("rated", true);
                    Toast.makeText(ctx, R.string.msg_rating_saved, Toast.LENGTH_SHORT).show();
                    if (callback != null) callback.onRated();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(ctx, R.string.error_rating_save, Toast.LENGTH_SHORT).show());
    }
}
