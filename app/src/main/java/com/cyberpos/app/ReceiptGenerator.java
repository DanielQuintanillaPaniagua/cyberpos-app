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

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.cyberpos.app.model.CartItem;
import com.cyberpos.app.model.CartTotals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReceiptGenerator {

    // A4 dimensions in PostScript points
    private static final int   W = 595;
    private static final int   H = 842;
    private static final float M = 40f;   // left/right margin

    // Column X positions for items table
    private static final float COL_PRODUCT  = M;
    private static final float COL_QTY      = M + 272f;
    private static final float COL_UNIT     = M + 336f;
    private static final float COL_SUBTOTAL = W - M;   // right-aligned

    // Colors
    private static final int C_DARK    = 0xFF0D0D0D;
    private static final int C_GREEN   = 0xFF39FF14;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_TEXT    = 0xFF1A1A1A;
    private static final int C_SEC     = 0xFF757575;
    private static final int C_ROW_ALT = 0xFFF5F5F5;
    private static final int C_DIV     = 0xFFDDDDDD;

    private ReceiptGenerator() {}

    /**
     * Generates a PDF receipt in getCacheDir()/reports/ and returns the File.
     * Runs synchronously — call from a background thread.
     *
     * @param logo     merchant logo bitmap, nullable — receipt works without it
     */
    public static File generate(
            Context ctx,
            String businessName,
            String invoiceId,
            double amountUsd,
            double amountBtc,
            List<CartItem> items,
            double ivaPercent,
            double isrPercent,
            String discountType,
            double discountValue,
            Bitmap logo) throws IOException {

        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(W, H, 1).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas cv = page.getCanvas();

        float y = drawHeader(cv, businessName, logo);
        y = drawTransactionInfo(cv, invoiceId, y);
        y = drawItemsTable(cv, items, y);
        y = drawTotals(cv, amountUsd, amountBtc, items, ivaPercent, isrPercent,
                discountType, discountValue, y);
        drawFooter(cv, y);

        doc.finishPage(page);

        File out = tempFile(ctx);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        }
        doc.close();
        return out;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private static float drawHeader(Canvas cv, String businessName, Bitmap logo) {
        final float bandH = 84f;
        cv.drawRect(0, 0, W, bandH, fill(C_DARK));

        // Logo (if provided) — top-right corner, max 60×60
        if (logo != null) {
            float logoSz = 60f;
            float logoX  = W - M - logoSz;
            float logoY  = (bandH - logoSz) / 2f;
            Rect dst = new Rect((int) logoX, (int) logoY,
                    (int) (logoX + logoSz), (int) (logoY + logoSz));
            cv.drawBitmap(logo, null, dst, null);
        }

        cv.drawText("⚡ CyberPOS",
                M, 24f, text(C_GREEN, 15f, true));
        cv.drawText(businessName != null && !businessName.isEmpty() ? businessName : "Mi Negocio",
                M, 48f, text(C_WHITE, 14f, true));
        cv.drawText("RECIBO DE COMPRA",
                M, 70f, text(C_GREEN, 9f, true));

        return bandH;
    }

    // ── Transaction info ──────────────────────────────────────────────────────

    private static float drawTransactionInfo(Canvas cv, String invoiceId, float startY) {
        float y = startY + 14f;

        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy  HH:mm", new Locale("es", "SV"));
        String dateStr = df.format(new Date());
        cv.drawText("Fecha:    " + dateStr, M, y, text(C_SEC, 9f, false));
        y += 16f;

        String inv = invoiceId != null ? invoiceId : "—";
        if (inv.length() > 24) inv = inv.substring(0, 22) + "…";
        cv.drawText("Factura:  " + inv, M, y, text(C_SEC, 9f, false));
        y += 14f;

        // Neon green separator
        Paint sep = new Paint(Paint.ANTI_ALIAS_FLAG);
        sep.setColor(C_GREEN);
        sep.setStrokeWidth(1.5f);
        cv.drawLine(M, y, W - M, y, sep);

        return y + 10f;
    }

    // ── Items table ───────────────────────────────────────────────────────────

    private static float drawItemsTable(Canvas cv, List<CartItem> items, float startY) {
        if (items == null || items.isEmpty()) return startY;

        float y = startY;

        // Section label
        cv.drawText("DETALLE DE COMPRA", M, y + 12f, text(C_SEC, 8f, true));
        y += 20f;

        // Table header band
        final float ROW_H = 22f;
        cv.drawRect(M, y, W - M, y + ROW_H, fill(C_DARK));
        Paint hdr = text(C_GREEN, 8f, true);
        cv.drawText("PRODUCTO",   COL_PRODUCT  + 2, y + 15f, hdr);
        cv.drawText("CANT",       COL_QTY      + 2, y + 15f, hdr);
        cv.drawText("P/U",        COL_UNIT     + 2, y + 15f, hdr);
        drawRight(cv, "SUBTOTAL", COL_SUBTOTAL,     y + 15f, hdr);
        y += ROW_H;

        // Rows — with height overflow guard (leave ~160pt for totals + footer)
        Paint cellNorm = text(C_TEXT, 9f, false);
        Paint cellBold = text(C_TEXT, 9f, true);
        Paint divPaint = new Paint();
        divPaint.setColor(C_DIV);
        divPaint.setStrokeWidth(0.5f);

        int maxRows = (int) ((H - y - 160f) / ROW_H);
        int shown   = Math.min(items.size(), Math.max(maxRows - 1, 1));

        for (int i = 0; i < shown; i++) {
            CartItem it = items.get(i);
            if (i % 2 == 1) cv.drawRect(M, y, W - M, y + ROW_H, fill(C_ROW_ALT));

            String nombre = it.getNombre();
            if (nombre != null && nombre.length() > 30) nombre = nombre.substring(0, 28) + "…";

            cv.drawText(nombre != null ? nombre : "—",
                    COL_PRODUCT + 2, y + 15f, cellNorm);
            cv.drawText(String.valueOf(it.getCantidad()),
                    COL_QTY + 2,    y + 15f, cellBold);
            drawRight(cv, String.format(Locale.US, "$%.2f", it.getPrecioUsd()),
                    COL_UNIT + 68,  y + 15f, cellNorm);
            drawRight(cv, String.format(Locale.US, "$%.2f", it.getSubtotal()),
                    COL_SUBTOTAL,   y + 15f, cellBold);

            cv.drawLine(M, y + ROW_H, W - M, y + ROW_H, divPaint);
            y += ROW_H;
        }

        if (shown < items.size()) {
            int remaining = items.size() - shown;
            cv.drawText("… y " + remaining + " producto" + (remaining > 1 ? "s" : "") + " más",
                    COL_PRODUCT + 2, y + 14f, text(C_SEC, 8f, false));
            y += 18f;
        }

        return y + 6f;
    }

    // ── Totals ────────────────────────────────────────────────────────────────

    private static float drawTotals(Canvas cv, double amountUsd, double amountBtc,
                                     List<CartItem> items, double ivaPercent, double isrPercent,
                                     String discountType, double discountValue,
                                     float startY) {
        float y = startY + 8f;

        CartTotals t = CartTotals.compute(items, discountType, discountValue, ivaPercent, isrPercent);
        boolean hasDetail = (items != null && !items.isEmpty())
                && (t.descuento > 0 || t.impuestos > 0);

        if (hasDetail) {
            Paint labelPaint = text(C_SEC,  9f, false);
            Paint valPaint   = text(C_TEXT, 9f, false);

            y = totalRow(cv, "Subtotal", String.format(Locale.US, "$%.2f", t.subtotal),
                    labelPaint, valPaint, y);

            if (t.descuento > 0) {
                y = totalRow(cv, "Descuento", String.format(Locale.US, "-$%.2f", t.descuento),
                        labelPaint, text(C_GREEN, 9f, true), y);
            }
            if (ivaPercent > 0) {
                y = totalRow(cv,
                        String.format(Locale.US, "IVA %.0f%%", ivaPercent),
                        String.format(Locale.US, "$%.2f", t.iva),
                        labelPaint, valPaint, y);
            }
            if (isrPercent > 0) {
                y = totalRow(cv,
                        String.format(Locale.US, "ISR %.0f%%", isrPercent),
                        String.format(Locale.US, "$%.2f", t.isr),
                        labelPaint, valPaint, y);
            }
            y += 6f;
        }

        // Green separator before total
        Paint sepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sepPaint.setColor(C_GREEN);
        sepPaint.setStrokeWidth(1.5f);
        cv.drawLine(M, y, W - M, y, sepPaint);
        y += 10f;

        // TOTAL USD — large, neon green
        cv.drawText("TOTAL", M, y + 18f, text(C_SEC, 9f, true));
        drawRight(cv, String.format(Locale.US, "$%.2f USD", amountUsd),
                W - M, y + 18f, text(C_GREEN, 20f, true));
        y += 28f;

        // BTC equivalent
        if (amountBtc > 0) {
            drawRight(cv, String.format(Locale.US, "≈ %.8f BTC", amountBtc),
                    W - M, y, text(C_SEC, 9f, false));
            y += 16f;
        }

        return y;
    }

    private static float totalRow(Canvas cv, String label, String value,
                                   Paint labelPaint, Paint valuePaint, float y) {
        cv.drawText(label, M,     y + 14f, labelPaint);
        drawRight(cv, value, W - M, y + 14f, valuePaint);
        return y + 18f;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private static void drawFooter(Canvas cv, float contentBottom) {
        float y = Math.max(contentBottom + 24f, H - 60f);

        Paint line = new Paint();
        line.setColor(C_DIV);
        line.setStrokeWidth(0.5f);
        cv.drawLine(M, y, W - M, y, line);
        y += 16f;

        Paint center = text(C_TEXT, 9f, true);
        center.setTextAlign(Paint.Align.CENTER);
        cv.drawText("Pagado con Bitcoin ⚡ — CyberPOS", W / 2f, y, center);
        y += 14f;

        Paint sub = text(C_SEC, 7f, false);
        sub.setTextAlign(Paint.Align.CENTER);
        cv.drawText("github.com/cyberpos-sv  ·  GPL v3", W / 2f, y, sub);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void drawRight(Canvas cv, String text, float rightX, float y, Paint paint) {
        float tw = paint.measureText(text);
        cv.drawText(text, rightX - tw, y, paint);
    }

    private static Paint text(int color, float sizePt, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sizePt);
        if (bold) p.setTypeface(Typeface.DEFAULT_BOLD);
        return p;
    }

    private static Paint fill(int color) {
        Paint p = new Paint();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    static File tempFile(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "reports");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(dir, "recibo_" + ts + ".pdf");
    }

    /**
     * Copies a PDF file into the user-visible Downloads folder.
     * On API 29+ uses MediaStore (no permission required).
     * On API 26-28 writes directly to the Downloads directory
     * (caller must hold WRITE_EXTERNAL_STORAGE before calling this).
     * Runs synchronously — call from a background thread.
     */
    public static void saveToDownloads(Context ctx, File pdf) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, pdf.getName());
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri col = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = ctx.getContentResolver().insert(col, values);
            if (uri == null) throw new IOException("MediaStore insert failed");
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                 FileInputStream fis = new FileInputStream(pdf)) {
                copyStream(fis, os);
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, values, null, null);
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File dest = new File(dir, pdf.getName());
            try (FileInputStream fis = new FileInputStream(pdf);
                 FileOutputStream fos = new FileOutputStream(dest)) {
                copyStream(fis, fos);
            }
            MediaScannerConnection.scanFile(ctx, new String[]{dest.getAbsolutePath()}, null, null);
        }
    }

    private static void copyStream(java.io.InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
    }
}
