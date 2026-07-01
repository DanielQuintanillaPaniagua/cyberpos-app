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

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ES: Cliente único para la API de BTCPay Server. Centraliza toda la comunicación
 *     HTTP (conexión, cabecera de autorización, timeouts, lectura y parseo) que
 *     antes estaba duplicada en PaymentActivity y CustomerHomeActivity.
 *
 *     SEGURIDAD: las credenciales (URL, Store ID, API key) vienen de BtcPayConfig:
 *     las que el comerciante guardó cifradas en el dispositivo, con fallback a
 *     BuildConfig (local.properties) para desarrollo. La key ya NO se compila en
 *     el APK de producción — cada comerciante configura la suya en Ajustes →
 *     Servidor BTCPay.
 *
 * EN: Single client for the BTCPay Server API. Centralizes all HTTP plumbing
 *     (connection, auth header, timeouts, reading and parsing) that used to be
 *     duplicated across PaymentActivity and CustomerHomeActivity.
 *
 *     SECURITY: credentials (URL, Store ID, API key) come from BtcPayConfig:
 *     the ones the merchant saved encrypted on the device, falling back to
 *     BuildConfig (local.properties) for development. The key is NO longer
 *     compiled into production APKs — each merchant sets their own under
 *     Settings → BTCPay Server.
 */
public final class BtcPayClient {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private BtcPayClient() {}

    // ── Tipos de resultado / Result types ────────────────────────────────────

    /** ES: Datos de una factura BTCPay. / EN: BTCPay invoice data. */
    public static final class Invoice {
        public final String id;
        public final String checkoutLink;
        public final double amount;
        public final String currency;
        public final String status;

        Invoice(String id, String checkoutLink, double amount, String currency, String status) {
            this.id = id;
            this.checkoutLink = checkoutLink;
            this.amount = amount;
            this.currency = currency;
            this.status = status;
        }
    }

    /** ES: Un método de pago de una factura. / EN: One payment method of an invoice. */
    public static final class PaymentMethod {
        public final String paymentMethodId;
        @Nullable public final String destination;
        @Nullable public final String paymentLink;

        PaymentMethod(String paymentMethodId, @Nullable String destination,
                      @Nullable String paymentLink) {
            this.paymentMethodId = paymentMethodId;
            this.destination = destination;
            this.paymentLink = paymentLink;
        }
    }

