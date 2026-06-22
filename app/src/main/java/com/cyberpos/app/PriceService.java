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
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd";
    private static final long POLL_MS = 60_000L;

    private static volatile PriceService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final Runnable pollTask = this::fetch;

    private double cachedPrice = 0;
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

    private void fetch() {
        new Thread(() -> {
            double price = 0;
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
                    price = new JSONObject(body)
                            .getJSONObject("bitcoin")
                            .getDouble("usd");
                }
                conn.disconnect();
            } catch (Exception ignored) {}

            final double finalPrice = price;
            handler.post(() -> {
                if (finalPrice > 0) {
                    cachedPrice = finalPrice;
                    for (Listener l : new ArrayList<>(listeners)) {
                        l.onPriceUpdated(cachedPrice);
                    }
                }
                // Schedule next poll whether or not this one succeeded,
                // so a transient network error doesn't kill the loop.
                handler.postDelayed(pollTask, POLL_MS);
            });
        }).start();
    }
}
