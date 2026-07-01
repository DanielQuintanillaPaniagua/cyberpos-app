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

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cyberpos.app.databinding.ActivityCatalogoBinding;
import com.cyberpos.app.model.CartItem;
import com.cyberpos.app.model.CartTotals;
import com.cyberpos.app.model.Producto;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CatalogoActivity extends AppCompatActivity {

    private static final String CAT_ALL = "Todos";

    private ActivityCatalogoBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final List<Producto> allProductos = new ArrayList<>();
    private final List<Producto> filteredProductos = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();

    private ProductoAdapter productoAdapter;
    private CartAdapter cartAdapter;

    private String selectedCategory = CAT_ALL;
    private double btcUsdRate = 0;
    private double ivaPercent = 0;
    private double isrPercent = 0;
    private String ivaStr = "";
    private String isrStr = "";

    // ES: Descuento global aplicado al carrito (F8) / EN: Global discount applied to the cart (F8)
    private String globalDiscType = CartTotals.DISC_NONE;
    private double globalDiscValue = 0;

    private final PriceService.Listener priceListener = price -> {
        btcUsdRate = price;
        binding.tvLivePrice.setText(String.format(Locale.US, "$%,.0f USD", price));
        updateCartTotal();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCatalogoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        productoAdapter = new ProductoAdapter(filteredProductos, this::addToCart);
        binding.recyclerProductos.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerProductos.setAdapter(productoAdapter);

        cartAdapter = new CartAdapter(cart, this::onCartChanged);
        binding.recyclerCart.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerCart.setAdapter(cartAdapter);
        binding.recyclerCart.setNestedScrollingEnabled(false);

        binding.btnCobrar.setOnClickListener(v -> cobrar());
        binding.btnAplicarDescuento.setOnClickListener(v -> showGlobalDiscountDialog());
        binding.btnCantidadManual.setOnClickListener(v ->
                startActivity(new Intent(this, PaymentActivity.class)));
        binding.btnGestionarProductos.setOnClickListener(v ->
                startActivity(new Intent(this, GestionProductosActivity.class)));

        setupBottomNav();
        updateCartPanel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.bottomNav.setSelectedItemId(R.id.nav_cobros);
        PriceService.get().addListener(priceListener);
        loadTaxConfig();
        loadProductos();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PriceService.get().removeListener(priceListener);
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    private void loadTaxConfig() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid)
                .collection("configuracion").document("impuestos")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        ivaStr = doc.getString("iva");
                        isrStr = doc.getString("isr");
                        if (ivaStr == null) ivaStr = "";
                        if (isrStr == null) isrStr = "";
                        try { ivaPercent = !ivaStr.isEmpty() ? Double.parseDouble(ivaStr) : 0; }
                        catch (NumberFormatException e) { ivaPercent = 0; }
                        try { isrPercent = !isrStr.isEmpty() ? Double.parseDouble(isrStr) : 0; }
                        catch (NumberFormatException e) { isrPercent = 0; }
                        updateCartTotal();
                    }
                });
    }

    private void loadProductos() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).collection("productos")
                .whereEqualTo("activo", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    allProductos.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Producto p = doc.toObject(Producto.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            allProductos.add(p);
                        }
                    }
                    buildCategoryChips();
                    filterByCategory(selectedCategory);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_load_products, Toast.LENGTH_SHORT).show());
    }

    // ── Category chips ────────────────────────────────────────────────────────

    private void buildCategoryChips() {
        binding.chipGroupCategories.removeAllViews();

        Set<String> cats = new LinkedHashSet<>();
        cats.add(CAT_ALL);
        for (Producto p : allProductos) {
            if (p.getCategoria() != null && !p.getCategoria().isEmpty()) {
                cats.add(p.getCategoria());
            }
        }

        for (String cat : cats) {
            Chip chip = new Chip(this);
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setChecked(cat.equals(selectedCategory));
            chip.setTypeface(Typeface.MONOSPACE);
            chip.setTextSize(12f);
            chip.setOnClickListener(v -> {
                selectedCategory = cat;
                filterByCategory(cat);
            });
            binding.chipGroupCategories.addView(chip);
        }
    }

    private void filterByCategory(String category) {
        filteredProductos.clear();
        if (CAT_ALL.equals(category)) {
            filteredProductos.addAll(allProductos);
        } else {
            for (Producto p : allProductos) {
                if (category.equals(p.getCategoria())) filteredProductos.add(p);
            }
        }
        productoAdapter.notifyDataSetChanged();

        boolean empty = filteredProductos.isEmpty();
        binding.tvEmptyProducts.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerProductos.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ── Cart ──────────────────────────────────────────────────────────────────

    private void addToCart(Producto p) {
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).getProductoId().equals(p.getId())) {
                cart.get(i).setCantidad(cart.get(i).getCantidad() + 1);
                cartAdapter.notifyItemChanged(i);
                onCartChanged();
                return;
            }
        }
        CartItem item = new CartItem(p.getId(), p.getNombre(), p.getPrecioUsd(), p.getCategoria());
        item.setDescuentoTipo(p.getDescuentoTipo());
        item.setDescuentoValor(p.getDescuentoValor());
        item.setDescuentoAlcance(p.getDescuentoAlcance());
        item.setDescuentoMinCant(p.getDescuentoMinCant());
        cart.add(item);
        cartAdapter.notifyItemInserted(cart.size() - 1);
        updateCartPanel();
        updateCartTotal();
    }

    private void onCartChanged() {
        updateCartPanel();
        updateCartTotal();
    }

    private void updateCartPanel() {
        boolean hasItems = !cart.isEmpty();
        binding.tvCartEmpty.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        binding.recyclerCart.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.btnCobrar.setEnabled(hasItems);

        int count = 0;
        for (CartItem item : cart) count += item.getCantidad();
        binding.tvCartItemCount.setText(count > 0
                ? String.format(Locale.US, "%d ítem%s", count, count == 1 ? "" : "s") : "");
    }

    private void updateCartTotal() {
        CartTotals t = CartTotals.compute(cart, globalDiscType, globalDiscValue, ivaPercent, isrPercent);

        binding.tvTotalUsd.setText(String.format(Locale.US, "$%.2f USD", t.total));
        if (btcUsdRate > 0) {
            binding.tvTotalBtc.setText(String.format(Locale.US, "≈ %.8f BTC", t.total / btcUsdRate));
        } else {
            binding.tvTotalBtc.setText("≈ — BTC");
        }

        // ── Desglose: subtotal / descuento / impuestos (F8) ──
        if (t.descuento > 0 || t.impuestos > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "%s: $%.2f",
                    getString(R.string.label_subtotal), t.subtotal));
            if (t.descuento > 0) {
                sb.append(String.format(Locale.US, "\n%s: -$%.2f",
                        getString(R.string.label_discount), t.descuento));
            }
            if (ivaPercent > 0) sb.append(String.format(Locale.US, "\nIVA %.0f%%: $%.2f", ivaPercent, t.iva));
            if (isrPercent > 0) sb.append(String.format(Locale.US, "\nISR %.0f%%: $%.2f", isrPercent, t.isr));
            binding.tvBreakdown.setText(sb.toString());
            binding.tvBreakdown.setVisibility(View.VISIBLE);
        } else {
            binding.tvBreakdown.setVisibility(View.GONE);
        }
    }

    // ── Descuento global / Global discount (F8) ───────────────────────────────

    private void showGlobalDiscountDialog() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, 0);

        RadioGroup rg = new RadioGroup(this);
        RadioButton rbPct = new RadioButton(this);
        rbPct.setId(View.generateViewId());
        rbPct.setText(getString(R.string.discount_percent));
        RadioButton rbFix = new RadioButton(this);
        rbFix.setId(View.generateViewId());
        rbFix.setText(getString(R.string.discount_fixed));
        rg.addView(rbPct);
        rg.addView(rbFix);

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint(R.string.hint_discount_value);

        // ES: Precargar el descuento actual / EN: Prefill the current discount
        if (CartTotals.DISC_FIXED.equals(globalDiscType)) rg.check(rbFix.getId());
        else rg.check(rbPct.getId());
        if (globalDiscValue > 0) et.setText(String.format(Locale.US, "%.2f", globalDiscValue));

        layout.addView(rg);
        layout.addView(et);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.btn_apply_discount)
                .setView(layout)
                .setPositiveButton(R.string.btn_save_product, (d, w) -> {
                    String code = rg.getCheckedRadioButtonId() == rbFix.getId()
                            ? CartTotals.DISC_FIXED : CartTotals.DISC_PERCENT;
                    double value;
                    try {
                        value = Double.parseDouble(et.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        value = 0;
                    }
                    if (value <= 0
                            || (CartTotals.DISC_PERCENT.equals(code) && value > 100)) {
                        Toast.makeText(this, R.string.error_discount_value, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    globalDiscType = code;
                    globalDiscValue = value;
                    updateCartTotal();
                })
                .setNegativeButton(R.string.btn_cancel, null);

        if (CartTotals.isDiscount(globalDiscType, globalDiscValue)) {
            b.setNeutralButton(R.string.btn_remove_discount, (d, w) -> {
                globalDiscType = CartTotals.DISC_NONE;
                globalDiscValue = 0;
                updateCartTotal();
            });
        }
        b.show();
    }

    private void cobrar() {
        if (cart.isEmpty()) return;
        CartTotals t = CartTotals.compute(cart, globalDiscType, globalDiscValue, ivaPercent, isrPercent);

        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra(PaymentActivity.EXTRA_AMOUNT, t.total);
        intent.putExtra(PaymentActivity.EXTRA_CART_ITEMS, new ArrayList<>(cart));
        intent.putExtra(PaymentActivity.EXTRA_IVA_PERCENT, ivaPercent);
        intent.putExtra(PaymentActivity.EXTRA_ISR_PERCENT, isrPercent);
        intent.putExtra(PaymentActivity.EXTRA_DISCOUNT_TYPE, globalDiscType);
        intent.putExtra(PaymentActivity.EXTRA_DISCOUNT_VALUE, globalDiscValue);
        startActivity(intent);
    }

    // ── Bottom nav ────────────────────────────────────────────────────────────

    private void setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_merchant_history) {
                startActivity(new Intent(this, MerchantHistorialActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_dashboard) {
                startActivity(new Intent(this, DashboardActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } else if (id == R.id.nav_merchant_settings) {
                startActivity(new Intent(this, MerchantAjustesActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
            return true;
        });
    }
}
