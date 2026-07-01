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
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * ES: Credenciales de BTCPay Server del comerciante, guardadas cifradas SOLO en
 *     este dispositivo (EncryptedSharedPreferences + Android Keystore). El
 *     comerciante las ingresa en BtcPayConfigActivity — la API key nunca se
 *     compila dentro del APK ni se sube a Firestore.
 *
 *     Los valores de BuildConfig (local.properties) quedan como fallback para
 *     desarrollo: si no hay configuración guardada en el dispositivo, se usan
 *     esos. Así el flujo de desarrollo con BTCPay local en Docker no cambia.
 *
 * EN: Merchant's BTCPay Server credentials, stored encrypted ON THIS DEVICE
 *     only (EncryptedSharedPreferences + Android Keystore). The merchant enters
 *     them in BtcPayConfigActivity — the API key is never compiled into the APK
 *     nor uploaded to Firestore.
 *
 *     BuildConfig values (local.properties) remain as a development fallback:
 *     when no device config is saved, those are used. This keeps the local
 *     Docker BTCPay dev workflow unchanged.
 */
public final class BtcPayConfig {

    private static final String PREFS_ENCRYPTED = "btcpay_config";
    private static final String KEY_URL      = "url";
    private static final String KEY_STORE_ID = "store_id";
    private static final String KEY_API_KEY  = "api_key";

    private static volatile SharedPreferences prefs;

    private BtcPayConfig() {}

    /**
     * ES: Llamar una vez desde Application.onCreate. Si el Keystore del
     *     dispositivo falla (raro, pero pasa en hardware defectuoso), se usa un
     *     SharedPreferences normal para no dejar la app inutilizable.
     * EN: Call once from Application.onCreate. If the device Keystore fails
     *     (rare, but happens on broken hardware), fall back to plain
     *     SharedPreferences rather than bricking the app.
     */
    static void init(Context context) {
        Context app = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    app,
                    PREFS_ENCRYPTED,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            prefs = app.getSharedPreferences(PREFS_ENCRYPTED + "_fallback",
                    Context.MODE_PRIVATE);
        }
    }

    /** ES: URL base efectiva, sin barra final. / EN: Effective base URL, no trailing slash. */
    public static String getUrl() {
        return normalizeUrl(get(KEY_URL, BuildConfig.BTCPAY_URL));
    }

    public static String getStoreId() {
        return get(KEY_STORE_ID, BuildConfig.BTCPAY_STORE_ID);
    }

    public static String getApiKey() {
        return get(KEY_API_KEY, BuildConfig.BTCPAY_API_KEY);
    }

    /**
     * ES: true si hay credenciales guardadas por el comerciante en el dispositivo
     *     (no cuenta el fallback de BuildConfig).
     * EN: true when the merchant saved credentials on this device
     *     (the BuildConfig fallback doesn't count).
     */
    public static boolean hasDeviceConfig() {
        SharedPreferences p = prefs;
        return p != null && !p.getString(KEY_URL, "").isEmpty();
    }

    public static void save(String url, String storeId, String apiKey) {
        SharedPreferences p = prefs;
        if (p == null) return;
        p.edit()
                .putString(KEY_URL, normalizeUrl(url))
                .putString(KEY_STORE_ID, storeId.trim())
                .putString(KEY_API_KEY, apiKey.trim())
                .apply();
    }

    private static String get(String key, String buildConfigFallback) {
        SharedPreferences p = prefs;
        String saved = p != null ? p.getString(key, "") : "";
        return !saved.isEmpty() ? saved : buildConfigFallback;
    }

    private static String normalizeUrl(String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
