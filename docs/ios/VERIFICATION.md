# Evidencia de la base iOS

Base de la rama: `97e68e1523a3b9a20e8cd9aa90f4e5fc4ff72eca`.
Rama: `codex/ios-kmp-foundation`. PR draft: https://github.com/ErickRFM/Compa-ero-de-esc/pull/127. Sin merge autorizado.

## Validación local

- KMP JVM: 16 pruebas, cero fallos. Las pruebas de cliente/configuración fallaron inicialmente por ausencia de implementación y pasaron después.
- YAML Codemagic: PASS contra el esquema JSON oficial descargado de codemagic.io/codemagic-schema.json; también PASS en plists, referencias PBX, fuentes/esquema y sintaxis Bash.
- Android/API aislado: 142 pruebas Android, 168 API, 16 contratos, 3 modelos y 7 validación, cero fallos. Lint Debug: cero errores, 11 advertencias existentes. APK Debug y Release generados (102,729,648 y 75,654,694 bytes). BUILD SUCCESSFUL en 22m30s, 1735 tareas.
- No hay cambios en fuentes, módulos, versiones ni grafo de producción Android/backend.
- Revisión independiente: un hallazgo P2 corregido (cierre del motor Ktor), con prueba observada fallar antes y pasar después. Revisión posterior sin otros problemas concretos.

Los reportes del checkout original no se usan como evidencia final debido a cambios concurrentes de rama.

## Compilación real macOS

Commit de código verificado: `a9f9cc4876f61c09405d2624268099fd2e81ab13`.

- iOS PASS: https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/37889125889
- Android compile/test/lint/package e instrumentación PASS: https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/37889130389
- Backend compile/tests/smoke PASS: https://github.com/ErickRFM/Compa-ero-de-esc/actions/runs/37889130344

El run iOS ejecutó el mismo script que Codemagic en macOS 15.7.9, Xcode 26.0.1, host ARM64:
- 16 tests JVM y 16 tests iosSimulatorArm64, cero fallos/omisiones.
- Framework Kotlin/Native estático y SwiftUI app: BUILD SUCCEEDED.
- 3 XCTest, cero fallos/omisiones: TEST SUCCEEDED.
- Instalación y arranque de org.companerodeescuela.ios en iPhone 17 Pro, iOS Simulator 26.2.
- Captura de pantalla inspeccionada: pantalla Hoy V8 y navegación inicial visibles.
- ZIP .app de simulador ARM64, xcresult, reportes y procedencia publicados en el artefacto ios-simulator-evidence. Firma desactivada.

Esta comprobación prueba compilación, tests y arranque inicial. No prueba login contra una cuenta real, todos los flujos de navegación, iOS 16 en dispositivo ni las funcionalidades pendientes.

El primer run (a8e0991) compiló app/framework y pasó tests KMP, pero falló por el orden de argumentos de lipo. Se corrigió y el run anterior verificó el pipeline completo. Los cambios posteriores a a9f9cc4 de esta entrega son documentación de resultados; consultar los checks del SHA actual del PR antes de merge.

## Codemagic y gate de merge

Codemagic conectado: https://codemagic.io/app/6ac8735db82b0d75c91e6626/settings.
La primera ejecución allí debe iniciarse por el usuario seleccionando la rama y workflow, conforme al alcance confirmado; ver README.md. No se ha observado un build Codemagic ni se declara aprobado.

Validación de su YAML y ejecución GitHub son evidencias distintas a una ejecución Codemagic. No hay tokens ni credenciales Apple en el repositorio.

Mantener PR draft hasta aprobación de revisión y checks/builds requeridos del SHA actual. No hacer merge con verificaciones pendientes. Aun con simulador verde, la aplicación no está lista para producción: ver REUSE_AND_GAPS.md.
