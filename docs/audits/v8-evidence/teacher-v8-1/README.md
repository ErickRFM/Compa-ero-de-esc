# Evidencia docente V8.1

30 capturas originales de la app real en AVD API 30, autenticada por UI contra localhost QA. Fuente de app: `6e25c7d50c828738ebae3e6478fc2b09205620c1`. Los PNG/XML originales se conservan **localmente**, fuera de Git; el PR publica únicamente metadatos técnicos, inventario y resultados. La revisión automática de aprobación rechazó publicar el paquete de capturas por posible exposición de nombres/identificadores; no se intentó eludir esa restricción.

Carpetas `360`, `390`, `430`, `768`, `1024` y `360-font130`: Inicio (`home`), Asistencia (`attendance`), Mis clases (`classes`), Evaluación (`grading`), Canal (`channel`) con PNG/XML/JSON de tamaño, densidad, fuente, fecha y SHA. `matrix-results.json` registra los seis recorridos correctos, incluida vuelta Canal → Clases. Los dumps no contienen contraseñas/tokens de acceso. Los QR son efímeros y caducaron.

`contact-sheet-390.png` reduce y agrupa las cinco capturas para comparar; originales sin modificar. `flows/attendance-reviewed` muestra la revisión administrativa real del registro de desarrollo con evidencia original y autor/motivo.

Cuenta y horario del proveedor de desarrollo existente; las dos asignaciones se crearon por API de Control Escolar en localhost. No se sembraron estos datos en la app ni producción. Cifras, vacíos y errores corresponden a respuestas reales de esos servicios.

Diferencias con referencias: campus/logo existentes y sin retrato inventado; cinco tabs del contrato actual; Evaluación secundaria con Volver; scroll natural; estados vacíos/indisponibles en vez de datos del ejemplo. Entrada escolar necesita su servicio operativo; sincronización institucional no está disponible.

Ver [auditoría](../../TEACHER_V8_1_IMPLEMENTATION_AUDIT.md). `BUILD_PROVENANCE.json` separa APK local, debug HTTPS instalable y release sin firma. APK guardados en `build/outputs/teacher-v8-1`, no en Git.
