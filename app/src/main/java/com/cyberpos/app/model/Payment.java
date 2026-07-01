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

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class Payment {

    @Exclude
    private String id;

    private String merchantId;
    private double amountUsd;
    private double amountBtc;
    private String customerName;
    private String description;
    private String lightningInvoice;
    private String btcPayInvoiceId;
    private String status;

    private List<Map<String, Object>> cartItems;

    private String merchantName;
    private double ivaPercent;
    private double isrPercent;

    // ES: Descuento aplicado (F8) — tipo global CartTotals.DISC_* y monto total descontado en USD
    // EN: Applied discount (F8) — global type CartTotals.DISC_* and total discounted amount in USD
    private String discountType = "";
    private double discountValue = 0;
    private double discountUsd = 0;

    // ES: Cobro mixto (F10) — efectivo USD + resto en Bitcoin. "bitcoin" = pago 100% BTC (default,
    //     compatible con pagos históricos que no tienen este campo).
    // EN: Mixed payment (F10) — USD cash + BTC remainder. "bitcoin" = 100% BTC payment (default,
    //     compatible with historic payments missing this field).
    private String paymentType = "bitcoin";
    private double cashAmountUsd = 0;

    // ES: true si el cliente ya calificó este pago (F11) — evita mostrar la opción dos veces.
    // EN: true once the customer has rated this payment (F11) — prevents showing the option twice.
    private boolean rated = false;

    @ServerTimestamp
    private Date createdAt;

    public Payment() {}

    public Payment(String merchantId, double amountUsd, double amountBtc, String customerName,
                   String description, String lightningInvoice, String btcPayInvoiceId) {
        this.merchantId = merchantId;
        this.amountUsd = amountUsd;
        this.amountBtc = amountBtc;
        this.customerName = customerName != null ? customerName : "";
        this.description = description;
        this.lightningInvoice = lightningInvoice;
        this.btcPayInvoiceId = btcPayInvoiceId;
        this.status = "pending";
        this.createdAt = null; // ES: @ServerTimestamp rellena este campo / EN: @ServerTimestamp fills this
    }

    @Exclude public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public double getAmountUsd() { return amountUsd; }
    public void setAmountUsd(double amountUsd) { this.amountUsd = amountUsd; }

    public double getAmountBtc() { return amountBtc; }
    public void setAmountBtc(double amountBtc) { this.amountBtc = amountBtc; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLightningInvoice() { return lightningInvoice; }
    public void setLightningInvoice(String lightningInvoice) { this.lightningInvoice = lightningInvoice; }

    public String getBtcPayInvoiceId() { return btcPayInvoiceId; }
    public void setBtcPayInvoiceId(String btcPayInvoiceId) { this.btcPayInvoiceId = btcPayInvoiceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Map<String, Object>> getCartItems() { return cartItems; }
    public void setCartItems(List<Map<String, Object>> cartItems) { this.cartItems = cartItems; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public double getIvaPercent() { return ivaPercent; }
    public void setIvaPercent(double ivaPercent) { this.ivaPercent = ivaPercent; }

    public double getIsrPercent() { return isrPercent; }
    public void setIsrPercent(double isrPercent) { this.isrPercent = isrPercent; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public double getDiscountValue() { return discountValue; }
    public void setDiscountValue(double discountValue) { this.discountValue = discountValue; }

    public double getDiscountUsd() { return discountUsd; }
    public void setDiscountUsd(double discountUsd) { this.discountUsd = discountUsd; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public double getCashAmountUsd() { return cashAmountUsd; }
    public void setCashAmountUsd(double cashAmountUsd) { this.cashAmountUsd = cashAmountUsd; }

    public boolean isRated() { return rated; }
    public void setRated(boolean rated) { this.rated = rated; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
