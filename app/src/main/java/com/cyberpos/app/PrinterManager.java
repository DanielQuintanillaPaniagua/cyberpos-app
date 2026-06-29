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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PrinterManager {

    private static final String PREFS    = "printer_prefs";
    private static final String KEY_MAC  = "printer_mac";
    private static final String KEY_NAME = "printer_name";

    // ES: Configuración estándar ESC/POS para impresoras térmicas de 58 mm
    // EN: Standard ESC/POS settings for 58 mm thermal printers
    private static final int   DPI       = 203;
    private static final float WIDTH_MM  = 48f;
    private static final int   CHARS     = 32;

    public interface PrintCallback {
        void onSuccess();
        void onError(String message);
    }

    private PrinterManager() {}

    // ── Preferencias / Preferences ───────────────────────────────────────────

    public static void savePrinter(Context ctx, String mac, String name) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MAC,  mac)
                .putString(KEY_NAME, name)
                .apply();
    }

    public static String getSavedPrinterMac(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MAC, null);
    }

    public static String getSavedPrinterName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null);
    }

    // ── Impresión / Print ─────────────────────────────────────────────────────

    /**
     * ES: Imprime un recibo de pago en la impresora térmica Bluetooth configurada.
     *     Se ejecuta en un hilo de fondo; {@code callback} siempre se entrega en el hilo principal.
     * EN: Prints a payment receipt on the configured Bluetooth thermal printer.
     *     Runs on a background thread; {@code callback} is always delivered on the main thread.
     */
    public static void printReceipt(Context ctx,
                                     String businessName,
                                     String description,
                                     double amountUsd,
                                     double amountBtc,
                                     PrintCallback callback) {
        String mac = getSavedPrinterMac(ctx);
        if (mac == null) {
            callback.onError("no_printer");
            return;
        }

        String text = buildReceipt(businessName, description, amountUsd, amountBtc);
        Handler main = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) {
                    main.post(() -> callback.onError("Bluetooth no disponible en este dispositivo"));
                    return;
                }
                BluetoothDevice device = adapter.getRemoteDevice(mac);
                EscPosPrinter printer = new EscPosPrinter(
                        new BluetoothConnection(device), DPI, WIDTH_MM, CHARS);
                printer.printFormattedText(text);
                main.post(callback::onSuccess);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Error desconocido";
                main.post(() -> callback.onError(msg));
            }
        }).start();
    }

    // ── Diseño del recibo / Receipt layout ───────────────────────────────────

    private static String buildReceipt(String businessName, String description,
                                        double amountUsd, double amountBtc) {
        String biz  = (businessName != null && !businessName.isEmpty())
                ? businessName : "Mi Negocio";
        String desc = (description  != null && !description.isEmpty())
                ? description : "Pago Bitcoin";
        String now  = new SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault())
                .format(new Date());

        return "[C]<font size='big'><b>CyberPOS</b></font>\n"
             + "[C]<b>" + biz + "</b>\n"
             + "[C]El Salvador\n"
             + "[L]\n"
             + "[C]================================\n"
             + "[C]<b>RECIBO DE PAGO</b>\n"
             + "[C]================================\n"
             + "[L]\n"
             + "[L]Fecha:  " + now + "\n"
             + "[L]\n"
             + "[C]<font size='big'><b>$" + String.format(Locale.US, "%.2f", amountUsd) + " USD</b></font>\n"
             + "[C]" + String.format(Locale.US, "%.8f", amountBtc) + " BTC\n"
             + "[L]\n"
             + "[L]Concepto: " + desc + "\n"
             + "[L]Metodo:   Bitcoin Lightning\n"
             + "[L]Estado:   <b>CONFIRMADO</b>\n"
             + "[L]\n"
             + "[C]================================\n"
             + "[C]<b>Gracias por su pago!</b>\n"
             + "[C]CyberPOS - GPL v3\n"
             + "[L]\n"
             + "[L]\n"
             + "[L]\n";
    }
}
