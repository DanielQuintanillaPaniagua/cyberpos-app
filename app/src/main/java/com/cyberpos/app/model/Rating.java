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
package com.cyberpos.app.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * ES: Calificación de un cliente a un comerciante (F11). Vive en
 *     merchants/{merchantUid}/ratings/{paymentId} — el ID del documento es el ID del pago
 *     calificado, lo que garantiza como mismo Firestore que un pago solo pueda calificarse una vez.
 * EN: A customer's rating of a merchant (F11). Lives at
 *     merchants/{merchantUid}/ratings/{paymentId} — the document ID is the rated payment's ID,
 *     which makes Firestore itself enforce one rating per transaction.
 */
public class Rating {

    private String customerId;
    private String paymentId;
    private int stars;
    private String comment;

    @ServerTimestamp
    private Date createdAt;

    public Rating() {}

    public Rating(String customerId, String paymentId, int stars, String comment) {
        this.customerId = customerId;
        this.paymentId = paymentId;
        this.stars = stars;
        this.comment = comment != null ? comment : "";
        this.createdAt = null; // ES: @ServerTimestamp rellena este campo / EN: @ServerTimestamp fills this
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
