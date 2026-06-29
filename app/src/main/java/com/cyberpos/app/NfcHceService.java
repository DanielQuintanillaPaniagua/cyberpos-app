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

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.util.Arrays;

/**
 * ES: Emula una etiqueta NFC Tipo 4 (NDEF sobre ISO 7816-4) para que el teléfono
 *     del comerciante actúe como tarjeta NFC. Cuando el cliente acerca su teléfono,
 *     el lector NDEF recoge la URI de pago automáticamente — sin etiqueta física.
 *
 *     Flujo del protocolo (controlado por el lector NFC del cliente):
 *       1. SELECT APPLICATION  (AID D2760000850101) → 9000
 *       2. SELECT FILE         (CC file  E103)       → 9000
 *       3. READ BINARY CC      (15 bytes)            → CC file + 9000
 *       4. SELECT FILE         (NDEF file E104)      → 9000
 *       5. READ BINARY NDEF    (N bytes)             → NDEF message + 9000
 *
 * EN: Emulates an NFC Type 4 Tag (NDEF over ISO 7816-4) so the merchant's phone
 *     acts as an NFC card. When the customer taps their phone, the NDEF reader
 *     picks up the payment URI automatically — no physical tag required.
 *
 *     Protocol flow (driven by the customer's NFC reader):
 *       1. SELECT APPLICATION  (AID D2760000850101) → 9000
 *       2. SELECT FILE         (CC file  E103)       → 9000
 *       3. READ BINARY CC      (15 bytes)            → CC file + 9000
 *       4. SELECT FILE         (NDEF file E104)      → 9000
 *       5. READ BINARY NDEF    (N bytes)             → NDEF message + 9000
 */
public class NfcHceService extends HostApduService {

    // ES: AID de la aplicación NDEF Type 4 Tag
    // EN: NDEF Type 4 Tag application AID
    private static final byte[] NDEF_AID = {
            (byte)0xD2, 0x76, 0x00, 0x00, (byte)0x85, 0x01, 0x01
    };

    // ES: IDs de archivo estándar
    // EN: Standard file IDs
    private static final byte[] CC_FILE_ID   = {(byte)0xE1, 0x03};
    private static final byte[] NDEF_FILE_ID = {(byte)0xE1, 0x04};

    // ES: Máquina de estados
    // EN: State machine
    private static final int ST_NONE = 0;
    private static final int ST_APP  = 1;
    private static final int ST_CC   = 2;
    private static final int ST_NDEF = 3;

    private int state = ST_NONE;

    // ES: Palabras de estado (status words)
    // EN: Status words
    private static final byte[] SW_OK  = {(byte)0x90, 0x00};
    private static final byte[] SW_ERR = {(byte)0x6A, (byte)0x82};

    // ES: Contenido del archivo NDEF compartido: prefijo de 2 bytes de longitud + bytes del mensaje NDEF.
    //     Escrito por PaymentActivity, leído por processCommandApdu en el hilo binder.
    // EN: Shared NDEF file content: 2-byte length prefix + NDEF message bytes.
    //     Written by PaymentActivity, read by processCommandApdu on the binder thread.
    private static volatile byte[] ndefFile = null;

    // ── API pública / Public API ──────────────────────────────────────────────

