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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityTwoFactorBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class TwoFactorActivity extends AppCompatActivity {

    static final String EXTRA_ROLE   = "role";
    static final String EXTRA_METODO = "metodo";
    static final String EXTRA_EMAIL  = "email";

    private static final int RC_SEND_SMS = 42;

    private ActivityTwoFactorBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String uid;
    private String role;
    private String metodo;
    private String userEmail;
    private String pendingSmsCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTwoFactorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        uid       = auth.getCurrentUser().getUid();
        role      = getIntent().getStringExtra(EXTRA_ROLE);
        metodo    = getIntent().getStringExtra(EXTRA_METODO);
        userEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        if (metodo == null) metodo = "email";

        binding.btnVerificar.setOnClickListener(v -> verifyCode());
        binding.btnReenviar.setOnClickListener(v -> sendCode());

        sendCode();
    }

    // ── Code generation & delivery ────────────────────────────────────────────

    private void sendCode() {
        String code = generateOtp();
        storeOtp(code);
        if ("sms".equals(metodo)) {
            fetchPhoneAndSendSms(code);
        } else {
            sendByEmail(code);
        }
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private void storeOtp(String code) {
        long expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L;
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("expiresAt", expiresAt);
        data.put("used", false);
        db.collection("users").document(uid)
                .collection("configuracion").document("otp")
                .set(data);
    }

    private void sendByEmail(String code) {
        String to = userEmail != null ? userEmail : "";
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + to));
        intent.putExtra(Intent.EXTRA_SUBJECT, "CyberPOS - Código de verificación");
        intent.putExtra(Intent.EXTRA_TEXT,
                "Tu código de verificación CyberPOS: " + code + "\n\nVálido por 10 minutos.");
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.msg_send_2fa_code)));
            binding.tvSubtitle.setText(getString(R.string.subtitle_two_factor_email, to));
        } catch (Exception e) {
            binding.tvSubtitle.setText(getString(R.string.subtitle_two_factor_email, to));
            Toast.makeText(this, R.string.error_no_email_client, Toast.LENGTH_LONG).show();
        }
    }

    private void fetchPhoneAndSendSms(String code) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String phone = doc.getString("telefono");
                    if (phone == null || phone.isEmpty()) phone = doc.getString("phone");
                    if (phone == null || phone.isEmpty()) {
                        // No phone configured — fall back to email
                        metodo = "email";
                        sendByEmail(code);
                        return;
                    }
                    sendBySms(code, phone);
                })
                .addOnFailureListener(e -> {
                    metodo = "email";
                    sendByEmail(code);
                });
    }

    private void sendBySms(String code, String phone) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            pendingSmsCode = code;
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, RC_SEND_SMS);
            return;
        }
        doSendSms(code, phone);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_SEND_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchPhoneAndSendSms(pendingSmsCode);
            } else {
                metodo = "email";
                sendByEmail(pendingSmsCode);
            }
        }
    }

    private void doSendSms(String code, String phone) {
        try {
            String text = getString(R.string.msg_sms_2fa_code, code);
            SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            if (sms != null) sms.sendTextMessage(phone, null, text, null, null);
            binding.tvSubtitle.setText(getString(R.string.subtitle_two_factor_sms));
            Toast.makeText(this, R.string.msg_code_sent_sms, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            metodo = "email";
            sendByEmail(code);
        }
    }

    // ── Verification ──────────────────────────────────────────────────────────

    private void verifyCode() {
        String entered = binding.etCode.getText() != null
                ? binding.etCode.getText().toString().trim() : "";
        if (entered.isEmpty()) {
            binding.etCode.setError(getString(R.string.error_code_required));
            return;
        }

        setLoading(true);

        db.collection("users").document(uid)
                .collection("configuracion").document("otp")
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        setLoading(false);
                        Toast.makeText(this, R.string.error_invalid_code, Toast.LENGTH_LONG).show();
                        return;
                    }

                    String storedCode = doc.getString("code");
                    Long expiresAt    = doc.getLong("expiresAt");
                    Boolean used      = doc.getBoolean("used");

                    if (Boolean.TRUE.equals(used)) {
                        setLoading(false);
                        Toast.makeText(this, R.string.error_code_used, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                        setLoading(false);
                        Toast.makeText(this, R.string.error_code_expired, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!entered.equals(storedCode)) {
                        setLoading(false);
                        Toast.makeText(this, R.string.error_invalid_code, Toast.LENGTH_LONG).show();
                        return;
                    }

                    doc.getReference().update("used", true)
                            .addOnCompleteListener(t -> navigateToMain());
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.error_2fa_failed, Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToMain() {
        // ES: Rol desconocido/ausente → menor privilegio (cliente), nunca comerciante
        // EN: Unknown/missing role → least privilege (customer), never merchant
        Class<?> dest = "merchant".equals(role) ? MainActivity.class : CustomerHomeActivity.class;
        Intent intent = new Intent(this, dest);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        binding.btnVerificar.setEnabled(!loading);
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
