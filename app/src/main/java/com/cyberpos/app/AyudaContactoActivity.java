package com.cyberpos.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cyberpos.app.databinding.ActivityAyudaContactoBinding;

public class AyudaContactoActivity extends AppCompatActivity {

    private ActivityAyudaContactoBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAyudaContactoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        setupFaq();
        setupContacto();
        setupAcercaDe();
    }

    private void setupFaq() {
        setupFaqRow(binding.rowFaq1, binding.respFaq1, binding.iconFaq1);
        setupFaqRow(binding.rowFaq2, binding.respFaq2, binding.iconFaq2);
        setupFaqRow(binding.rowFaq3, binding.respFaq3, binding.iconFaq3);
    }

    private void setupFaqRow(View row, TextView respuesta, TextView icono) {
        row.setOnClickListener(v -> {
            boolean visible = respuesta.getVisibility() == View.VISIBLE;
            respuesta.setVisibility(visible ? View.GONE : View.VISIBLE);
            icono.setText(visible ? "›" : "⌄");
        });
    }

    private void setupContacto() {
        binding.rowEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:soporte@cyberpos.app"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Soporte CyberPOS");
            startActivity(Intent.createChooser(intent, "Enviar email"));
        });

        binding.rowWhatsapp.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/50370000000"));
            startActivity(intent);
        });

        binding.rowTelegram.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://t.me/cyberpos_sv"));
            startActivity(intent);
        });
    }

    private void setupAcercaDe() {
        binding.rowTerminos.setOnClickListener(v ->
            openUrl("https://docs.google.com/document/d/e/2PACX-1vSFnOO5M03b4BR7W322V_Sp-PCubqI5eut1MjFtt3ANmiXklJRP0Ed_VMJ0tt2tkRva7PQPSB5K5d01/pub"));
        binding.rowPrivacidad.setOnClickListener(v ->
            openUrl("https://cyberpos.app/privacidad"));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
