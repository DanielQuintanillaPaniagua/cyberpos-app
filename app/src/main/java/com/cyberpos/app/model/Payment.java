package com.cyberpos.app.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

public class Payment {

    private String merchantId;
    private double amountUsd;
    private String description;
    private String lightningInvoice;
    private String status;

    @ServerTimestamp
    private Date createdAt;

    // Required empty constructor for Firestore deserialization
    public Payment() {}

    public Payment(String merchantId, double amountUsd, String description,
                   String lightningInvoice, Date createdAt) {
        this.merchantId = merchantId;
        this.amountUsd = amountUsd;
        this.description = description;
        this.lightningInvoice = lightningInvoice;
        this.status = "pending";
        this.createdAt = createdAt;
    }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public double getAmountUsd() { return amountUsd; }
    public void setAmountUsd(double amountUsd) { this.amountUsd = amountUsd; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLightningInvoice() { return lightningInvoice; }
    public void setLightningInvoice(String lightningInvoice) { this.lightningInvoice = lightningInvoice; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