    /**
     * ES: Llamar cuando se muestre un nuevo QR de factura. Pasar null para limpiar.
     * EN: Call this when a new invoice QR is shown. Pass null to clear.
     */
    public static void setPaymentUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            ndefFile = null;
            return;
        }
        try {
            NdefRecord record  = NdefRecord.createUri(uri);
            NdefMessage msg    = new NdefMessage(record);
            byte[]      msgB   = msg.toByteArray();
            // ES: Archivo NDEF = longitud big-endian de 2 bytes + bytes del mensaje
            // EN: NDEF file = 2-byte big-endian length + message bytes
            byte[] file = new byte[2 + msgB.length];
            file[0] = (byte)((msgB.length >> 8) & 0xFF);
            file[1] = (byte)(msgB.length & 0xFF);
            System.arraycopy(msgB, 0, file, 2, msgB.length);
            ndefFile = file;
        } catch (Exception e) {
            ndefFile = null;
        }
    }

    // ── HostApduService / HostApduService ────────────────────────────────────

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (apdu == null || apdu.length < 4) return SW_ERR;

        byte ins = apdu[1];
        byte p1  = apdu[2];
        byte p2  = apdu[3];

        // ── SELECT (INS = A4) / SELECT (INS = A4) ───────────────────────────
        if (ins == (byte)0xA4) {

            // ES: SELECT APPLICATION por AID (P1 = 04)
            // EN: SELECT APPLICATION by AID (P1 = 04)
            if (p1 == 0x04) {
                if (apdu.length < 5) return SW_ERR;
                int aidLen = apdu[4] & 0xFF;
                if (apdu.length < 5 + aidLen) return SW_ERR;
                byte[] aid = Arrays.copyOfRange(apdu, 5, 5 + aidLen);
                if (Arrays.equals(aid, NDEF_AID)) {
                    state = ST_APP;
                    return SW_OK;
                }
                return SW_ERR;
            }

            // ES: SELECT FILE por ID (P1 = 00, P2 = 0C)
            // EN: SELECT FILE by ID (P1 = 00, P2 = 0C)
            if (p1 == 0x00 && p2 == 0x0C) {
                if (state < ST_APP || apdu.length < 5) return SW_ERR;
                int idLen  = apdu[4] & 0xFF;
                if (apdu.length < 5 + idLen) return SW_ERR;
                byte[] fid = Arrays.copyOfRange(apdu, 5, 5 + idLen);
                if (Arrays.equals(fid, CC_FILE_ID)) {
                    state = ST_CC;
                    return SW_OK;
                }
                if (Arrays.equals(fid, NDEF_FILE_ID)) {
                    state = ST_NDEF;
                    return SW_OK;
                }
                return SW_ERR;
            }
        }

        // ── READ BINARY (INS = B0) / READ BINARY (INS = B0) ────────────────
        if (ins == (byte)0xB0) {
            int offset = ((p1 & 0xFF) << 8) | (p2 & 0xFF);
            int length = apdu.length > 4 ? (apdu[4] & 0xFF) : 0;
            if (length == 0) length = 256; // ES: Le = 00 significa 256 / EN: Le = 00 means 256

            if (state == ST_CC) {
                return sliceWithSW(buildCcFile(), offset, length);
            }
            if (state == ST_NDEF) {
                byte[] file = ndefFile;
                if (file == null) return SW_ERR;
                return sliceWithSW(file, offset, length);
            }
        }

        return SW_ERR;
    }

    @Override
    public void onDeactivated(int reason) {
        state = ST_NONE;
    }

    // ── Utilidades / Helpers ─────────────────────────────────────────────────

    /**
     * ES: Capability Container (CC) — 15 bytes según la especificación NDEF Type 4 Tag.
     *     Apunta al lector hacia el archivo NDEF (E104) y lo declara legible.
     * EN: Capability Container (CC) — 15 bytes per NDEF Type 4 Tag spec.
     *     Points the reader to the NDEF file (E104) and declares it readable.
     */
    private static byte[] buildCcFile() {
        return new byte[]{
            0x00, 0x0F,          // ES: longitud del archivo CC = 15 / EN: CC file length = 15
            0x20,                // ES: versión de mapeo NDEF 2.0 / EN: NDEF Mapping version 2.0
            0x00, 0x3B,          // ES: MLe = 59 (máx. respuesta READ BINARY) / EN: MLe = 59 (max READ BINARY response)
            0x00, 0x34,          // ES: MLc = 52 (máx. datos UPDATE BINARY) / EN: MLc = 52 (max UPDATE BINARY data)
            0x04,                // ES: etiqueta NDEF File Control TLV / EN: NDEF File Control TLV tag
            0x06,                // ES: longitud del valor TLV = 6 / EN: TLV value length = 6
            (byte)0xE1, 0x04,    // ES: ID del archivo NDEF / EN: NDEF File ID
            0x0F, (byte)0xFF,    // ES: tamaño máx. del archivo NDEF = 4095 bytes / EN: Max NDEF file size = 4095 bytes
            0x00,                // ES: acceso de lectura: sin restricciones / EN: Read access: unrestricted
            (byte)0xFF           // ES: acceso de escritura: restringido (etiqueta de solo lectura) / EN: Write access: restricted (read-only tag)
        };
    }

    /**
     * ES: Devuelve data[offset..offset+length) seguido de SW 9000.
     * EN: Returns data[offset..offset+length) followed by SW 9000.
     */
    private static byte[] sliceWithSW(byte[] data, int offset, int length) {
        if (offset >= data.length) return SW_ERR;
        int end   = Math.min(offset + length, data.length);
        int chunk = end - offset;
        byte[] response = new byte[chunk + 2];
        System.arraycopy(data, offset, response, 0, chunk);
        response[chunk]     = (byte)0x90;
        response[chunk + 1] = 0x00;
        return response;
    }
}
