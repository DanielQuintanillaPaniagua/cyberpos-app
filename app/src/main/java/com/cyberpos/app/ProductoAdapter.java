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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cyberpos.app.databinding.ItemProductoGridBinding;
import com.cyberpos.app.model.Producto;

import java.util.List;
import java.util.Locale;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ViewHolder> {

    public interface OnProductoClick {
        void onClick(Producto producto);
    }

    private final List<Producto> productos;
    private final OnProductoClick listener;

    public ProductoAdapter(List<Producto> productos, OnProductoClick listener) {
        this.productos = productos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductoGridBinding binding = ItemProductoGridBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Producto p = productos.get(position);
        holder.binding.tvProductoNombre.setText(p.getNombre());
        holder.binding.tvProductoPrecio.setText(
                String.format(Locale.US, "$%.2f", p.getPrecioUsd()));

        if (p.getImagenBase64() != null && !p.getImagenBase64().isEmpty()) {
            try {
                byte[] bytes = Base64.decode(p.getImagenBase64(), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                holder.binding.ivProductoImagen.setImageBitmap(bmp);
            } catch (Exception ignored) {
                holder.binding.ivProductoImagen.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            holder.binding.ivProductoImagen.setImageResource(android.R.drawable.ic_menu_add);
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(p));
    }

    @Override
    public int getItemCount() { return productos.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemProductoGridBinding binding;
        ViewHolder(ItemProductoGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
