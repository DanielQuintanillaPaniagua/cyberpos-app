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
package com.cyberpos.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ES: Tests del punto único de cálculo del carrito. El contrato acordado:
 *     subtotal (precios originales) → descuento (por producto + global) →
 *     impuestos ADITIVOS sobre la base descontada → total.
 * EN: Tests for the single cart-math entry point. The agreed contract:
 *     subtotal (original prices) → discount (per-product + global) →
 *     ADDITIVE taxes on the discounted base → total.
 */
public class CartTotalsTest {

    private static final double EPS = 1e-9;

    private static CartItem item(double precioUsd, int cantidad) {
        CartItem it = new CartItem("id-" + precioUsd, "Producto", precioUsd, "General");
        it.setCantidad(cantidad);
        return it;
    }

    private static CartItem itemConDescuento(double precioUsd, int cantidad,
                                             String tipo, double valor, String alcance,
                                             int minCant) {
        CartItem it = item(precioUsd, cantidad);
        it.setDescuentoTipo(tipo);
        it.setDescuentoValor(valor);
        it.setDescuentoAlcance(alcance);
        it.setDescuentoMinCant(minCant);
        return it;
    }

    // ── applyDiscount ────────────────────────────────────────────────────────

    @Test
    public void applyDiscount_porcentaje() {
        assertEquals(90.0, CartTotals.applyDiscount(100, CartTotals.DISC_PERCENT, 10), EPS);
    }

    @Test
    public void applyDiscount_fijo() {
        assertEquals(70.0, CartTotals.applyDiscount(100, CartTotals.DISC_FIXED, 30), EPS);
    }

    @Test
    public void applyDiscount_nuncaNegativo() {
        // ES: Descuento fijo mayor al monto no puede dejar precio negativo
        // EN: A fixed discount larger than the amount can't go below zero
        assertEquals(0.0, CartTotals.applyDiscount(20, CartTotals.DISC_FIXED, 50), EPS);
        assertEquals(0.0, CartTotals.applyDiscount(20, CartTotals.DISC_PERCENT, 100), EPS);
    }

    @Test
    public void applyDiscount_ignoraValoresInvalidos() {
        assertEquals(100.0, CartTotals.applyDiscount(100, CartTotals.DISC_PERCENT, 0), EPS);
        assertEquals(100.0, CartTotals.applyDiscount(100, CartTotals.DISC_PERCENT, -5), EPS);
        assertEquals(100.0, CartTotals.applyDiscount(100, null, 10), EPS);
        assertEquals(100.0, CartTotals.applyDiscount(100, "tipo_desconocido", 10), EPS);
        assertEquals(100.0, CartTotals.applyDiscount(100, CartTotals.DISC_NONE, 10), EPS);
    }

    @Test
    public void isDiscount_soloTiposConocidosConValorPositivo() {
        assertTrue(CartTotals.isDiscount(CartTotals.DISC_PERCENT, 10));
        assertTrue(CartTotals.isDiscount(CartTotals.DISC_FIXED, 1));
        assertFalse(CartTotals.isDiscount(CartTotals.DISC_PERCENT, 0));
        assertFalse(CartTotals.isDiscount(CartTotals.DISC_NONE, 10));
        assertFalse(CartTotals.isDiscount(null, 10));
    }

    // ── compute: casos base / base cases ─────────────────────────────────────

    @Test
    public void compute_carritoNulo_todoCero() {
        CartTotals t = CartTotals.compute(null, CartTotals.DISC_NONE, 0, 0, 0);
        assertEquals(0.0, t.subtotal, EPS);
        assertEquals(0.0, t.descuento, EPS);
        assertEquals(0.0, t.total, EPS);
    }

    @Test
    public void compute_carritoVacio_todoCero() {
        CartTotals t = CartTotals.compute(Collections.emptyList(),
                CartTotals.DISC_NONE, 0, 0, 0);
        assertEquals(0.0, t.subtotal, EPS);
        assertEquals(0.0, t.total, EPS);
    }

    @Test
    public void compute_sinDescuentosNiImpuestos_totalIgualSubtotal() {
        List<CartItem> items = Arrays.asList(item(10, 2), item(5, 1));
        CartTotals t = CartTotals.compute(items, CartTotals.DISC_NONE, 0, 0, 0);
        assertEquals(25.0, t.subtotal, EPS);
        assertEquals(0.0, t.descuento, EPS);
        assertEquals(25.0, t.base, EPS);
        assertEquals(25.0, t.total, EPS);
    }

    // ── compute: impuestos / taxes ───────────────────────────────────────────

    @Test
    public void compute_impuestosAditivos_noCompuestos() {
        // ES: total = base + base·iva% + base·isr% (NO multiplicativo).
        //     Con base 100, IVA 13, ISR 5 → 118, no 118.65.
        // EN: total = base + base·iva% + base·isr% (NOT compounded).
        //     Base 100, IVA 13, ISR 5 → 118, not 118.65.
        List<CartItem> items = Collections.singletonList(item(100, 1));
        CartTotals t = CartTotals.compute(items, CartTotals.DISC_NONE, 0, 13, 5);
        assertEquals(13.0, t.iva, EPS);
        assertEquals(5.0, t.isr, EPS);
        assertEquals(18.0, t.impuestos, EPS);
        assertEquals(118.0, t.total, EPS);
    }

    @Test
    public void compute_descuentoAntesDeImpuestos() {
        // ES: El IVA se calcula sobre la base YA descontada: 100 − 10% = 90 → IVA 11.70
        // EN: VAT is computed on the ALREADY discounted base: 100 − 10% = 90 → VAT 11.70
        List<CartItem> items = Collections.singletonList(item(100, 1));
        CartTotals t = CartTotals.compute(items, CartTotals.DISC_PERCENT, 10, 13, 0);
        assertEquals(10.0, t.descuento, EPS);
        assertEquals(90.0, t.base, EPS);
        assertEquals(11.70, t.iva, EPS);
        assertEquals(101.70, t.total, EPS);
    }

