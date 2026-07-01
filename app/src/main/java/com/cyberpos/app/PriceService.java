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

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PriceService {

    public interface Listener {
        void onPriceUpdated(double btcUsd);
    }

    private static final String ENDPOINT =
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur";
    private static final long POLL_MS = 60_000L;

    private static volatile PriceService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final Runnable pollTask = this::fetch;

    private double cachedPrice = 0;   // ES: BTC→USD / EN: BTC→USD
    private double cachedEur = 0;     // ES: BTC→EUR / EN: BTC→EUR
    private boolean started = false;

    private PriceService() {}

    public static PriceService get() {
        if (instance == null) {
            synchronized (PriceService.class) {
                if (instance == null) instance = new PriceService();
            }
        }
        return instance;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        if (cachedPrice > 0) l.onPriceUpdated(cachedPrice);
        if (!started) {
            started = true;
            fetch();
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public double getCachedPrice() {
        return cachedPrice;
    }

    /** ES: Tasa BTC→USD en caché (0 si aún no cargada). / EN: Cached BTC→USD rate (0 if not loaded yet). */
    public double getUsdRate() {
        return cachedPrice;
    }

    /** ES: Tasa BTC→EUR en caché (0 si aún no cargada). / EN: Cached BTC→EUR rate (0 if not loaded yet). */
    public double getEurRate() {
        return cachedEur;
    }

    private void fetch() {
        new Thread(() -> {
            double price = 0;
            double eur = 0;
            try {
                HttpURLConnection conn =
                        (HttpURLConnection) new URL(ENDPOINT).openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestMethod("GET");
                conn.connect();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    Scanner sc = new Scanner(is).useDelimiter("\\A");
                    String body = sc.hasNext() ? sc.next() : "";
                    sc.close();
                    JSONObject bitcoin = new JSONObject(body).getJSONObject("bitcoin");
                    price = bitcoin.getDouble("usd");
                    eur   = bitcoin.optDouble("eur", 0);
                }
                conn.disconnect();
            } catch (Exception ignored) {}

            final double finalPrice = price;
            final double finalEur = eur;
            handler.post(() -> {
                if (finalPrice > 0) {
                    cachedPrice = finalPrice;
                    if (finalEur > 0) cachedEur = finalEur;
                    for (Listener l : new ArrayList<>(listeners)) {
                        l.onPriceUpdated(cachedPrice);
                    }
                }
                // ES: Programar el siguiente sondeo sin importar si éste fue exitoso,
                //     para que un error de red transitorio no detenga el ciclo.
                // EN: Schedule next poll whether or not this one succeeded,
                //     so a transient network error doesn't kill the loop.
                handler.postDelayed(pollTask, POLL_MS);
            });
        }).start();
    }
}
