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

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cyberpos.app.databinding.ItemGestionProductoBinding;
import com.cyberpos.app.model.Producto;

import java.util.List;
import java.util.Locale;

public class GestionProductosAdapter extends RecyclerView.Adapter<GestionProductosAdapter.ViewHolder> {

    public interface OnItemAction {
        void onEdit(Producto producto, int position);
        void onDelete(Producto producto, int position);
    }

    private final List<Producto> productos;
    private final OnItemAction listener;

    public GestionProductosAdapter(List<Producto> productos, OnItemAction listener) {
        this.productos = productos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGestionProductoBinding binding = ItemGestionProductoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Producto p = productos.get(position);
        holder.binding.tvGestionNombre.setText(p.getNombre());
        holder.binding.tvGestionDetalles.setText(
                String.format(Locale.US, "%s • $%.2f",
                        p.getCategoria() != null ? p.getCategoria() : "—",
                        p.getPrecioUsd()));

        holder.binding.btnEditProducto.setOnClickListener(v ->
                listener.onEdit(p, holder.getAdapterPosition()));
        holder.binding.btnDeleteProducto.setOnClickListener(v ->
                listener.onDelete(p, holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() { return productos.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemGestionProductoBinding binding;
        ViewHolder(ItemGestionProductoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
