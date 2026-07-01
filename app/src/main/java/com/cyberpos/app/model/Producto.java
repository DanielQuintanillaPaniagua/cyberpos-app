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

import com.google.firebase.firestore.Exclude;

public class Producto {

    @Exclude
    private String id;

    private String nombre;
    private double precioUsd;
    private String categoria;
    private String imagenBase64;
    private boolean activo;

    // ES: Stock del producto (F9). -1 = ilimitado/sin control de inventario.
    // EN: Product stock (F9). -1 = unlimited/not tracked.
    private int stock = -1;

    // ES: Descuento por producto (F8) — tipo CartTotals.DISC_* / EN: Per-product discount (F8)
    private String descuentoTipo = CartTotals.DISC_NONE;
    private double descuentoValor = 0;
    // ES: Alcance del descuento (todas/primera/umbral) y mínimo de unidades para "umbral"
    // EN: Discount scope (all/first/threshold) and minimum units for "threshold"
    private String descuentoAlcance = CartTotals.ALC_ALL;
    private int descuentoMinCant = 1;

    public Producto() {}

    public Producto(String nombre, double precioUsd, String categoria, String imagenBase64) {
        this.nombre = nombre;
        this.precioUsd = precioUsd;
        this.categoria = categoria;
        this.imagenBase64 = imagenBase64 != null ? imagenBase64 : "";
        this.activo = true;
    }

    /** ES: Precio unitario tras aplicar su descuento. / EN: Unit price after its discount. */
    @Exclude
    public double getPrecioConDescuento() {
        return CartTotals.applyDiscount(precioUsd, descuentoTipo, descuentoValor);
    }

    @Exclude
    public boolean tieneDescuento() {
        return CartTotals.isDiscount(descuentoTipo, descuentoValor);
    }

    @Exclude public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public double getPrecioUsd() { return precioUsd; }
    public void setPrecioUsd(double precioUsd) { this.precioUsd = precioUsd; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getImagenBase64() { return imagenBase64; }
    public void setImagenBase64(String imagenBase64) { this.imagenBase64 = imagenBase64; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    /** ES: true si el stock está controlado (>= 0) y llegó a cero. / EN: true if stock is tracked and is zero. */
    @Exclude
    public boolean isAgotado() { return stock == 0; }

    public String getDescuentoTipo() { return descuentoTipo; }
    public void setDescuentoTipo(String descuentoTipo) { this.descuentoTipo = descuentoTipo; }

    public double getDescuentoValor() { return descuentoValor; }
    public void setDescuentoValor(double descuentoValor) { this.descuentoValor = descuentoValor; }

    public String getDescuentoAlcance() { return descuentoAlcance; }
    public void setDescuentoAlcance(String descuentoAlcance) { this.descuentoAlcance = descuentoAlcance; }

    public int getDescuentoMinCant() { return descuentoMinCant; }
    public void setDescuentoMinCant(int descuentoMinCant) { this.descuentoMinCant = descuentoMinCant; }
}
