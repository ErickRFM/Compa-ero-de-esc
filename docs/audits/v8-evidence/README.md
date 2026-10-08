# Evidencia V8

Capturas originales ADB con jerarquía UI XML; referencias del usuario solo para auditoría, nunca empaquetadas como pantalla. API local y cuentas existentes de desarrollo; cualquier contenido QA es una fixture explícita.

- `before`: baseline ejecutado en9445d24; canal/QR no accesibles en navegación de alumno.
- `intermediate`: iteraciones anteriores y errores reproducidos; no son evidencia final.
- `after`: estados/acciones reales identificados en el informe. Las cinco capturas usadas en comparación se repiten con código reconciliado05a14de, excepto estados extra que conservan su etapa indicada.
- `responsive`: cinco configuraciones; destinos, canal/pase desplazados y Login.
- `references`: mockups originales aportados por el usuario.
- `comparisons`: recorte documentado, normalización, overlay y diferencia; sin porcentaje de fidelidad.
- `fixtures`: PDF/imagen con seis días y duraciones de15min a3h, sábado23:30. Solo pruebas de OCR/agenda personal.
- `logs`: resultados, errores de intentos y datos QA sanitizados. Sin JWT ni contraseñas de producción.

Reproducción: AndroidSDK en ubicación estándar Windows, AVD manecomb_ptt_api30, Python con Pillow. `v8-emulator-qa.py` obtiene siempre una jerarquía nueva; `v8-responsive-qa.py` exige sesión QA iniciada. `v8-local-fixtures.py` crea avisos de prueba en cada ejecución: ejecutar una vez sobre una API local fresca; no es un seed de producción. `v8-compare-screens.py` requiere originales en Temp con los nombres adjuntados.

Ver [informe](../V8-ANDROID-QA-REPORT.md) y [auditoría](../V8-UI-EMULATOR-AUDIT.md). APK y manifiesto se entregan en dist/v8-audit, no en Git.
