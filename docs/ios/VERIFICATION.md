# Evidencia de la base iOS

Base de la rama: `97e68e1523a3b9a20e8cd9aa90f4e5fc4ff72eca`.
Rama: `codex/ios-kmp-foundation`. Sin merge autorizado.

## Estado local

- KMP JVM: 16 pruebas, cero fallos. Las pruebas de cliente/configuración fallaron inicialmente por ausencia de implementación y pasaron después.
- Framework Native y app Swift compilados en macOS/Xcode 26.0.1 en el primer run cloud. Pasaron 16 tests JVM y 16 tests Native. El script falló después en el orden de argumentos de lipo; corregido. XCTest/instalación/arranque están pendientes del siguiente run.
- YAML Codemagic: PASS contra el esquema JSON oficial descargado de codemagic.io/codemagic-schema.json; también PASS en plists, referencias PBX, fuentes/esquema y sintaxis Bash.
- Android/API aislado: compile Debug y APK Debug generados; 142 pruebas Android, 168 API, 16 contratos, 3 modelos y 7 validación, cero fallos. Lint Debug: cero errores. APK Release también generado, más pequeño que Debug (75,654,694 frente a 102,729,648 bytes); BUILD SUCCESSFUL en 22m30s, 1735 tareas. Los cambios iOS no modifican módulos ni grafo raíz.
- Revisión independiente: un hallazgo P2 corregido (cierre del motor Ktor), con prueba observada fallar antes y pasar después. Revisión posterior sin otros problemas concretos; integración macOS pendiente.

Los reportes previos del checkout original no se usan como evidencia final debido a cambios concurrentes de rama.

## Cloud

`ci-ios` en GitHub usa Xcode 26.0.x en macOS y ejecuta el mismo script que Codemagic: tests JVM/Native, build, XCTest y arranque de la app.
Registrar aquí resultados y URLs observados del SHA del PR. Hasta entonces, no afirmar compilación cloud aprobada.

Codemagic necesita el repositorio conectado a una cuenta y un workflow ejecutado. No hay tokens de Codemagic ni credenciales Apple incorporados en esta base. Validación de su YAML y build GitHub son evidencias distintas a una ejecución Codemagic.

## Gate de merge y producción

Mantener PR draft mientras falten checks/revisión/builds requeridos. Aun con simulador verde, la aplicación no está lista para producción: ver `REUSE_AND_GAPS.md`.

## Resultados GitHub observados en el primer commit de implementación

Commit a8e09910446570fd137468ff25049d920ceb9d0d:
- Android compile/test/lint/package e instrumentación: PASS, https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/37888540553
- Backend compile/tests/smoke: PASS, https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/37888540552
- Primer iOS: compilación app y framework PASS; tests JVM/Native PASS; script arquitectura FAIL corregido en commit siguiente, https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/37888540558

Codemagic conectado: https://codemagic.io/app/6ac8735db82b0d75c91e6626/settings. Primera ejecución pendiente por usuario, conforme al alcance confirmado.
