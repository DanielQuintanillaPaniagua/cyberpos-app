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
        this.createdAt = null; // @ServerTimestamp fills this
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
