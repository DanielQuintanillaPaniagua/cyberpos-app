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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * ES: Candado biométrico local (huella/rostro). Reemplaza al 2FA por email/SMS,
 *     que era teatro (el propio dispositivo generaba y "enviaba" el código).
 *
 *     La biometría demuestra que quien abre la app es el dueño de ESTE
 *     dispositivo — es un candado de dispositivo, no de cuenta. Por eso el
 *     estado "activado" se guarda cifrado EN el dispositivo (por uid), nunca en
 *     Firestore, y la verificación la hace el sistema Android, no la app.
 *
 * EN: Local biometric lock (fingerprint/face). Replaces the email/SMS 2FA,
 *     which was theatre (the device itself generated and "sent" the code).
 *
 *     Biometrics prove whoever opens the app owns THIS device — it's a device
 *     lock, not an account factor. Hence the "enabled" flag is stored encrypted
 *     ON the device (per uid), never in Firestore, and the check is performed by
 *     Android, not by the app.
 */
public final class BiometricLock {

    private static final String PREFS = "biometric_lock";
    private static final int AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.BIOMETRIC_WEAK;

    // ES: Desbloqueado una vez por proceso — no re-preguntar al saltar de pantalla.
    // EN: Unlocked once per process — don't re-ask when moving between screens.
    private static volatile boolean unlockedThisProcess = false;

    private static volatile SharedPreferences prefs;

    private BiometricLock() {}

    /** ES: Llamar una vez desde Application.onCreate. / EN: Call once from Application.onCreate. */
    static void init(Context context) {
        Context app = context.getApplicationContext();
        try {
            MasterKey key = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            prefs = EncryptedSharedPreferences.create(app, PREFS, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            prefs = app.getSharedPreferences(PREFS + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public static boolean isEnabled(String uid) {
        SharedPreferences p = prefs;
        return p != null && uid != null && p.getBoolean(uid, false);
    }

    public static void setEnabled(String uid, boolean enabled) {
        SharedPreferences p = prefs;
        if (p == null || uid == null) return;
        p.edit().putBoolean(uid, enabled).apply();
    }

    /** ES: Estado del sensor (BiometricManager.BIOMETRIC_*). / EN: Sensor status. */
    public static int status(Context ctx) {
        return BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS);
    }

    public static boolean canAuthenticate(Context ctx) {
        return status(ctx) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** ES: Volver a exigir huella (p. ej. al cerrar sesión). / EN: Require the prompt again (e.g. on logout). */
    public static void relock() {
        unlockedThisProcess = false;
    }

    public interface Callback {
        void onUnlocked();
        void onCancelled();
    }

    /**
     * ES: Puerta de entrada. Ejecuta onUnlocked de inmediato si el candado no
     *     está activo para este uid, si ya se desbloqueó en este proceso, o si el
     *     sensor no está disponible. Si no, pide la huella.
     * EN: Entry gate. Runs onUnlocked immediately when the lock is off for this
     *     uid, already unlocked this process, or the sensor is unavailable.
     *     Otherwise it prompts.
     */
    public static void gate(FragmentActivity activity, String uid, Callback cb) {
        if (unlockedThisProcess || !isEnabled(uid) || !canAuthenticate(activity)) {
            cb.onUnlocked();
            return;
        }
        prompt(activity, activity.getString(R.string.biometric_unlock_subtitle),
                () -> { unlockedThisProcess = true; cb.onUnlocked(); },
                cb::onCancelled);
    }

    /**
     * ES: Muestra el diálogo biométrico del sistema. Usado por la puerta de
     *     entrada y por las pantallas que activan el candado.
     * EN: Shows the system biometric dialog. Used by the gate and by the screens
     *     that enable the lock.
     */
    public static void prompt(FragmentActivity activity, String subtitle,
                              Runnable onSuccess, Runnable onCancel) {
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_prompt_title))
                .setSubtitle(subtitle)
                .setNegativeButtonText(activity.getString(R.string.btn_cancel))
                .build();

        BiometricPrompt bp = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        onSuccess.run();
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                        onCancel.run();
                    }
                });
        bp.authenticate(info);
    }
}