    // ── compute: descuento global / global discount ──────────────────────────

    @Test
    public void compute_descuentoGlobalFijo() {
        List<CartItem> items = Collections.singletonList(item(50, 1));
        CartTotals t = CartTotals.compute(items, CartTotals.DISC_FIXED, 20, 0, 0);
        assertEquals(20.0, t.descuento, EPS);
        assertEquals(30.0, t.total, EPS);
    }

    @Test
    public void compute_descuentoGlobalMayorAlCarrito_totalCero() {
        List<CartItem> items = Collections.singletonList(item(15, 1));
        CartTotals t = CartTotals.compute(items, CartTotals.DISC_FIXED, 100, 13, 5);
        assertEquals(0.0, t.base, EPS);
        assertEquals(0.0, t.total, EPS);
        // ES: El descuento reportado es lo realmente descontado (15), no lo pedido (100)
        // EN: Reported discount is what was actually taken (15), not what was asked (100)
        assertEquals(15.0, t.descuento, EPS);
    }

    // ── compute: descuento por producto + global / per-product + global ─────

    @Test
    public void compute_descuentoProductoYGlobalSeSuman() {
        // ES: Producto de $20 con 50% → $10; global fijo $5 → base $5; descuento total $15
        // EN: $20 item at 50% off → $10; $5 fixed global → base $5; total discount $15
        List<CartItem> items = Collections.singletonList(
                itemConDescuento(20, 1, CartTotals.DISC_PERCENT, 50, CartTotals.ALC_ALL, 1));
        CartTotals t = CartTotals.compute(items, CartTotals.DISC_FIXED, 5, 0, 0);
        assertEquals(20.0, t.subtotal, EPS);
        assertEquals(15.0, t.descuento, EPS);
        assertEquals(5.0, t.base, EPS);
        assertEquals(5.0, t.total, EPS);
    }

    // ── Alcance del descuento por producto (F8) / per-product scope ─────────

    @Test
    public void alcanceTodas_descuentaCadaUnidad() {
        CartItem it = itemConDescuento(10, 3, CartTotals.DISC_PERCENT, 10, CartTotals.ALC_ALL, 1);
        assertEquals(27.0, it.getSubtotalConDescuento(), EPS);
    }

    @Test
    public void alcanceNulo_compatibilidad_actuaComoTodas() {
        CartItem it = itemConDescuento(10, 3, CartTotals.DISC_PERCENT, 10, null, 1);
        assertEquals(27.0, it.getSubtotalConDescuento(), EPS);
    }

    @Test
    public void alcancePrimera_descuentaSoloUnaUnidad() {
        // ES: 1 unidad a $9 + 2 unidades a $10 = $29
        // EN: 1 unit at $9 + 2 units at $10 = $29
        CartItem it = itemConDescuento(10, 3, CartTotals.DISC_PERCENT, 10, CartTotals.ALC_FIRST, 1);
        assertEquals(29.0, it.getSubtotalConDescuento(), EPS);
    }

    @Test
    public void alcanceUmbral_debajoDelMinimo_sinDescuento() {
        CartItem it = itemConDescuento(10, 2, CartTotals.DISC_PERCENT, 10,
                CartTotals.ALC_THRESHOLD, 3);
        assertEquals(20.0, it.getSubtotalConDescuento(), EPS);
    }

    @Test
    public void alcanceUmbral_alcanzandoElMinimo_descuentaTodas() {
        CartItem it = itemConDescuento(10, 3, CartTotals.DISC_PERCENT, 10,
                CartTotals.ALC_THRESHOLD, 3);
        assertEquals(27.0, it.getSubtotalConDescuento(), EPS);
    }

    @Test
    public void alcanceUmbral_minimoCeroSeTrataComoUno() {
        CartItem it = itemConDescuento(10, 1, CartTotals.DISC_PERCENT, 10,
                CartTotals.ALC_THRESHOLD, 0);
        assertEquals(9.0, it.getSubtotalConDescuento(), EPS);
    }

    @Test
    public void sinDescuentoDeProducto_subtotalConDescuentoIgualAlOriginal() {
        CartItem it = item(10, 3);
        assertEquals(30.0, it.getSubtotal(), EPS);
        assertEquals(30.0, it.getSubtotalConDescuento(), EPS);
    }

    // ── Invariantes / Invariants ─────────────────────────────────────────────

    @Test
    public void compute_desgloseSiempreConsistente() {
        // ES: Para cualquier combinación: base = subtotal − descuento,
        //     total = base + impuestos, y nada es negativo.
        // EN: For any combination: base = subtotal − discount,
        //     total = base + taxes, and nothing is negative.
        List<CartItem> items = Arrays.asList(
                itemConDescuento(12.99, 3, CartTotals.DISC_PERCENT, 15, CartTotals.ALC_FIRST, 1),
                itemConDescuento(7.50, 2, CartTotals.DISC_FIXED, 2, CartTotals.ALC_ALL, 1),
                item(0.99, 5));
        CartTotals t = CartTotals.compute(items, CartTotals.DISC_PERCENT, 5, 13, 1.5);

        assertEquals(t.base, t.subtotal - t.descuento, EPS);
        assertEquals(t.total, t.base + t.impuestos, EPS);
        assertEquals(t.impuestos, t.iva + t.isr, EPS);
        assertTrue(t.base >= 0);
        assertTrue(t.total >= 0);
        assertTrue(t.descuento >= 0);
    }
}
