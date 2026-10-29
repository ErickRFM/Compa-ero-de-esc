# Activación SII → Compañero — 25 de octubre de 2026 (CDMX)

Estado: **productor y entrega habilitados en Production; programador habilitado y primera ejecución satisfactoria**. Esto acredita configuración, autenticación y ejecución del procesador. La entrega/proyección de un grupo real y el acceso móvil aún no están acreditados porque no existen datos académicos fuente.

## Autorización y alcance

El usuario pidió habilitar la sincronización después del cierre de la rotación y autorizó expresamente trasladar el CRON_SECRET vigente de Vercel al secreto CRON_SECRET de ErickRFM/Siii. La revisión automática bloqueó el traslado antes de esa autorización explícita; después se realizó directamente entre el acceso autenticado local a Vercel y stdin de `gh secret set`. No se imprimió, persistió en archivos ni cambió su valor. No se rotó la HMAC.

Se conservaron los cambios existentes, sin worktrees, APKs, matrices completas ni datos académicos ficticios. No se modificaron variables de Render durante esta activación.

## Despliegues y activación por etapas

| Servicio/etapa | Despliegue | Resultado |
| --- | --- | --- |
| Compañero | `dep-dbfe57ivcj2c73fq61n0` | Live, production; `/health` y `/ready` HTTP 200 |
| SII, productor true / entrega false | `dpl_GEgz5PnfSZ4Y7mg4uBxZN7YjQXuX` | READY; autoridad habilitada y firmas verificadas antes de continuar |
| SII, productor true / entrega true | `dpl_6zvmySnwT3q95ikCg1ZfUfPVfJPk` | READY; alias `siii-three.vercel.app` confirmado en este deployment |

Ambos despliegues SII reutilizan el SHA revisado `1699415748b71fad8f11a8754096e2ccc52d6b69`. El preflight oficial de configuración pasó en cada etapa. El segundo se ejecutó con `--active`. La guarda adicional comprobó destino único/UUID/keyId/origen exactos, rol de base restringido mediante APPLICATION_ROLE_QUERY/assertRoleInspection y presencia de outbox/versiones antes de compilar. Compilación y validación de tipos finalizaron correctamente.

## Resultados de red en el despliegue final

| Comprobación | Resultado |
| --- | --- |
| Compañero `/integration/v1/projection-status`, firma válida | HTTP 200 y contrato verificado |
| Misma ruta, firma inválida y nonce independiente | HTTP 401 |
| SII autoridad, firma válida sobre referencia inexistente | HTTP 404 / SOURCE_IDENTITY_UNAVAILABLE; autoridad activa, sin crear identidad |
| SII autoridad, firma inválida | HTTP 401 |
| Procesador autenticado de entrega | HTTP 200 / `status:processed` |
| Procesador sin autorización | HTTP 401 |

El procesador se comprobó a `2026-10-26T05:34:58Z`: claimed=0, accepted=0, retried=0, failed=0, budgetExhausted=false, cancelled=false. Este resultado comprueba ambos flags efectivos del entorno servido y acceso runtime a la base, sin confundir una respuesta disabled con procesamiento activo.

## Estado final

- `SIII_ACADEMIC_OUTBOX_ENABLED=true`, solo Production.
- `SIII_COMPANION_DELIVERY_ENABLED=true`, solo Production.
- GitHub `SIII_COMPANION_AUTOMATION_ENABLED=true`.
- Secreto GitHub CRON_SECRET provisionado con el valor vigente de Vercel; metadatos confirmados, sin mostrar valores.
- Workflow existente `.github/workflows/companion-delivery.yml`: programación `*/5 * * * *`, concurrencia única, endpoint protegido, sin redirects ni reintentos HTTP añadidos. La ejecución depende de la disponibilidad de GitHub Actions; la programación no garantiza puntualidad exacta.
- Primera ejecución manual del mismo workflow: [40275012722](https://github.com/ErickRFM/Siii/actions/runs/40275012722), SHA exacto, job delivery **success**, finalizado `2026-10-26T05:35:17Z`.
- El helper temporal SIII_ACTIVATION_PREFLIGHT_SOURCE quedó vacío, igual que antes de esta intervención. Se comprobó ausencia de variables NEXT/PROBE. Los comandos de preflight fueron overrides por despliegue, sin agregar código de diagnóstico al repositorio desplegado.
- Los bootstrap institucional y de credencial permanecen apagados, con sus valores temporales ya retirados durante la rotación anterior.

## Identidad, vigencia y límite de aceptación

MongoDB Atlas Cluster0 → companero_dev conserva la credencial `_id` `siii-hmac-20261025-138dedb2ba5840759868ff77844616a7`, enabled=true, scope group:write, tenant `0690309f-8897-4c45-bf7f-d46c9c3a8ae1`. Se consultaron exclusivamente estos metadatos, sin leer ciphertext ni secretos. La caducidad sigue siendo `1793591855`: **2026-11-02 03:57:35 UTC** (1 de noviembre, 21:57:35 CDMX). Debe realizarse una renovación coordinada autorizada antes de esa hora; no se extendió ni sustituyó esta credencial durante la activación.

Una consulta final de solo lectura en la base SII confirmó 0 academic_sections, 0 institutional_students, 0 institutional_teachers y 0 academic_section_outbox. Cuando existan datos reales autorizados, queda verificar recepción, proyección/versiones exactas, vínculos nativos, revocaciones y lecturas móviles. No se afirma una entrega real, proyección ni acceso móvil comprobados.

El registro institucional previo con UUID incompleto permanece sin tocar y no se usa como destino. Su limpieza requiere revisión y autorización separadas.
