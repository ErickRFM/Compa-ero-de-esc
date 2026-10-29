# Rotación HMAC SII → Compañero — 25 de octubre de 2026 (CDMX)

Actualización posterior: el usuario autorizó activar la sincronización. Ambos flags están habilitados y el primer lote del programador pasó; ver [informe de activación](SIII_SYNC_ACTIVATION_2026-10-25.md). El estado OFF descrito abajo corresponde al cierre previo de la rotación.

**Resultado: rechazo HMAC 401 resuelto; rotación aplicada y verificada. Sincronización productiva desactivada.** No se crearon datos académicos, worktrees ni APKs. Se conservaron los cambios previos.

## Identidad y credencial

- MongoDB: Cluster0 → `companero_dev`.
- Institución aprobada: `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`, `SII Institucional`, `registrationEnabled=false`.
- keyId nuevo: `siii-hmac-20261025-138dedb2ba5840759868ff77844616a7`.
- Credencial persistida: enabled true, scope `group:write`, tenant exacto aprobado, expiración UNIX `1793591855` (`2026-11-02 03:57:35 UTC`). Se consultaron únicamente metadatos; no se leyó `secretCiphertext`.
- Se generó un solo secreto de 32 bytes, custodiado primero con DPAPI CurrentUser y ACL exclusiva fuera de Git. El mismo secreto se cargó en ambos servicios. No aparece en este informe, código, mensajes ni salidas de diagnóstico.
- No se sobrescribió ni reactivó una credencial anterior. `siii-group-01` sigue sin documento Mongo en la última consulta.

## Despliegues finales y dominio servido

| Servicio | Despliegue | Estado | SHA |
| --- | --- | --- | --- |
| Compañero / Render | `dep-dbfe57ivcj2c73fq61n0` | live, production | `55a1b99780ddef2cb2e9add7f82f6d6124965628` |
| SII / Vercel | `dpl_GeJXCYJdF2BmwMrUc8sitCDKQwiC` | READY | `1699415748b71fad8f11a8754096e2ccc52d6b69` |

La consulta del alias confirmó que `siii-three.vercel.app` sirve exactamente el despliegue final indicado, dentro del proyecto autorizado, sin redirección. La compilación normal de SII pasó con una guarda explícita que exige ambos flags exactamente false y JSON de destino válido antes de `npm run build`. No se repitieron matrices de pruebas.

Comprobaciones HTTP finales: Compañero `/health` 200 con `environment=production` y `/ready` 200; SII `/` 200. El operador cambió APP_ENV después del cierre inicial: `dep-dbfe57ivcj2c73fq61n0` quedó live a `2026-10-26T05:19:18Z`. Se repitió únicamente la verificación focal de firmas/guardas después de ese cambio, con los mismos resultados satisfactorios de la tabla siguiente y exit 0.

## Resultados reales de autenticación

Se utilizaron primero el lector y firmador TypeScript existentes de SII en ejecuciones protegidas, sin enviar snapshots. Tanto el destino temporal como el activo pasaron HTTP 200 con contrato `siii.projection_status.v1` y HTTP 401 ante firma inválida. Esos deployments de observación finalizaron intencionalmente antes del build de aplicación (salida 17); su estado ERROR no representa un fallo HMAC.

Después de restaurar el destino activo borrado accidentalmente, eliminar temporales y desplegar la aplicación final, `Test-SiiiHmacRotationLive.ps1` verificó de nuevo por red con la misma custodia:

| Observación | HTTP | Resultado |
| --- | --- | --- |
| Compañero projection-status, firma válida y nonce nuevo | 200 | Contrato verificado, referencia de observación inexistente |
| Compañero projection-status, firma inválida y nonce independiente | 401 | Rechazo conservado |
| SII authority, firma válida | 503 | `SOURCE_AUTHORITY_DISABLED`, guardia esperada con productor apagado |
| SII authority, firma inválida | 401 | Rechazo conservado |

La ejecución final terminó con exit 0 / Passed true. Una primera comprobación local no concluyó; se añadieron diagnósticos seguros por etapa y la siguiente pasada terminó correctamente, sin cambiar configuración ni secretos entre ambas. No se atribuye una causa no acreditada a aquel fallo local.

El 503 de autoridad demuestra la guardia de desactivación después de autenticar; no acredita entrega de un manifest académico ni sincronización de datos. No se habilitaron flags para eludirla y no se realizaron escrituras académicas.

## Configuración y limpieza

- `SIII_ACADEMIC_OUTBOX_ENABLED=false`.
- `SIII_COMPANION_DELIVERY_ENABLED=false`.
- El destino activo de SII está como Secret/sensitive, solo Production. Tras la restauración su ID es `nNnjvqMEmgICIXWd`; la verificación de autoridad confirma que el entorno servido reconoce el nuevo secreto/keyId.
- `SIII_ACADEMIC_AUTHORITY_TARGETS_JSON` de Render vincula el tenant aprobado con `https://siii-three.vercel.app`, el UUID fuente aprobado y el nuevo keyId. El origen fue comprobado contra la configuración protegida y los dominios verificados del proyecto antes de escribirlo. Se creó por merge después de que el operador confirmara que no existía.
- Ambos bootstrap quedaron desactivados. El operador retiró ID/nombre institucionales temporales y las cuatro variables temporales HMAC; se verificaron los arranques posteriores y la persistencia de la credencial.
- La consulta final de metadatos Vercel confirmó ausencia de NEXT, PROBE_SOURCE y PROBE_MODE. No se retiraron el destino activo ni los flags.
- Los TXT privados de pegado se eliminaron al terminar la validación, incluida la copia recreada para restaurar la variable activa. Se conservaron únicamente la custodia DPAPI y sus metadatos privados. No hay procesos de copia pendientes.

## Límites y pendientes para producción

1. Entorno conciliado: el operador cambió APP_ENV y se verificó el arranque live con `environment=production`, salud/disponibilidad 200 y HMAC válido200/inválido401. Este bloqueo del cierre inicial queda resuelto. No se habilitó sincronización durante el cambio.
2. El primer arranque institucional manual creó también el ID incompleto `0690309f-8897-4c45-bf7f-d46c9c3a8ae`, con registro público cerrado. No se utiliza como destino y se conservó sin cambios. Su eliminación requiere revisión de referencias y autorización separadas.
3. Aún faltan datos académicos reales autorizados y la validación de entrega/proyección y acceso móvil para acreditar sincronización de extremo a extremo. No se generaron datos de relleno.
4. La sincronización sigue OFF. Esta rotación no autoriza cron, productor ni entrega continua.

Evidencia ampliada: `SIII_HMAC_401_HANDOFF.md`. Custodia y procedimiento: `../operations/SIII_EXISTING_HMAC_BOOTSTRAP_PREPARATION.md`.
