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

/**
 * ES: Moneda de visualización elegida por el cliente. Se guarda en SharedPreferences
 *     (lectura síncrona para formatear) además de en Firestore (sincronización entre
 *     dispositivos, gestionada por CustomerMonedaActivity).
 * EN: Customer's chosen display currency. Stored in SharedPreferences (synchronous read
 *     for formatting) in addition to Firestore (cross-device sync, handled by
 *     CustomerMonedaActivity).
 */
public final class CurrencyPref {

    private static final String PREF = "cyberpos_prefs";
    private static final String KEY  = "display_currency";

    public static final String USD = "USD";
    public static final String EUR = "EUR";
    public static final String BTC = "BTC";
    public static final String SAT = "SAT";

    private CurrencyPref() {}

    public static String get(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, USD);
    }

    public static void set(Context ctx, String code) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, code).apply();
    }
}
