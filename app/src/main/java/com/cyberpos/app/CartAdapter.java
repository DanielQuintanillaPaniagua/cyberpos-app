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

import com.cyberpos.app.databinding.ItemCartBinding;
import com.cyberpos.app.model.CartItem;

import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    public interface CartChangeListener {
        void onCartChanged();
    }

    private final List<CartItem> items;
    private final CartChangeListener listener;

    public CartAdapter(List<CartItem> items, CartChangeListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCartBinding binding = ItemCartBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem item = items.get(position);
        holder.binding.tvCartItemNombre.setText(item.getNombre());
        holder.binding.tvCartItemCategoria.setText(
                item.getCategoria() != null ? item.getCategoria() : "");
        holder.binding.tvCartItemCantidad.setText(String.valueOf(item.getCantidad()));
        holder.binding.tvCartItemSubtotal.setText(
                String.format(Locale.US, "$%.2f", item.getSubtotal()));

        holder.binding.btnDecrease.setOnClickListener(v -> {
            if (item.getCantidad() > 1) {
                item.setCantidad(item.getCantidad() - 1);
                holder.binding.tvCartItemCantidad.setText(String.valueOf(item.getCantidad()));
                holder.binding.tvCartItemSubtotal.setText(
                        String.format(Locale.US, "$%.2f", item.getSubtotal()));
                listener.onCartChanged();
            }
        });

        holder.binding.btnIncrease.setOnClickListener(v -> {
            item.setCantidad(item.getCantidad() + 1);
            holder.binding.tvCartItemCantidad.setText(String.valueOf(item.getCantidad()));
            holder.binding.tvCartItemSubtotal.setText(
                    String.format(Locale.US, "$%.2f", item.getSubtotal()));
            listener.onCartChanged();
        });

        holder.binding.btnRemoveItem.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_ID) {
                items.remove(pos);
                notifyItemRemoved(pos);
                listener.onCartChanged();
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemCartBinding binding;
        ViewHolder(ItemCartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
