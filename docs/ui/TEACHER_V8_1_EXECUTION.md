# Docente V8.1 — ejecución 2026-10-08

Especificación: `TEACHER_V8_1_VISUAL_MASTER_PLAN.md` y cinco referencias adjuntas del usuario. Ejecución en esta sesión, sin generar maquetas nuevas.

## Base y preservación

- Main reconciliado: `d91b57b`, con #94/#95/#97/#99/#96/#102; #100 y cabeza `c3f46b3` de #103 integrados en la rama.
- Checkout inicial: `dfc1db323ed9a2159d03970719c24cfb4f0100a0`, PR #100.
- Cambios locales conservados en stash `teacher-v8-1-preserve-preexisting` y copia adicional en `build/teacher-v8-1-baseline`.
- Rama: `codex/teacher-v8-1-integration-20261008`.

## Trabajo y comprobaciones

- [x] Reconciliar #94, #95 y navegación #103; conservar cambios locales.
- [x] Conservar clase/grupo en los tres destinos mediante argumentos tipados y autorización.
- [x] Probar aislamiento, limpieza, porcentajes y archivos inválidos.
- [x] Afinar Inicio: pase prioritario, siguiente clase vigente, métricas y estados reales.
- [x] Afinar Asistencia: QR efímero, filtros y filas lazy con revisión trazable.
- [x] Afinar Canal: compositor plegable, respuestas predeterminadas y aislamiento.
- [x] Verificar 126 Android + 166 API + 23 shared + 2 instrumentación; lint sin errores; debug/release R8.
- [x] Capturar 30 vistas reales en seis perfiles de tamaño/fuente.
- [x] Verificar ajuste administrativo por UI y hash del APK instalado.
- [ ] Confirmar importación completa y envío mediante UI en teléfono físico; DocumentsUI no completó selección con ADB.
- [ ] Resolver Mongo del backend público y validar red escolar real.
- [ ] Obtener aceptación final física. Los checks remotos y la fusión se registran en el PR de entrega.

## Revisión prioritaria

Cuenta multirrol, asignación retirada durante edición, respuesta tardía de otro grupo, archivo fallido que conserva una importación anterior, clase finalizada/QR caducado y matrículas inválidas deben producir estados explícitos. La integración institucional ausente es un bloqueo real: nunca declarar publicación de notas.

Resultados, diferencias visuales y trazabilidad: [auditoría](../audits/TEACHER_V8_1_IMPLEMENTATION_AUDIT.md). Fuente de código/APK: `6e25c7d50c828738ebae3e6478fc2b09205620c1`. No se declara producción lista: `/health` público informa Mongo `down`.
