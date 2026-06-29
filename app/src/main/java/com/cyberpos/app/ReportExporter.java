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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.cyberpos.app.model.Payment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReportExporter {

    // ES: A4 en puntos PostScript (1 pt = 1/72 pulgada)
    // EN: A4 in PostScript points (1 pt = 1/72 inch)
    private static final int W = 595;
    private static final int H = 842;
    private static final float M = 36f;       // ES: margen / EN: margin
    private static final float SAFE_BOTTOM = H - 48f;

    private ReportExporter() {}

    // ── PDF / PDF ────────────────────────────────────────────────────────────

    public static File generatePdf(Context ctx, List<Payment> payments,
                                    Date start, Date end, String businessName) throws IOException {
        PdfDocument doc = new PdfDocument();
        int[] pageNum = {1};

        PdfDocument.Page page = newPage(doc, pageNum);
        Canvas cv = page.getCanvas();

        float y = drawHeader(cv, businessName, start, end);
        y = drawSummary(cv, payments, y);
        y = drawTableHeader(cv, y);
        y = drawTableRows(doc, pageNum, page, cv, payments, y);

        drawFooter(cv, pageNum[0]);
        doc.finishPage(page);

        File out = reportFile(ctx, "reporte", "pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        }
        doc.close();
        return out;
    }

    private static PdfDocument.Page newPage(PdfDocument doc, int[] pageNum) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(W, H, pageNum[0]++).create();
        return doc.startPage(info);
    }

    private static float drawHeader(Canvas cv, String biz, Date start, Date end) {
        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", new Locale("es", "SV"));

        // ES: Banda oscura de encabezado
        // EN: Dark header band
        Paint bg = fillPaint(Color.BLACK);
        cv.drawRect(0, 0, W, 72, bg);

        cv.drawText("CyberPOS",
                M, 28, textPaint(Color.parseColor("#00FF88"), 20f, true));
        cv.drawText("Lightning Payments para tu negocio",
                M, 44, textPaint(Color.WHITE, 9f, false));
        cv.drawText("REPORTE DE TRANSACCIONES",
                M, 64, textPaint(Color.parseColor("#00FF88"), 12f, true));

        float y = 88f;
        cv.drawText(biz != null && !biz.isEmpty() ? biz : "Mi Negocio",
                M, y, textPaint(Color.BLACK, 15f, true));

        String range = "Período: " + df.format(start) + " — " + df.format(end);
        cv.drawText(range, M, y + 16, textPaint(Color.GRAY, 9f, false));

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.parseColor("#00FF88"));
        line.setStrokeWidth(2f);
        cv.drawLine(M, y + 26, W - M, y + 26, line);

        return y + 40;
    }

    private static float drawSummary(Canvas cv, List<Payment> payments, float y) {
        int count = payments.size();
        double btc = 0, usd = 0;
        for (Payment p : payments) {
            if ("settled".equals(p.getStatus())) {
                btc += p.getAmountBtc();
                usd += p.getAmountUsd();
            }
        }

        cv.drawRect(M, y, W - M, y + 52, fillPaint(Color.parseColor("#F0F0F0")));

        float cw = (W - 2 * M) / 3f;
        float[] xs = {M + 10, M + cw + 10, M + 2 * cw + 10};
        String[] labels = {"TRANSACCIONES", "TOTAL BTC", "TOTAL USD"};
        String[] values = {
                String.valueOf(count),
                String.format(Locale.US, "%.8f", btc),
                String.format(Locale.US, "$%.2f", usd)
        };

        Paint lbl = textPaint(Color.parseColor("#888888"), 8f, false);
        Paint val = textPaint(Color.BLACK, 13f, true);

        for (int i = 0; i < 3; i++) {
            cv.drawText(labels[i], xs[i], y + 18, lbl);
            cv.drawText(values[i], xs[i], y + 40, val);
        }

        return y + 60;
    }

    private static float drawTableHeader(Canvas cv, float y) {
        cv.drawRect(M, y, W - M, y + 22, fillPaint(Color.BLACK));

        Paint h = textPaint(Color.parseColor("#00FF88"), 8f, true);
        cv.drawText("FECHA",   M + 4,   y + 15, h);
        cv.drawText("CLIENTE", M + 90,  y + 15, h);
        cv.drawText("USD",     M + 210, y + 15, h);
        cv.drawText("BTC",     M + 265, y + 15, h);
        cv.drawText("ESTADO",  M + 370, y + 15, h);

        return y + 22;
    }

    private static float drawTableRows(PdfDocument doc, int[] pageNum,
                                        PdfDocument.Page firstPage, Canvas firstCv,
                                        List<Payment> payments, float startY) {
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault());

        Paint cell    = textPaint(Color.BLACK, 8f, false);
        Paint altBg   = fillPaint(Color.parseColor("#F7F7F7"));
        Paint green   = textPaint(Color.parseColor("#007744"), 8f, true);
        Paint amber   = textPaint(Color.parseColor("#B85C00"), 8f, false);
        Paint divider = new Paint();
        divider.setColor(Color.parseColor("#E0E0E0"));
        divider.setStrokeWidth(0.5f);

        // ES: Retornamos el estado actualizado mediante el truco del array porque podemos
        //     reemplazar página/canvas al desbordarse. Los envolvemos para que la clase interna pueda mutar.
        // EN: We return updated state via the array trick since we may replace page/canvas
        //     on overflow. Wrap them so inner class can mutate.
        PdfDocument.Page[] curPage = {firstPage};
        Canvas[] cv = {firstCv};
        float[] y = {startY};

        for (int i = 0; i < payments.size(); i++) {
            // Page overflow check
            if (y[0] + 20 > SAFE_BOTTOM) {
                drawFooter(cv[0], pageNum[0] - 1);
                doc.finishPage(curPage[0]);
                curPage[0] = newPage(doc, pageNum);
                cv[0] = curPage[0].getCanvas();
                y[0] = drawTableHeader(cv[0], 40f);
            }

            Payment p = payments.get(i);

            if (i % 2 == 1) {
                cv[0].drawRect(M, y[0], W - M, y[0] + 20, altBg);
            }

            String dateStr = p.getCreatedAt() != null ? df.format(p.getCreatedAt()) : "—";
            String name = coerce(p.getCustomerName(), "Cliente");
            if (name.length() > 18) name = name.substring(0, 16) + "…";

            cv[0].drawText(dateStr, M + 4, y[0] + 14, cell);
            cv[0].drawText(name, M + 90, y[0] + 14, cell);
            cv[0].drawText(
                    String.format(Locale.US, "$%.2f", p.getAmountUsd()),
                    M + 210, y[0] + 14, cell);
            cv[0].drawText(
                    String.format(Locale.US, "%.6f", p.getAmountBtc()),
                    M + 265, y[0] + 14, cell);

            boolean settled = "settled".equals(p.getStatus());
            cv[0].drawText(settled ? "✓ PAGADO" : "PENDIENTE",
                    M + 370, y[0] + 14, settled ? green : amber);

            cv[0].drawLine(M, y[0] + 20, W - M, y[0] + 20, divider);
            y[0] += 20;
        }

        // ES: Reemplazar las referencias de la primera página/canvas para que el llamador pueda terminar la última página
        // EN: Replace the first page/canvas references so the caller can finish the last page
        firstPage = curPage[0];
        firstCv   = cv[0];
        return y[0];
    }

    private static void drawFooter(Canvas cv, int page) {
        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("es", "SV"));
        String text = "Generado por CyberPOS · GPL v3 · " + df.format(new Date())
                + "  |  Pág. " + page;

        Paint line = new Paint();
        line.setColor(Color.parseColor("#E0E0E0"));
        line.setStrokeWidth(0.5f);
        cv.drawLine(M, H - 28, W - M, H - 28, line);

        cv.drawText(text, M, H - 14, textPaint(Color.GRAY, 7f, false));
    }

    // ── CSV / CSV ────────────────────────────────────────────────────────────

    public static File generateCsv(Context ctx, List<Payment> payments) throws IOException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        StringBuilder sb = new StringBuilder(
                "Fecha,Cliente,\"Monto USD\",\"Monto BTC\",Estado,\"ID Factura\"\n");

        for (Payment p : payments) {
            sb.append(p.getCreatedAt() != null ? df.format(p.getCreatedAt()) : "").append(',')
              .append(csvCell(coerce(p.getCustomerName(), ""))).append(',')
              .append(String.format(Locale.US, "%.2f", p.getAmountUsd())).append(',')
              .append(String.format(Locale.US, "%.8f", p.getAmountBtc())).append(',')
              .append(coerce(p.getStatus(), "pending")).append(',')
              .append(csvCell(coerce(p.getBtcPayInvoiceId(), ""))).append('\n');
        }

        File out = reportFile(ctx, "reporte", "csv");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(sb.toString().getBytes("UTF-8"));
        }
        return out;
    }

    // ── Descarga al dispositivo / Download to device ─────────────────────────

    /**
     * ES: Copia {@code tempFile} en la carpeta Downloads/CyberPOS del dispositivo.
     *     En API 29+ usa MediaStore (sin permiso adicional).
     *     En API 26-28 escribe directamente (requiere WRITE_EXTERNAL_STORAGE, el llamador debe verificar).
     *     Devuelve la ruta de visualización mostrada al usuario (p. ej. "Descargas/CyberPOS/reporte_…pdf").
     * EN: Copies {@code tempFile} into the device's Downloads/CyberPOS folder.
     *     On API 29+ uses MediaStore (no extra permission).
     *     On API 26-28 writes directly (requires WRITE_EXTERNAL_STORAGE, caller must verify).
     *
     * @return display path shown to the user (e.g. "Descargas/CyberPOS/reporte_…pdf")
     */
    public static String saveToDownloads(Context ctx, File tempFile,
                                          String displayName, String mimeType) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
            cv.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            cv.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/CyberPOS");

            Uri uri = ctx.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("MediaStore insert returned null");

            try (InputStream in  = new FileInputStream(tempFile);
                 OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Cannot open MediaStore output stream");
                copyStream(in, out);
            }
        } else {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "CyberPOS");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            try (InputStream in  = new FileInputStream(tempFile);
                 OutputStream out = new FileOutputStream(new File(dir, displayName))) {
                copyStream(in, out);
            }
        }
        return "Descargas/CyberPOS/" + displayName;
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }

    // ── Utilidades / Helpers ─────────────────────────────────────────────────

    private static File reportFile(Context ctx, String prefix, String ext) {
        File dir = new File(ctx.getCacheDir(), "reports");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(dir, prefix + "_" + ts + "." + ext);
    }

    private static Paint textPaint(int color, float sizePt, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sizePt);
        if (bold) p.setTypeface(Typeface.DEFAULT_BOLD);
        return p;
    }

    private static Paint fillPaint(int color) {
        Paint p = new Paint();
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static String coerce(String s, String fallback) {
        return (s != null && !s.isEmpty()) ? s : fallback;
    }

    private static String csvCell(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
