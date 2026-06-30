<div align="center">

# ⚡ CyberPOS

### Bitcoin POS para pequeños negocios en El Salvador

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-green.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://android.com)
[![Bitcoin](https://img.shields.io/badge/Bitcoin-Lightning%20%7C%20On--Chain-orange.svg)](https://bitcoin.org)
[![BTCPay](https://img.shields.io/badge/BTCPay-Server-blue.svg)](https://btcpayserver.org)
[![Version](https://img.shields.io/badge/Version-v2.0.0--beta-purple.svg)](https://github.com/DanielQuintanillaPaniagua/cyberpos-app/releases)
[![Made in El Salvador](https://img.shields.io/badge/Hecho%20en-El%20Salvador%20🇸🇻-blue.svg)](https://github.com/DanielQuintanillaPaniagua/cyberpos-app)

> *"Don't trust, verify."* — código abierto, auditable, sin custodios.

**Lightning Payments para tu negocio | Hecho en El Salvador 🇸🇻**

</div>

---

## ¿Qué es CyberPOS?

CyberPOS es una aplicación Android de punto de venta (POS) para recibir pagos en Bitcoin — on-chain y Lightning Network — diseñada para pequeños negocios en El Salvador y Latinoamérica.

Sin intermediarios. Sin custodia. Sin KYC. Tus llaves, tu dinero.

Se conecta a tu propio **BTCPay Server** para generar facturas reales, mostrar el QR al cliente, y confirmar el pago automáticamente. El cliente paga con cualquier wallet Bitcoin (Muun, Phoenix, Blue Wallet, etc.).

---

## Screenshots

<div align="center">

| Login | Cobros (comerciante) | Historial (comerciante) |
|:-----:|:-------------------:|:----------------------:|
| <img src="screenshots/screen_login.png" width="180"/> | <img src="screenshots/screen_comerciante_cobros.png" width="180"/> | <img src="screenshots/screen_comerciante_historial.png" width="180"/> |

| Ajustes (comerciante) | Pago (cliente) | Historial (cliente) | Ajustes (cliente) |
|:--------------------:|:--------------:|:-------------------:|:-----------------:|
| <img src="screenshots/screen_comerciante_ajustes.png" width="130"/> | <img src="screenshots/screen_cliente_pago.png" width="130"/> | <img src="screenshots/screen_cliente_historial.png" width="130"/> | <img src="screenshots/screen_cliente_ajustes.png" width="130"/> |

</div>

---

## Características

### Para el comerciante
- ⚡ Genera invoices reales vía **BTCPay Server API**
- 📱 QR de pago en pantalla con precio BTC/USD en tiempo real (CoinGecko)
- ✅ Confirmación automática de pago por polling
- 📊 Historial completo de transacciones desde Firestore
- 📄 **Reportes exportables en PDF y CSV** con filtros por fecha
- 🖨️ **Impresión térmica Bluetooth** vía protocolo ESC/POS
- 📡 **NFC tap-to-pay** — cliente acerca el teléfono y recibe el URI de pago
- 🔧 Configuración completa de BTCPay desde la app (URL, API Key, Store ID)
- 🎨 Personalización de pantalla de cobro (logo, color, mensaje, expiración del QR)
- 🏦 Gestión de cuentas bancarias e impuestos (IVA, ISR)
- 🔐 PIN de seguridad (SHA-256), 2FA, dispositivos vinculados
- 🌍 Soporte multiidioma: Español, English, Português, Français, Deutsch, Italiano
- 🟠 Modo demostración (regtest) con badge DEMO visible

### Para el cliente
- 📷 Escanea el QR del comerciante con la cámara
- 💸 Abre tu wallet Bitcoin favorita para pagar (Intent.ACTION_VIEW)
- 📋 Ve los detalles del invoice antes de pagar
- 📈 Historial de pagos realizados
- 🔒 PIN, biometría (huella/facial), notificaciones configurables

---

## Stack técnico

| Componente | Tecnología |
|---|---|
| App | Android (Java), minSdk 26, targetSdk 34 |
| Auth | Firebase Authentication |
| Base de datos | Firebase Firestore |
| Pagos | BTCPay Server 2.0.0 via REST API |
| Red Bitcoin | Bitcoin Core v25.0 (regtest/mainnet) |
| Explorer | NBXplorer 2.5.0 |
| Precio BTC | CoinGecko API (tiempo real) |
| QR Scanner | ZXing |
| HTTP | OkHttp |
| Reportes | iText7 (PDF), CSV nativo |
| Impresión | ESCPOS-ThermalPrinter (Bluetooth ESC/POS) |
| NFC | Android NFC API (nativo) |
| Seguridad | EncryptedSharedPreferences (AndroidX Security) |

---

## Hardware compatible

### Impresoras térmicas Bluetooth
- Cualquier impresora con protocolo **ESC/POS** vía Bluetooth
- Rango de precio: $15-30 USD (disponibles en AliExpress)
- Probado con: *contribuciones bienvenidas — abre un Issue con tu modelo*

---

## Requisitos

### Para el comerciante
- Android 8.0+ (API 26)
- **BTCPay Server** propio (self-hosted o en la nube)
  - [Guía de instalación](https://btcpayserver.org/deploy)
  - O usar el modo demo con Docker local (ver abajo)

### Para el cliente
- Android 8.0+ (API 26)
- Wallet Bitcoin instalada (recomendadas: [Muun](https://muun.com), [Phoenix](https://phoenix.acinq.co), [Blue Wallet](https://bluewallet.io))

---

## Setup para desarrollo (modo demo / regtest)

### 1. Clonar el repositorio

```bash
git clone https://github.com/DanielQuintanillaPaniagua/cyberpos-app.git
cd cyberpos-app
git checkout dev
```

### 2. Crear `local.properties`

Este archivo **no está en el repo** (contiene credenciales). Crealo manualmente en la raíz del proyecto:

```properties
sdk.dir=C:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
BTCPAY_API_KEY=tu_api_key_de_btcpay
BTCPAY_STORE_ID=tu_store_id
BTCPAY_URL=http://10.0.2.2:14142
```

> Para teléfono físico reemplazá `10.0.2.2` con la IP local de tu PC en la red Wi-Fi.

### 3. Levantar BTCPay Server local (Docker)

```bash
cd C:\ruta\a\BTCPayServer
docker-compose up -d
```

Accedé al dashboard en: `http://localhost:14142`

> Incluye: PostgreSQL 13, Bitcoin Core v25.0 (regtest), NBXplorer 2.5.0, BTCPay Server 2.0.0

### 4. Minar bloques iniciales en regtest ⚠️

**Paso obligatorio.** Sin bloques minados BTCPay no puede procesar pagos en regtest.

```bash
# Obtener dirección del wallet
docker exec btcpayserver-bitcoin-1 bitcoin-cli \
  -regtest -rpcuser=cyberpos -rpcpassword=cyberpos123 \
  getnewaddress

# Minar 101 bloques (reemplazá  con la dirección obtenida)
docker exec btcpayserver-bitcoin-1 bitcoin-cli \
  -regtest -rpcuser=cyberpos -rpcpassword=cyberpos123 \
  generatetoaddress 101 
```

### 5. Actualizar IP automáticamente (script PowerShell)

Si tu IP local cambia entre sesiones, corrés este script en PowerShell como administrador:

```powershell
cd C:\ruta\al\proyecto
.\actualizar-ip.ps1
```

Detecta tu IP de Wi-Fi activa y actualiza `BTCPAY_URL` en `local.properties` automáticamente.

### 6. Compilar y correr

Abrí el proyecto en Android Studio, sincronizá con Gradle y correlo en un emulador o dispositivo físico.

---

## Arquitectura

cyberpos-app/
├── app/src/main/java/com/cyberpos/app/
│   ├── activities/
│   │   ├── auth/          # Login, Register
│   │   ├── merchant/      # PaymentActivity, MerchantHistorial, Reportes, Ajustes (11 pantallas)
│   │   └── customer/      # CustomerHome, HistorialActivity, Ajustes (6 pantallas)
│   ├── services/
│   │   ├── PriceService   # Precio BTC en tiempo real via CoinGecko
│   │   ├── PrintService   # Impresión térmica Bluetooth ESC/POS
│   │   └── NfcService     # NFC tap-to-pay
│   └── utils/             # Helpers, constantes
├── app/src/main/res/
│   ├── values/            # Strings en Español (default)
│   ├── values-en/         # English
│   ├── values-pt/         # Português
│   ├── values-fr/         # Français
│   ├── values-de/         # Deutsch
│   └── values-it/         # Italiano
└── docs/
└── screenshots/       # Capturas de la app

**Persistencia Firestore:**
users/{uid}/
├── negocio/datos          # Información del negocio
├── cuentas_bancarias/     # Cuentas bancarias (subcolección)
├── transacciones/         # Historial de pagos
└── configuracion/
├── moneda
├── notificaciones
├── pantalla_cobro     # Incluye logo como Base64
├── pin                # Hash SHA-256
├── dos_fa
└── biometria

---

## Roadmap

- [x] Flujo de pago on-chain completo (BTCPay + regtest)
- [x] Historial de transacciones en tiempo real
- [x] 11 pantallas de Ajustes del comerciante
- [x] 6 pantallas de Ajustes del cliente
- [x] Multiidioma (6 idiomas)
- [x] Onboarding para nuevos comerciantes
- [x] Configuración de BTCPay desde la app
- [x] Modo demo con badge DEMO
- [x] Reportes exportables PDF/CSV
- [x] Impresión térmica Bluetooth (ESC/POS)
- [x] NFC tap-to-pay
- [ ] ⚡ Lightning Network real (LND)
- [ ] LNURL-pay — QR estático permanente
- [ ] Widget de Android para precio BTC en tiempo real
- [ ] Categorías de productos e inventario básico

---

## Contribuir

El proyecto es **GPL v3** — libre de usar, modificar y redistribuir bajo los mismos términos. El código fuente es la garantía: auditalo, forkéalo, mejoralo.

1. Fork del repo
2. Crea tu rama: `git checkout -b feature/mi-mejora`
3. Commit: `git commit -m "feat: descripción"`
4. Push: `git push origin feature/mi-mejora`
5. Abre un Pull Request

Reportá bugs o sugerencias en [GitHub Issues](https://github.com/DanielQuintanillaPaniagua/cyberpos-app/issues).

---

## Licencia
CyberPOS — Bitcoin POS para pequeños negocios
Copyright (C) 2026 Daniel Quintanilla Paniagua
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Licencia completa: [LICENSE](LICENSE) — GPL v3

---

<div align="center">

**⚡ Hecho en El Salvador 🇸🇻 con Bitcoin y código libre**

*Not your keys, not your coins.*

[GitHub](https://github.com/DanielQuintanillaPaniagua/cyberpos-app) · [Issues](https://github.com/DanielQuintanillaPaniagua/cyberpos-app/issues) · [GPL v3](https://www.gnu.org/licenses/gpl-3.0)

</div>

