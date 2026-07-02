# Política de seguridad

CyberPOS maneja pagos en Bitcoin. Nos tomamos la seguridad en serio y agradecemos la divulgación responsable.

## Reportar una vulnerabilidad

**No abras un issue público** para vulnerabilidades: eso las expone a atacantes antes de que exista un parche.

En su lugar, repórtala en privado por alguna de estas vías:

- **GitHub Security Advisories** (preferido): pestaña **Security → Report a vulnerability** del repositorio.
- **Email:** danielenriquepanigua07@gmail.com — asunto empezando con `[SECURITY]`.

Incluye, si puedes:
- Descripción del problema y su impacto.
- Pasos para reproducirlo (o una prueba de concepto).
- Versión / commit afectado.

Intentaremos responder en un plazo razonable y te mantendremos al tanto del avance. Con tu permiso, te daremos crédito cuando se publique el arreglo.

## Alcance

Interesan especialmente:
- Fugas o mal manejo de la API key de BTCPay.
- Debilidades en las [reglas de Firestore](firestore.rules) (acceso a datos de otros usuarios, escalada de rol, manipulación de pagos).
- Bypass del bloqueo biométrico o del PIN.
- Cualquier vía para marcar un pago como pagado sin que lo esté, o para leer/alterar pagos ajenos.

## Limitaciones conocidas del diseño (no son vulnerabilidades nuevas)

CyberPOS es un cliente **sin backend propio**. Estos compromisos son conocidos y están documentados; no hace falta reportarlos, pero sí tenerlos en cuenta:

- **La API key de BTCPay reside en el dispositivo del comerciante** (cifrada con Android Keystore). Recomendamos crearla con permiso mínimo (`cancreateinvoice` + `canviewinvoices`).
- **Las reglas de Firestore son la barrera principal.** El encabezado de [`firestore.rules`](firestore.rules) documenta dónde el diseño sin backend obliga a permisos más amplios de lo ideal (p. ej. lectura de pagos por usuario autenticado para el lookup por invoice, y el estado `settled` marcado por el cliente).
- **La confirmación de pago depende del polling del cliente**; un pago puede quedar `pending` si la app se cierra en el momento justo. La solución planificada es un backend con webhooks de BTCPay (ver Roadmap del README).

## Buenas prácticas para quien despliega CyberPOS

- Usa **HTTPS** en tu BTCPay Server de producción (el deployment oficial incluye Let's Encrypt gratis). Los builds de release rechazan HTTP en cleartext.
- Publica las [reglas de Firestore](firestore.rules) del repo antes de exponer la app.
- Crea la API key de BTCPay con el permiso mínimo necesario.
- Activa el bloqueo biométrico en los dispositivos de cobro.
