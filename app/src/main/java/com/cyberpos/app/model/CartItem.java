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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CartItem implements Serializable {

    private String productoId;
    private String nombre;
    private double precioUsd;
    private String categoria;
    private int cantidad;

    // ES: Descuento por producto copiado del Producto al agregar al carrito (F8)
    // EN: Per-product discount copied from Producto when added to the cart (F8)
    private String descuentoTipo = CartTotals.DISC_NONE;
    private double descuentoValor = 0;
    private String descuentoAlcance = CartTotals.ALC_ALL;
    private int descuentoMinCant = 1;

    public CartItem() {}

    public CartItem(String productoId, String nombre, double precioUsd, String categoria) {
        this.productoId = productoId;
        this.nombre = nombre;
        this.precioUsd = precioUsd;
        this.categoria = categoria;
        this.cantidad = 1;
    }

    /** ES: Subtotal con precios ORIGINALES (antes de descuento). / EN: Subtotal at ORIGINAL prices. */
    public double getSubtotal() { return precioUsd * cantidad; }

    /** ES: Precio unitario tras su descuento de producto. / EN: Unit price after its per-product discount. */
    public double getPrecioConDescuento() {
        return CartTotals.applyDiscount(precioUsd, descuentoTipo, descuentoValor);
    }

    /**
     * ES: Subtotal tras el descuento de producto, respetando su ALCANCE (F8):
     *     - todas: descuento a cada unidad
     *     - primera: descuento solo a 1 unidad
     *     - umbral: descuento a todas SOLO si cantidad ≥ N, si no ninguna
     * EN: Subtotal after the per-product discount, honoring its SCOPE (F8):
     *     all / first-unit-only / threshold (all units only if qty ≥ N).
     */
    public double getSubtotalConDescuento() {
        double full = precioUsd * cantidad;
        if (!CartTotals.isDiscount(descuentoTipo, descuentoValor) || cantidad <= 0) {
            return full;
        }
        double discUnit = getPrecioConDescuento();
        if (CartTotals.ALC_FIRST.equals(descuentoAlcance)) {
            return discUnit + precioUsd * (cantidad - 1);
        }
        if (CartTotals.ALC_THRESHOLD.equals(descuentoAlcance)) {
            int n = descuentoMinCant > 0 ? descuentoMinCant : 1;
            return cantidad >= n ? discUnit * cantidad : full;
        }
        // ALC_ALL (o nulo por compatibilidad) / ALC_ALL (or null for backward compat)
        return discUnit * cantidad;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("productoId", productoId != null ? productoId : "");
        map.put("nombre", nombre);
        map.put("precio", precioUsd);
        map.put("cantidad", cantidad);
        map.put("categoria", categoria != null ? categoria : "");
        map.put("descuentoTipo", descuentoTipo != null ? descuentoTipo : "");
        map.put("descuentoValor", descuentoValor);
        map.put("descuentoAlcance", descuentoAlcance != null ? descuentoAlcance : CartTotals.ALC_ALL);
        map.put("descuentoMinCant", descuentoMinCant);
        return map;
    }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public double getPrecioUsd() { return precioUsd; }
    public void setPrecioUsd(double precioUsd) { this.precioUsd = precioUsd; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public String getDescuentoTipo() { return descuentoTipo; }
    public void setDescuentoTipo(String descuentoTipo) { this.descuentoTipo = descuentoTipo; }

    public double getDescuentoValor() { return descuentoValor; }
    public void setDescuentoValor(double descuentoValor) { this.descuentoValor = descuentoValor; }

    public String getDescuentoAlcance() { return descuentoAlcance; }
    public void setDescuentoAlcance(String descuentoAlcance) { this.descuentoAlcance = descuentoAlcance; }

    public int getDescuentoMinCant() { return descuentoMinCant; }
    public void setDescuentoMinCant(int descuentoMinCant) { this.descuentoMinCant = descuentoMinCant; }
}
