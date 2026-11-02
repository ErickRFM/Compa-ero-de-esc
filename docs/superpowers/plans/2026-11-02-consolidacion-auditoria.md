# Consolidación y auditoría integral de Compañero

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidar el código disponible y determinar con evidencia qué falta antes de afinar la app con el emulador.

**Architecture:** Se conserva Android Compose/Hilt, Ktor/Mongo y los límites de autoridad SIII. La revisión Git, UI y lógica se realiza en frentes independientes de solo lectura; el agente principal implementa y verifica las correcciones confirmadas.

**Tech Stack:** Kotlin 2.2.21, Compose, AGP 8.13.2, JDK 21, Gradle wrapper, Ktor y MongoDB.

**Spec:** Solicitud del usuario del 2026-11-02; diseño vigente en `docs/design/V25_VISUAL_MASTER_PLAN.md` y matriz de aceptación adyacente.

## Global Constraints

- Preservar todas las ramas y trabajo existente; no fusionar referencias históricas indiscriminadamente.
- Distinguir código implementado, pruebas locales, CI, despliegue y prueba física.
- Mantener permisos, separación de APK Académica/Gestión y autoridad institucional SIII.
- No introducir un tema, navegación o motor de animación paralelo.
- No publicar datos ni credenciales de cuentas reales en evidencia.

## Review Focus

- Navegación desde notificación antes de cargar la clase y después de cambiar sesión/rol.
- Formularios y listas extensas con teclado, pantalla pequeña y texto al 200%.
- Importación de archivos corruptos, límites y cambio de clase durante una operación.
- Revisión concurrente de tareas/asistencia y persistencia después de recargar Mongo.
- Ramas fusionadas mediante squash que parecen pendientes por diferencias de ancestry.

### Task 1: Sincronizar y clasificar Git

- [x] Registrar árbol limpio, remotos, ramas y worktrees.
- [x] Fetch de todos los remotos y avance rápido de main a `cd721f1d` (PR #266).
- [x] Crear `codex/consolidacion-auditoria-20261102` desde la base actualizada.
- [x] Contrastar ramas divergentes con PR fusionados y contenido actual; preservar históricos.
- [x] Registrar decisiones y PR pendientes en el informe.

### Task 2: Verificar la base completa

- [x] Ejecutar `ktlintCheck`, pruebas API/shared/Android, lint y build de ambas APK con el wrapper.
- [x] Ejecutar integración API con Mongo local desechable ya disponible, sin usar datos productivos.
- [x] Revisar CI del SHA actualizado y protección de main.
- [x] Registrar fallos, skips, cantidades y límites de cada comprobación.

### Task 3: Corregir defectos confirmados

**Files:** `ChannelScreen.kt`, pruebas instrumentadas de la app y otros archivos solo cuando la auditoría demuestre un fallo concreto.

- [x] Reproducir las acciones de tareas inaccesibles en una ventana acotada con texto ampliado.
- [x] Añadir una regresión de comportamiento para alcance por scroll de composición y revisión de tareas.
- [x] Adaptar contenedores existentes para scroll/IME conservando diseño y contratos.
- [x] Ejecutar la regresión y los checks correspondientes a los archivos modificados.
- [x] Resolver otros fallos funcionales demostrados por revisión/pruebas con su regresión.

### Task 4: Entregar un diagnóstico actual y consolidado

**Files:** `docs/audits/CONSOLIDACION_UI_LOGICA_20261102.md`, README y documentación de estado cuando sea obsoleta.

- [x] Crear matriz por módulo/rol con ruta UI → repositorio → API → persistencia y evidencia.
- [x] Separar problemas corregidos, pendientes reales y aceptación futura en emulador/dispositivo.
- [x] Revisar diff final, pruebas, secretos y whitespace.
- [ ] Commit y push de la rama; PR con evidencia concreta. Integrar únicamente con checks requeridos satisfactorios.

## Execution ledger

- Base local original `db71e84b`; base actualizada `cd721f1dedb48138b485d73654b5389b7866ba9c`.
- No había cambios locales sin commit ni AGENTS.md en el árbol/ancestros consultados.
- GitHub connector exige `link_id` no disponible; se usa Git CLI/GitHub CLI autenticado.
- PR funcional #266 ya fusionado; diez PR abiertos son dependencias. Se mantienen separados de esta auditoría.
- Disponible Mongo local `codex-v25-mongo` en `127.0.0.1:27017` y emulador `emulator-5554`; no equivalen a producción ni hardware real.
- Se continúa con la ejecución ya solicitada por el usuario; no se requiere una segunda aprobación del plan.
