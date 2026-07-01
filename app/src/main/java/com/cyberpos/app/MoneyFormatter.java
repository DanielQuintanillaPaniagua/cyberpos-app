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

import java.util.Locale;

/**
 * ES: Formatea un importe en la moneda de visualización elegida por el cliente
 *     (ver {@link CurrencyPref}). El importe canónico es siempre BTC; las tasas en
 *     vivo (USD/EUR) vienen de {@link PriceService}. Punto único de formateo para
 *     que todas las pantallas del cliente muestren los precios de forma consistente.
 * EN: Formats an amount in the customer's chosen display currency (see
 *     {@link CurrencyPref}). The canonical amount is always BTC; live USD/EUR rates
 *     come from {@link PriceService}. Single formatting choke point so every customer
 *     screen shows prices consistently.
 */
public final class MoneyFormatter {

    private static final double SATS_PER_BTC = 100_000_000d;
    private static final String PLACEHOLDER  = "≈ —";

    private MoneyFormatter() {}

    /**
     * ES: Equivalente en vivo a partir del monto BTC, usando las tasas actuales.
     * EN: Live equivalent from a BTC amount, using current rates.
     */
    public static String equivalent(Context ctx, double btc) {
        double usdRate = PriceService.get().getUsdRate();
        double eurRate = PriceService.get().getEurRate();
        switch (CurrencyPref.get(ctx)) {
            case CurrencyPref.EUR:
                return eurRate > 0
                        ? String.format(Locale.US, "≈ €%.2f EUR", btc * eurRate) : PLACEHOLDER;
            case CurrencyPref.BTC:
                return String.format(Locale.US, "≈ %.8f BTC", btc);
            case CurrencyPref.SAT:
                return String.format(Locale.US, "≈ %,.0f sats", btc * SATS_PER_BTC);
            case CurrencyPref.USD:
            default:
                return usdRate > 0
                        ? String.format(Locale.US, "≈ $%.2f USD", btc * usdRate) : PLACEHOLDER;
        }
    }

    /**
     * ES: Equivalente para el historial. Para USD usa el valor histórico guardado; para
     *     EUR lo deriva proporcionalmente a la tasa actual; para BTC/SAT usa el monto BTC.
     * EN: Equivalent for history. USD uses the stored historical value; EUR derives it
     *     proportionally at the current rate; BTC/SAT use the BTC amount.
     */
    public static String historyEquivalent(Context ctx, double btc, double storedUsd) {
        double usdRate = PriceService.get().getUsdRate();
        double eurRate = PriceService.get().getEurRate();
        switch (CurrencyPref.get(ctx)) {
            case CurrencyPref.EUR:
                return (eurRate > 0 && usdRate > 0)
                        ? String.format(Locale.US, "≈ €%.2f EUR", storedUsd * eurRate / usdRate)
                        : PLACEHOLDER;
            case CurrencyPref.BTC:
                return String.format(Locale.US, "≈ %.8f BTC", btc);
            case CurrencyPref.SAT:
                return String.format(Locale.US, "≈ %,.0f sats", btc * SATS_PER_BTC);
            case CurrencyPref.USD:
            default:
                return String.format(Locale.US, "≈ $%.2f USD", storedUsd);
        }
    }

    /**
     * ES: Precio de referencia "1 BTC = …" para el ticker en vivo. Para BTC/SAT no tiene
     *     sentido, así que cae a USD.
     * EN: "1 BTC = …" reference price for the live ticker. Meaningless for BTC/SAT, so it
     *     falls back to USD.
     */
    public static String btcTicker(Context ctx) {
        double usdRate = PriceService.get().getUsdRate();
        double eurRate = PriceService.get().getEurRate();
        if (CurrencyPref.EUR.equals(CurrencyPref.get(ctx)) && eurRate > 0) {
            return String.format(Locale.US, "1 BTC = €%,.0f", eurRate);
        }
        return usdRate > 0 ? String.format(Locale.US, "1 BTC = $%,.0f", usdRate) : "1 BTC = —";
    }
}
