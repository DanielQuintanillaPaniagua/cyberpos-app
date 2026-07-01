#!/usr/bin/env python3
"""
sync_strings.py — Sincroniza traducciones Android automáticamente.

Lee app/src/main/res/values/strings.xml (español) como fuente maestra
y añade los strings que falten en cada idioma traduciéndolos con
Google Translate (sin API key, vía deep-translator).

SOLO añade strings faltantes. NUNCA sobreescribe traducciones existentes.

Uso:
    pip install deep-translator
    python scripts/sync_strings.py

Opcional — ver qué falta sin traducir:
    python scripts/sync_strings.py --dry-run
"""

import os
import re
import sys
import time

DRY_RUN = '--dry-run' in sys.argv

try:
    from deep_translator import GoogleTranslator
except ImportError:
    print('Instala primero:  pip install deep-translator')
    sys.exit(1)

# ── Configuración ─────────────────────────────────────────────────────────────

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
RES_DIR    = os.path.join(SCRIPT_DIR, '..', 'app', 'src', 'main', 'res')

# carpeta values-XX : código ISO para Google Translate
TARGETS = {
    'en': 'en',
    'pt': 'pt',
    'fr': 'fr',
    'de': 'de',
    'it': 'it',
}

# Keys que se copian sin traducir (nombres de marca, comandos, valores fijos)
NO_TRANSLATE = {
    'app_name',
    'value_version',
    'value_fee',
    'value_time',
    'value_network',
    'value_network_onchain',
    'value_currency',
    'format_usd_equivalent',
    'nfc_service_desc',
    'msg_regtest_cmd',
    'msg_address_copied',
    'label_btc_amount',
    'label_usd_equivalent',
    'label_wallet_btc',
    'label_wallet_usd',
    'label_btc_price',
}

API_DELAY = 0.45  # segundos entre llamadas (evitar rate-limit de Google)

# ── Parseo ────────────────────────────────────────────────────────────────────

# Captura: grupo1=tag-de-apertura, grupo2=name, grupo3=valor, grupo4=cierre
_TAG_RE = re.compile(
    r'(<string\b([^>]*)>)(.*?)(</string>)',
    re.DOTALL
)
_NAME_RE = re.compile(r'\bname="([^"]+)"')


def parse_keys(path):
    """Devuelve {name: raw_value} para todos los strings traducibles del archivo."""
    with open(path, encoding='utf-8') as f:
        src = f.read()
    out = {}
    for m in _TAG_RE.finditer(src):
        attrs  = m.group(2)
        value  = m.group(3)
        name_m = _NAME_RE.search(attrs)
        if not name_m:
            continue
        name = name_m.group(1)
        if 'translatable="false"' in attrs:
            continue
        out[name] = value
    return out


# ── Traducción ────────────────────────────────────────────────────────────────

# Tokens seguros que Google Translate no modifica (fullwidth brackets)
_PH_RE = re.compile(
    r'%\d+\$[sdfe]'       # %1$s  %2$d  %1$f  etc.
    r"|\\n"               # \n  (salto de línea Android)
    r"|\\'"               # \'  (apóstrofe escapado Android)
    r'|&(?:amp|lt|gt|quot|apos);'  # entidades XML
    r'|&#\d+;'            # entidades numéricas XML
)


def _protect(text):
    """Sustituye placeholders por tokens ｟N｠ para que el traductor no los toque."""
    tokens = []

    def _sub(m):
        tokens.append(m.group(0))
        return f'｟{len(tokens) - 1}｠'

    return _PH_RE.sub(_sub, text), tokens


def _restore(text, tokens):
    """Restaura los tokens ｟N｠ por sus valores originales."""
    for i, t in enumerate(tokens):
        text = text.replace(f'｟{i}｠', t)
    return text


def translate_value(value, target_lang):
    """Traduce el valor de un string Android preservando placeholders y escapes."""
    stripped = value.strip()
    if not stripped:
        return value

    # Preparar texto para el traductor
    raw = value.replace("\\'", "'")   # unescape apostrofes temporalmente
    protected, tokens = _protect(raw)

    try:
        result = GoogleTranslator(source='es', target=target_lang).translate(protected)
        if not result:
            return value
    except Exception as e:
        print(f'      ⚠  Error al traducir: {e}')
        return value

    result = _restore(result, tokens)
    result = result.replace("'", "\\'")   # re-escapar apostrofes para Android XML
    return result


# ── Escritura ─────────────────────────────────────────────────────────────────

def append_missing(path, missing_keys, source_values, target_lang):
    """Inserta los strings traducidos justo antes de </resources>."""
    with open(path, encoding='utf-8') as f:
        content = f.read()

    lines = ['\n    <!-- Auto-traducido por sync_strings.py -->']

    for name in missing_keys:
        original = source_values[name]

        if name in NO_TRANSLATE:
            lines.append(f'    <string name="{name}">{original}</string>')
        else:
            translated = translate_value(original, target_lang)
            time.sleep(API_DELAY)
            lines.append(f'    <string name="{name}">{translated}</string>')

    block = '\n'.join(lines) + '\n'
    content = content.replace('</resources>', block + '</resources>')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    source_path = os.path.join(RES_DIR, 'values', 'strings.xml')

    if not os.path.exists(source_path):
        print(f'Error: no se encontró {source_path}')
        sys.exit(1)

    source = parse_keys(source_path)
    mode   = ' [DRY-RUN]' if DRY_RUN else ''
    print(f'Fuente: {len(source)} strings  (es){mode}\n')

    total_added = 0

    for folder, lang_code in TARGETS.items():
        target_path = os.path.join(RES_DIR, f'values-{folder}', 'strings.xml')

        if not os.path.exists(target_path):
            print(f'  ⚠  {folder}: archivo no encontrado, omitido')
            continue

        existing = parse_keys(target_path)
        missing  = [k for k in source if k not in existing]

        if not missing:
            print(f'  ✓  {folder}: sin cambios')
            continue

        if DRY_RUN:
            print(f'  →  {folder}: {len(missing)} strings faltantes:')
            for k in missing:
                print(f'       • {k}')
            continue

        print(f'  →  {folder}: {len(missing)} strings nuevos')
        for name in missing:
            if name not in NO_TRANSLATE:
                print(f'       traduciendo: {name}')
        append_missing(target_path, missing, source, lang_code)
        total_added += len(missing)
        print(f'     ✓ listo\n')

    if not DRY_RUN:
        print(f'Sincronización completada — {total_added} strings añadidos en total.')
    else:
        print('\n(--dry-run: no se escribió nada)')


if __name__ == '__main__':
    main()
