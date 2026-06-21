package com.cyberpos.app;

import android.content.Context;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityCuentasBancariasBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CuentasBancariasActivity extends AppCompatActivity {

    private ActivityCuentasBancariasBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final String[] TIPOS_CUENTA = {
        "Cuenta corriente", "Cuenta de ahorros", "Cuenta monetaria"
    };

    private static class Cuenta {
        String id, banco, tipo, numero, titular;
        boolean esPrincipal;

        String getNumeroMascarado() {
            if (numero == null || numero.length() < 4) return "•••• 0000";
            return "•••• " + numero.substring(numero.length() - 4);
        }
    }

    private final List<Cuenta> cuentas = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCuentasBancariasBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAgregarCuenta.setOnClickListener(v -> showCuentaDialog(null));

        loadCuentas();
    }

    private void loadCuentas() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).collection("cuentas_bancarias")
            .get()
            .addOnSuccessListener(snapshot -> {
                cuentas.clear();
                for (QueryDocumentSnapshot doc : snapshot) {
                    Cuenta c = new Cuenta();
                    c.id = doc.getId();
                    c.banco = doc.getString("banco");
                    c.tipo = doc.getString("tipo");
                    c.numero = doc.getString("numero");
                    c.titular = doc.getString("titular");
                    Boolean principal = doc.getBoolean("esPrincipal");
                    c.esPrincipal = principal != null && principal;
                    cuentas.add(c);
                }
                renderCuentas();
            })
            .addOnFailureListener(e ->
                Toast.makeText(this, "Error al cargar cuentas", Toast.LENGTH_SHORT).show());
    }

    private void renderCuentas() {
        binding.containerCuentas.removeAllViews();
        if (cuentas.isEmpty()) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.layoutEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < cuentas.size(); i++) {
            Cuenta cuenta = cuentas.get(i);
            View card = inflater.inflate(R.layout.item_cuenta_bancaria, binding.containerCuentas, false);

            ((TextView) card.findViewById(R.id.tvBanco)).setText(cuenta.banco);
            ((TextView) card.findViewById(R.id.tvTipo)).setText(cuenta.tipo);
            ((TextView) card.findViewById(R.id.tvNumeroMasked)).setText(cuenta.getNumeroMascarado());
            ((TextView) card.findViewById(R.id.tvTitular)).setText(cuenta.titular);

            View badge = card.findViewById(R.id.tvBadgePrincipal);
            badge.setVisibility(cuenta.esPrincipal ? View.VISIBLE : View.GONE);

            final Cuenta finalCuenta = cuenta;
            card.findViewById(R.id.btnMenu).setOnClickListener(v -> showPopupMenu(v, finalCuenta));

            if (i > 0) {
                // Add separator spacing
                View spacer = new View(this);
                android.widget.LinearLayout.LayoutParams p =
                    new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 8);
                spacer.setLayoutParams(p);
                binding.containerCuentas.addView(spacer);
            }
            binding.containerCuentas.addView(card);
        }
    }

    private void showPopupMenu(View anchor, Cuenta cuenta) {
        Context wrapper = new ContextThemeWrapper(this, R.style.Theme_CyberPOS);
        PopupMenu popup = new PopupMenu(wrapper, anchor);
        popup.getMenu().add(0, 0, 0, "Editar");
        if (!cuenta.esPrincipal) {
            popup.getMenu().add(0, 1, 1, "Marcar como principal");
        }
        popup.getMenu().add(0, 2, 2, "Eliminar");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: showCuentaDialog(cuenta); return true;
                case 1: marcarComoPrincipal(cuenta); return true;
                case 2: eliminarCuenta(cuenta); return true;
            }
            return false;
        });
        popup.show();
    }

    private void marcarComoPrincipal(Cuenta principal) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // Clear principal flag on all, then set on selected
        for (Cuenta c : cuentas) {
            db.collection("users").document(uid).collection("cuentas_bancarias")
                .document(c.id).update("esPrincipal", c.id.equals(principal.id));
        }
        for (Cuenta c : cuentas) c.esPrincipal = c.id.equals(principal.id);
        renderCuentas();
    }

    private void eliminarCuenta(Cuenta cuenta) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        new MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar cuenta")
            .setMessage("¿Eliminar la cuenta " + cuenta.getNumeroMascarado() + "?")
            .setPositiveButton("Eliminar", (d, w) ->
                db.collection("users").document(uid).collection("cuentas_bancarias")
                    .document(cuenta.id)
                    .delete()
                    .addOnSuccessListener(v -> {
                        cuentas.remove(cuenta);
                        renderCuentas();
                    })
                    .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show()))
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showCuentaDialog(Cuenta cuentaEditar) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_agregar_cuenta, null);

        AutoCompleteTextView etTipo = dialogView.findViewById(R.id.etTipoCuenta);
        ArrayAdapter<String> tipoAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, TIPOS_CUENTA);
        etTipo.setAdapter(tipoAdapter);

        TextInputEditText etBanco = dialogView.findViewById(R.id.etBanco);
        TextInputEditText etNumero = dialogView.findViewById(R.id.etNumeroCuenta);
        TextInputEditText etTitular = dialogView.findViewById(R.id.etTitular);

        boolean isEdit = cuentaEditar != null;
        if (isEdit) {
            etBanco.setText(cuentaEditar.banco);
            etTipo.setText(cuentaEditar.tipo, false);
            etNumero.setText(cuentaEditar.numero);
            etTitular.setText(cuentaEditar.titular);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(isEdit ? "Editar cuenta" : "Nueva cuenta")
            .setView(dialogView)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String banco = etBanco.getText() != null ? etBanco.getText().toString().trim() : "";
                String tipo = etTipo.getText().toString().trim();
                String numero = etNumero.getText() != null ? etNumero.getText().toString().trim() : "";
                String titular = etTitular.getText() != null ? etTitular.getText().toString().trim() : "";

                if (banco.isEmpty()) { dialogView.findViewById(R.id.tilBanco).requestFocus(); return; }
                if (numero.isEmpty()) { dialogView.findViewById(R.id.tilNumeroCuenta).requestFocus(); return; }
                if (titular.isEmpty()) { dialogView.findViewById(R.id.tilTitular).requestFocus(); return; }

                guardarCuenta(cuentaEditar, banco, tipo, numero, titular);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void guardarCuenta(Cuenta cuentaEditar, String banco, String tipo,
                               String numero, String titular) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("banco", banco);
        data.put("tipo", tipo);
        data.put("numero", numero);
        data.put("titular", titular);
        data.put("esPrincipal", cuentas.isEmpty() && cuentaEditar == null);

        if (cuentaEditar != null) {
            db.collection("users").document(uid).collection("cuentas_bancarias")
                .document(cuentaEditar.id).set(data)
                .addOnSuccessListener(v -> loadCuentas())
                .addOnFailureListener(e ->
                    Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show());
        } else {
            db.collection("users").document(uid).collection("cuentas_bancarias")
                .add(data)
                .addOnSuccessListener(ref -> loadCuentas())
                .addOnFailureListener(e ->
                    Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show());
        }
    }
}
