# V19 — Auditoría de sincronización académica y preflight

Fecha de revisión: 2026-10-24. Alcance: Compañero Android/Ktor y SIII PR #109. No se inspeccionaron capturas de autorización: no venían adjuntas; los estados se verificaron en código y contratos HTTP.

## Evidencia confirmada

- Compañero main está en merge V18 ab556aba9c911adb40c26a2d4914e971dd8c5bf8.
- Backend/Android compile-test-lint-package y CI de instrumentación Academic/Management en API 31/35 están verdes en ese SHA. El workflow de distribución firmada falla en el paso de keystore/certificado si sus secretos de distribución no están presentes; no se usa ni se solicita un secreto.
- SIII PR #109 sigue abierto en 6b41413c916e48d03954bb95db964500460d3919. Verify, UI y el contrato sintético reportan verde; la revisión GitHub está vacía. El contrato comprueba datos sintéticos, no una instalación productiva.
- El workflow del PR #109 fija el checkout receptor de Compañero en 586ec206576349c9a0d96ce85c50bbb3cf0fb0b5, anterior a V18. Sus ejecuciones verdes no validan el receptor fusionado V18. No se debe usar como prueba de compatibilidad hasta actualizar el pin y volver a ejecutar los checks.
- El PR #109 no agrega migraciones. 0018_academic_section_outbox.sql existe en SIII main con políticas/grants cerrados; la auditoría de solo lectura referenciada por el PR reporta que no estaba aplicada en producción. La migración productiva y la configuración real de Vercel no se verificaron desde este entorno.
- La entrega automática del PR depende de SIII_COMPANION_AUTOMATION_ENABLED; la variable de repositorio observada está unset. Las banderas de outbox/entrega y el keyring se documentan como opt-in. Se mantiene todo apagado; los valores de secretos no se consultaron.
- Ktor persiste tokens FCM en Mongo y crea el push outbox durable; el worker solo arranca con configuración autenticada, Mongo y service account FCM. El endpoint devices/register vincula el token a la sesión nativa. El cliente no solicita token mientras las notificaciones estén desactivadas/denegadas.
- La pantalla Android del alumno usa caché para fallos no-401 en AcademicRepository; el horario también puede leer caché tras cualquier fallo. V19 cambia esto para que HTTP 4xx, en especial 403/404, sigan visibles y no se conviertan en éxito local.
- El snapshot SIII se proyecta en siii_institutional_section_snapshots. SiiiSectionReader valida actor, sesión, institución, rol, enlace de identidad, inscripción y estado de alumno, pero recibe institución/sección/ID externo como argumentos. La búsqueda permite secciones individuales; no hay una ruta HTTP de lectura que resuelva el alumno nativo a sus secciones inscritas.
- /academic/load para sesión nativa continúa usando NativeAcademicProjection sobre canales/grupos de Compañero; no está conectado al snapshot SIII. El test SiiiFullChainLiveContractTest prueba la cadena contra Mongo desechable y llama al reader, pero no prueba una ruta Android/Ktor de lectura académica. Las calificaciones tampoco están incluidas en el snapshot/outbox actual.
- Por ello, Clases del alumno todavía consume aulas colaborativas de Compañero y las calificaciones vienen de ese mismo dominio. No se debe afirmar que reflejen matrículas, catálogo o evaluaciones publicadas por SIII hasta resolver y probar el contrato.

## Decisión de preflight

**NO-GO para activar sincronización o entrega SIII productiva.** Faltan, como mínimo:

1. Revisión del PR #109 y CI del contrato fijado al receptor V18.
2. Evidencia de aplicación de migración 0018 mediante el procedimiento operativo aprobado; no ejecutar una migración real desde esta tarea.
3. Resolver en Ktor la identidad institucional nativa de forma única y fail-closed, enumerar únicamente inscripciones vigentes del alumno en su institución y entregar grupos/materias/docentes/horario con versión/origen SIII.
4. Lectura autorizada de evaluaciones publicadas, con pruebas de permisos por rol/institución y respuestas 401/403/404.
5. Preflight aislado con credenciales ya provisionadas en el gestor de secretos, destino de prueba y payload sintético; validar firma, deduplicación, outbox, entrega, revocación y lectura. No copiar credenciales a Android ni a CI.
6. Mantener explícito el interruptor productivo en OFF hasta que esos pasos pasen y exista autorización operativa documentada.

## Pruebas pendientes para declarar capacidad

- 401, 403, 404, inscripción inactiva, institución equivocada y snapshot ausente.
- Enrolamiento vigente y cambios de sección entregados por outbox; horario oficial contra importación personal con ambos conservados y discrepancia visible.
- Evaluaciones sin publicación (no deben aparecer) y publicación autorizada.
- Notificación FCM sintética, toque directo al pase, persistencia de asistencia confirmada por Ktor/Mongo y casos negativos.
- Teléfonos reales Android API 31 y API 35. CI/emulador y tests sintéticos no certifican recepción FCM ni presencia física.