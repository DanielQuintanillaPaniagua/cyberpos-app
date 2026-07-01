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

import java.util.List;

/**
 * ES: Desglose de un carrito. Punto ÚNICO donde se calcula subtotal → descuento →
 *     impuestos → total, para que catálogo, pago y recibo muestren lo mismo.
 *     Orden acordado: primero el descuento, luego los impuestos sobre el monto ya descontado.
 * EN: Cart breakdown. SINGLE place where subtotal → discount → taxes → total is computed,
 *     so catalog, payment and receipt all agree.
 *     Agreed order: discount first, then taxes on the already-discounted amount.
 */
public final class CartTotals {

    // ES: Tipos de descuento (compartidos por Producto, CartItem y el descuento global)
    // EN: Discount types (shared by Producto, CartItem and the global discount)
    public static final String DISC_NONE    = "";
    public static final String DISC_PERCENT = "porcentaje";
    public static final String DISC_FIXED   = "fijo";

    // ES: Alcance del descuento por producto (F8) / EN: Per-product discount scope (F8)
    public static final String ALC_ALL       = "todas";    // ES: todas las unidades / EN: all units
    public static final String ALC_FIRST     = "primera";  // ES: solo la primera / EN: first unit only
    public static final String ALC_THRESHOLD = "umbral";   // ES: a partir de N unidades / EN: from N units

    public final double subtotal;   // ES: suma de precios ORIGINALES / EN: sum of ORIGINAL prices
    public final double descuento;  // ES: descuento por producto + global / EN: per-product + global discount
    public final double base;       // ES: subtotal − descuento (base gravable) / EN: taxable base
    public final double iva;
    public final double isr;
    public final double impuestos;  // iva + isr
    public final double total;      // base + impuestos

    private CartTotals(double subtotal, double descuento, double base,
                       double iva, double isr, double impuestos, double total) {
        this.subtotal = subtotal;
        this.descuento = descuento;
        this.base = base;
        this.iva = iva;
        this.isr = isr;
        this.impuestos = impuestos;
        this.total = total;
    }

    /**
     * ES: Aplica un descuento unitario a un precio. Usado tanto para el descuento por
     *     producto como para el global. Nunca devuelve menos de 0.
     * EN: Applies a unit discount to a price. Used for both per-product and global
     *     discounts. Never returns less than 0.
     */
    public static double applyDiscount(double amount, String type, double value) {
        if (value <= 0 || type == null) return amount;
        if (DISC_PERCENT.equals(type)) return Math.max(0, amount * (1 - value / 100.0));
        if (DISC_FIXED.equals(type))   return Math.max(0, amount - value);
        return amount;
    }

    public static boolean isDiscount(String type, double value) {
        return value > 0 && (DISC_PERCENT.equals(type) || DISC_FIXED.equals(type));
    }

    /**
     * ES: Calcula el desglose completo del carrito.
     * EN: Computes the full cart breakdown.
     *
     * @param globalDiscType  tipo de descuento global (DISC_*) — "" si no hay
     * @param globalDiscValue valor del descuento global
     */
    public static CartTotals compute(List<CartItem> items,
                                     String globalDiscType, double globalDiscValue,
                                     double ivaPct, double isrPct) {
        double subtotal = 0;      // ES: precios originales / EN: original prices
        double afterProduct = 0;  // ES: tras descuento por producto / EN: after per-product discount
        if (items != null) {
            for (CartItem it : items) {
                subtotal     += it.getSubtotal();
                afterProduct += it.getSubtotalConDescuento();
            }
        }
        double descProducto = subtotal - afterProduct;

        double descGlobal = afterProduct - applyDiscount(afterProduct, globalDiscType, globalDiscValue);

        double base = afterProduct - descGlobal;
        if (base < 0) base = 0;

        double descuento = descProducto + descGlobal;
        double iva = base * ivaPct / 100.0;
        double isr = base * isrPct / 100.0;
        double impuestos = iva + isr;
        double total = base + impuestos;

        return new CartTotals(subtotal, descuento, base, iva, isr, impuestos, total);
    }
}
