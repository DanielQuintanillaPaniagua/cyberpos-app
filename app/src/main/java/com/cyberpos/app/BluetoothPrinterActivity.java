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

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cyberpos.app.databinding.ActivityBluetoothPrinterBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothPrinterActivity extends AppCompatActivity {

    private static final int REQ_BLUETOOTH = 201;

    private ActivityBluetoothPrinterBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter adapter;
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private String selectedMac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBluetoothPrinterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        adapter = new DeviceAdapter(devices, this::onDeviceSelected);
        binding.recyclerDevices.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerDevices.setAdapter(adapter);

        selectedMac = PrinterManager.getSavedPrinterMac(this);
        refreshCurrentPrinterCard();
        checkPermissionsAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        selectedMac = PrinterManager.getSavedPrinterMac(this);
        refreshCurrentPrinterCard();
    }

    // ── Permisos / Permissions ────────────────────────────────────────────────

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        }, REQ_BLUETOOTH);
                return;
            }
        }
        loadPairedDevices();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                            @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_BLUETOOTH) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                loadPairedDevices();
            } else {
                Toast.makeText(this,
                        "Permiso Bluetooth requerido para ver dispositivos",
                        Toast.LENGTH_LONG).show();
                showEmpty();
            }
        }
    }

    // ── Carga de dispositivos / Device loading ────────────────────────────────

    private void loadPairedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible en este dispositivo",
                    Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBt);
            return;
        }

        Set<BluetoothDevice> paired;
        try {
            paired = bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            showEmpty();
            return;
        }

        devices.clear();
        if (paired != null) {
            devices.addAll(paired);
        }

        if (devices.isEmpty()) {
            showEmpty();
        } else {
            binding.tvEmptyDevices.setVisibility(View.GONE);
            binding.recyclerDevices.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private void showEmpty() {
        binding.recyclerDevices.setVisibility(View.GONE);
        binding.tvEmptyDevices.setVisibility(View.VISIBLE);
    }

    // ── Selección de dispositivo / Device selection ──────────────────────────

    private void onDeviceSelected(BluetoothDevice device) {
        String mac  = device.getAddress();
        String name;
        try {
            name = device.getName();
        } catch (SecurityException e) {
            name = mac;
        }
        if (name == null || name.isEmpty()) name = mac;

        PrinterManager.savePrinter(this, mac, name);
        selectedMac = mac;
        adapter.setSelectedMac(mac);
        adapter.notifyDataSetChanged();
        refreshCurrentPrinterCard();

        Toast.makeText(this,
                getString(R.string.msg_printer_saved, name),
                Toast.LENGTH_SHORT).show();
    }

    private void refreshCurrentPrinterCard() {
        String name = PrinterManager.getSavedPrinterName(this);
        binding.tvCurrentPrinterName.setText(
                name != null ? name : getString(R.string.label_no_printer_configured));
    }

    // ── Adaptador / Adapter ──────────────────────────────────────────────────

    interface DeviceClickListener {
        void onClick(BluetoothDevice device);
    }

    static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {

        private final List<BluetoothDevice> devices;
        private final DeviceClickListener listener;
        private String selectedMac;

        DeviceAdapter(List<BluetoothDevice> devices, DeviceClickListener listener) {
            this.devices  = devices;
            this.listener = listener;
        }

        void setSelectedMac(String mac) { this.selectedMac = mac; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bluetooth_device, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            BluetoothDevice device = devices.get(position);
            String name;
            try {
                name = device.getName();
            } catch (SecurityException e) {
                name = null;
            }
            String mac = device.getAddress();
            if (name == null || name.isEmpty()) name = mac;

            h.tvName.setText(name);
            h.tvMac.setText(mac);

            boolean isSelected = mac.equals(selectedMac);
            h.tvSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            h.tvChevron.setVisibility(isSelected ? View.GONE : View.VISIBLE);

            BluetoothDevice finalDevice = device;
            h.itemView.setOnClickListener(v -> listener.onClick(finalDevice));
        }

        @Override
        public int getItemCount() { return devices.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvName, tvMac, tvSelected, tvChevron;

            VH(@NonNull View v) {
                super(v);
                tvName     = v.findViewById(R.id.tvDeviceName);
                tvMac      = v.findViewById(R.id.tvDeviceMac);
                tvSelected = v.findViewById(R.id.tvSelected);
                tvChevron  = v.findViewById(R.id.tvChevron);
            }
        }
    }
}
