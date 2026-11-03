# UI y perfiles — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Hacer coherentes la presentación y el contexto de rol sin alterar autoridad del servidor.

**Architecture:** Reutilizar V8 y extraer un encabezado tipográfico compartido; transportar la experiencia activa a Perfil; conservar el resolver de roles como fuente de autorización visual.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle, Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-11-02-ui-perfiles-design.md`.

## Global Constraints

Sin dependencias, permisos ni contratos nuevos. Mantener distribuciones Academic/Management y preferencias de accesibilidad. No silenciar aserciones ni omisiones de CI.

## Review Focus

- Cuenta con varios roles: mostrar modo activo y sus acciones.
- Rol retirado: no ofrecer herramientas del rol anterior.
- Pantalla compacta/texto 200%: todas las opciones alcanzables.
- Títulos largos: conservar el acceso al perfil sin comprimir acciones.
- Datos vacíos/error: no presentar conteos como confirmaciones del servidor.

### Task 1: Contexto de perfil

Files: `MainActivity.kt`, `feature/profile/.../ProfileScreen.kt`, `ManagementProfileNavigationTest.kt`.

- [x] Registrar RED de solicitud docente en modo alumno y grupo administrativo vacío en modo coordinación.
- [x] Pasar experiencia activa y comprobar roles/distribución dentro de Perfil.
- [x] Identificar modo activo; mejorar jerarquía/filas; añadir comprobaciones de callbacks y rol retirado.
- [x] GREEN de regresiones y navegación existente.

### Task 2: Selector compacto y sistema visual

Files: `apps/android/app/src/main/kotlin/org/companerodeescuela/ExperiencePickerScreen.kt`, `apps/android/core/designsystem/src/main/kotlin/org/companerodeescuela/core/designsystem/v8/V8PageTitle.kt`, pantallas de Perfil, Tutoría, Coordinación, Administración, Clases, Canal, Evaluación y Ajustes.

- [x] Registrar RED del selector compacto sin scroll.
- [x] Permitir scroll, ancho limitado y márgenes de sistema en selector.
- [x] Reutilizar encabezado; quitar doble relleno en tarjetas afectadas; simplificar copy técnica.
- [x] Capturas y regresiones GREEN con texto ampliado/claro/oscuro.

### Task 3: Entrega

- [x] Informe con matriz de hallazgos y evidencia precisa.
- [x] Pruebas unitarias, instrumentación, lint/ktlint y ambas APK.
- [x] Revisión independiente del diff; resolver hueco de cobertura señalado.
- [x] Preparar entrega y descripción de PR con evidencia.

**Gate de integración:** comprobar CI completo del último SHA y sincronización Git autorizada; el estado verificable y SHA de integración quedan en el PR de esta rama.

## Ledger

- Inicio limpio en `main`; rama `codex/ui-perfiles-consistencia-20261102`.
- Base anterior ya validada: no se hereda su CI a este lote.
- Se ejecuta el pulido solicitado sobre la identidad existente; decisiones visuales reversibles y documentadas.
- El usuario confirmó conservar el borgoña y unificar la interfaz.
- RED localizado: tres fallos esperados. El primer intento de captura con scaffold navegaba antes de crear el grafo; se corrigió la preparación con navegación desde `runOnIdle` después del primer idle, conservando las aserciones.
- El formateador global intentó normalizar archivos ajenos al lote; se restauraron solo esos cambios mecánicos propios y se formatearon los Kotlin modificados con la CLI 1.5.0 del proyecto. No cambió el baseline ni las reglas globales.
- Cierre local: 1118 aprobados, cero fallos, tres contratos SIII opt-in omitidos. Veintiocho UI pasan en API 30; cuatro se repiten en 430 dp con topes reales 360/390/430 dp. Capturas finales inspeccionadas y pantalla restaurada. La revisión final no encontró hallazgos pendientes.
