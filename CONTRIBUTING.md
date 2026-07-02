# Contribuir a CyberPOS

¡Gracias por tu interés! CyberPOS es software libre (GPL v3) y las contribuciones son bienvenidas: código, traducciones, reportes de bugs, hardware probado o documentación.

## Antes de empezar

- **Bugs y features:** abre un [Issue](https://github.com/DanielQuintanillaPaniagua/cyberpos-app/issues) antes de escribir código, para acordar el enfoque.
- **Vulnerabilidades de seguridad:** NO abras un issue público. Sigue [SECURITY.md](SECURITY.md).
- **Idioma:** el proyecto es bilingüe (español/inglés). Escribe en el que te sea cómodo; los comentarios de código suelen ir en ambos (ES/EN).

## Montar el entorno

El setup completo (Firebase, BTCPay local en Docker/regtest, `local.properties`) está en el [README](README.md#setup-para-desarrollo-modo-demo--regtest). En resumen:

1. Crea tu propio proyecto Firebase y reemplaza `app/google-services.json`.
2. Publica [`firestore.rules`](firestore.rules) en tu Firestore.
3. Crea `local.properties` con tu SDK y credenciales de BTCPay (fallback de desarrollo).
4. Compila y prueba:

```bash
./gradlew assembleDebug        # compila
./gradlew testDebugUnitTest    # tests unitarios
./gradlew lintDebug            # lint
```

> No subas `local.properties` ni tu `google-services.json` real: ya están en `.gitignore`.

## Flujo de trabajo

1. Haz **fork** y crea una rama desde `dev` (no desde `main`):
   ```bash
   git checkout dev
   git checkout -b feature/mi-mejora
   ```
2. Haz tus cambios en commits pequeños y enfocados.
3. Asegúrate de que compila y los tests pasan antes de abrir el PR.
4. Abre el Pull Request **contra la rama `dev`**.
5. El CI (GitHub Actions) compilará, correrá tests y lint automáticamente. Un PR con el check en rojo no se mergea.

## Convención de commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/):

```
<tipo>: <descripción en imperativo>
```

Tipos comunes: `feat` (nueva función), `fix` (corrección), `chore` (mantenimiento), `docs`, `test`, `refactor`.

Ejemplos reales del historial:
```
feat: in-app BTCPay Server config with device-encrypted credentials
fix: fail-closed auth routing, least-privilege role default
test: cover CartTotals discount/tax math
```

## Estilo de código

- Java, indentación de 4 espacios, sin tabs.
- Sigue el estilo del archivo que estás tocando (nombres, densidad de comentarios, idioma de los comentarios).
- **Respeta los "puntos únicos"** documentados en el [README](README.md#arquitectura): `BtcPayClient`, `CartTotals`, `MoneyFormatter`/`CurrencyPref`, `BiometricLock`. No dupliques su lógica en otras clases.
- Los strings visibles al usuario van en `res/values/strings.xml` (español, la fuente maestra). **No hardcodees texto en el código ni en los layouts.** Las traducciones a los otros idiomas se generan automáticamente al mergear en `dev`/`main` — no las edites a mano.
- Si agregas una pantalla de comerciante con barra de navegación inferior, actualiza las 4 activities que la tienen (ver comentarios en el código).

## Tests

- La lógica pura (cálculos, parseo, formateo) debe tener tests unitarios en `app/src/test/`. Buen ejemplo: `CartTotalsTest`.
- Cambios que afecten el flujo de pago, biometría o reglas de Firestore: descríbelos y, si puedes, pruébalos en un dispositivo real (el `BiometricPrompt` y el NFC no se pueden verificar en CI).

## Traducciones

Para añadir o corregir texto, edita solo `app/src/main/res/values/strings.xml`. El workflow `sync-translations` traduce los strings faltantes a los 6 idiomas al mergear. Si quieres mejorar una traducción concreta a mano, edita el `values-XX/strings.xml` correspondiente — el workflow nunca sobrescribe traducciones existentes.

## Licencia de tus contribuciones

Al contribuir, aceptas que tu código se publique bajo **GPL v3**, igual que el resto del proyecto.
