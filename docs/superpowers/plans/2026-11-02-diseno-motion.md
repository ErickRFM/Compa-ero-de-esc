# Diseño y animaciones — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Mejorar la presencia visual y el movimiento sin perder roles, accesibilidad ni control de navegación.
**Architecture:** Reutilizar la política de motion; diseño compartido en V8; lógica de permisos permanece en features/servidor.
**Tech Stack:** Compose / Material 3 / Android.
**Spec:** `docs/superpowers/specs/2026-11-02-diseno-motion-design.md`.

## Global constraints

Conservar borgoña, APIs y roles; sin dependencias externas, bucles decorativos ni demoras de interacción. No ampliar timeouts ni omitir tests.

## Review focus

Movimiento reducido, cancelación de gesto, scroll a texto 200%, perfil mixto/revocado y transiciones sin retener el rol anterior.

## Tasks

- [x] RED de movimiento reducido y pulsación sobre controles existentes.
- [x] Motion compartido; navegación y selección sin movimiento al reducirlo.
- [x] Identidad, filas de Perfil y accesos de Inicio con más jerarquía visual.
- [x] GREEN, capturas, ambas APK y lint/ktlint.
- [x] Revisión independiente y preparación de entrega Git.

Seguimiento remoto: el PR de la rama registra el cierre de CI del SHA final y su integración; este plan acompaña la evidencia local anterior a emitir el commit. Resultados y límites en `docs/audits/DISENO_MOTION_20261102.md`.
