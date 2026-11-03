# Personalización y UX — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Hacer la UI más limpia y adaptable sin perder personalización, roles ni estados reales.
**Architecture:** Pares de color y carga en designsystem, controles adaptables en Ajustes, presentaciones compactas en Perfil/Inicio y arranque guiado por sesión.
**Tech Stack:** Kotlin, Compose, Material 3.
**Spec:** `docs/superpowers/specs/2026-11-02-personalizacion-ux-design.md`.

## Global constraints

Conservar seis acentos/preferencias y autoridad del servidor. Sin dependencias nuevas, timers falsos, cambios de baseline ni timeouts aumentados. No eliminar mensajes de seguridad, estado offline ni acciones de recuperación.

## Review focus

Color dinámico oscuro, tinta sobre acentos pastel, 320 dp/240%, semántica de switches, carga inicial frente a refresco y cambio de rol.

- [x] RED: parejas dinámicas/contraste y controles de personalización/carga.
- [x] Corregir pares de color compartidos, adaptar Ajustes y compactar Perfil/Inicio.
- [x] Carga reutilizable e inicio sin falso estado vacío; movimiento reducido.
- [x] GREEN, matriz visual de personalización, ambas APK y análisis estático.
- [x] Revisión independiente y preparación de consolidación.

El PR de la rama registra el estado remoto del SHA final y su integración. La evidencia local y sus límites están en `docs/audits/PERSONALIZACION_UX_20261102.md`.