    /** ES: Callback entregado SIEMPRE en el hilo principal. / EN: Callback always delivered on the main thread. */
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(BtcPayException error);
    }

    /**
     * ES: Error de BTCPay. httpCode >= 0 es una respuesta HTTP no exitosa (con cuerpo);
     *     httpCode == -1 es un fallo de red u otra excepción antes de recibir respuesta.
     * EN: BTCPay error. httpCode >= 0 is a non-success HTTP response (with body);
     *     httpCode == -1 is a network failure or other exception before any response.
     */
    public static final class BtcPayException extends Exception {
        public final int httpCode;
        @Nullable public final String body;

        BtcPayException(int httpCode, @Nullable String body, @Nullable String message,
                        @Nullable Throwable cause) {
            super(message, cause);
            this.httpCode = httpCode;
            this.body = body;
        }

        /**
         * ES: Mensaje legible: el campo "message" del cuerpo JSON de BTCPay si existe,
         *     luego "HTTP <code>: <body>", y por último el fallback provisto.
         * EN: Human-readable message: BTCPay JSON body "message" field if present,
         *     then "HTTP <code>: <body>", finally the supplied fallback.
         */
        public String userMessage(String fallback) {
            if (body != null) {
                try {
                    JSONObject json = new JSONObject(body);
                    if (json.has("message")) return json.getString("message");
                } catch (JSONException ignored) {}
                return "HTTP " + httpCode + ": " + body;
            }
            return getMessage() != null ? getMessage() : fallback;
        }
    }

    // ── API pública / Public API ─────────────────────────────────────────────

    /** ES: Crea una factura por un monto en USD. / EN: Creates an invoice for a USD amount. */
    public static void createInvoice(double amountUsd, @Nullable String description,
                                     Callback<Invoice> cb) {
        runAsync(() -> {
            String safeDesc = (description == null || description.isEmpty())
                    ? "Pago CyberPOS" : description;
            String body;
            try {
                body = new JSONObject()
                        .put("amount", String.format(Locale.US, "%.2f", amountUsd))
                        .put("currency", "USD")
                        .put("metadata", new JSONObject().put("itemDesc", safeDesc))
                        .toString();
            } catch (JSONException e) {
                throw new BtcPayException(-1, null, e.getMessage(), e);
            }
            String resp = request("POST", "/api/v1/stores/" + BtcPayConfig.getStoreId() + "/invoices", body);
            return parseInvoice(resp);
        }, cb);
    }

    /** ES: Obtiene una factura (monto, moneda, estado). / EN: Fetches an invoice (amount, currency, status). */
    public static void getInvoice(String invoiceId, Callback<Invoice> cb) {
        runAsync(() -> parseInvoice(
                request("GET", "/api/v1/stores/" + BtcPayConfig.getStoreId() + "/invoices/" + invoiceId, null)),
                cb);
    }

    /**
     * ES: Prueba credenciales SIN guardarlas: consulta la tienda y devuelve su
     *     nombre. Usado por BtcPayConfigActivity antes de guardar.
     * EN: Tests credentials WITHOUT saving them: fetches the store and returns
     *     its name. Used by BtcPayConfigActivity before saving.
     */
    public static void testConnection(String baseUrl, String storeId, String apiKey,
                                      Callback<String> cb) {
        final String base = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        runAsync(() -> {
            String resp = request("GET", base + "/api/v1/stores/" + storeId, apiKey, null);
            try {
                return new JSONObject(resp).optString("name", storeId);
            } catch (JSONException e) {
                throw new BtcPayException(-1, resp, e.getMessage(), e);
            }
        }, cb);
    }

    /** ES: Obtiene los métodos de pago de una factura. / EN: Fetches an invoice's payment methods. */
    public static void getPaymentMethods(String invoiceId, Callback<List<PaymentMethod>> cb) {
        runAsync(() -> {
            String resp = request("GET",
                    "/api/v1/stores/" + BtcPayConfig.getStoreId() + "/invoices/" + invoiceId + "/payment-methods",
                    null);
            try {
                JSONArray arr = new JSONArray(resp);
                List<PaymentMethod> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.getJSONObject(i);
                    list.add(new PaymentMethod(
                            m.optString("paymentMethodId", ""),
                            m.optString("destination", null),
                            m.optString("paymentLink", null)));
                }
                return list;
            } catch (JSONException e) {
                throw new BtcPayException(-1, resp, e.getMessage(), e);
            }
        }, cb);
    }

    // ── Internos / Internals ─────────────────────────────────────────────────

    private interface Producer<T> {
        T produce() throws BtcPayException;
    }

    private static <T> void runAsync(Producer<T> producer, Callback<T> cb) {
        EXECUTOR.execute(() -> {
            try {
                T result = producer.produce();
                MAIN.post(() -> cb.onSuccess(result));
            } catch (BtcPayException e) {
                MAIN.post(() -> cb.onError(e));
            }
        });
    }

    private static Invoice parseInvoice(String json) throws BtcPayException {
        try {
            JSONObject o = new JSONObject(json);
            return new Invoice(
                    o.optString("id", ""),
                    o.optString("checkoutLink", ""),
                    o.optDouble("amount", 0),
                    o.optString("currency", "USD"),
                    o.optString("status", ""));
        } catch (JSONException e) {
            throw new BtcPayException(-1, json, e.getMessage(), e);
        }
    }

    /**
     * ES: Ejecuta una petición HTTP y devuelve el cuerpo de una respuesta 2xx.
     *     Lanza BtcPayException en cualquier otro caso. Bloqueante — llamar en segundo plano.
     * EN: Runs an HTTP request and returns the body of a 2xx response.
     *     Throws BtcPayException otherwise. Blocking — call off the main thread.
     */
    private static String request(String method, String path, @Nullable String jsonBody)
            throws BtcPayException {
        return request(method, BtcPayConfig.getUrl() + path, BtcPayConfig.getApiKey(), jsonBody);
    }

    private static String request(String method, String fullUrl, String apiKey,
                                  @Nullable String jsonBody) throws BtcPayException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(fullUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "token " + apiKey);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            if (jsonBody != null) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return readStream(conn.getInputStream());
            }
            InputStream err = conn.getErrorStream();
            String body = err != null ? readStream(err) : "";
            throw new BtcPayException(code, body, "HTTP " + code, null);
        } catch (BtcPayException e) {
            throw e;
        } catch (Exception e) {
            throw new BtcPayException(-1, null, e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) {
        Scanner sc = new Scanner(is, "UTF-8").useDelimiter("\\A");
        String result = sc.hasNext() ? sc.next() : "";
        sc.close();
        return result;
    }
}
