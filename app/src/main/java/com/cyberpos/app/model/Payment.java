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

public class Payment {

    private String merchantId;
    private double amountUsd;
    private double amountBtc;
    private String customerName;
    private String description;
    private String lightningInvoice;
    private String btcPayInvoiceId;
    private String status;

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

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
