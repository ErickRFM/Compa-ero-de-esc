# Registro de ejecución iOS

Plan: docs/superpowers/plans/2026-10-08-ios-kmp-foundation.md
Spec y plan aprobados por el usuario. Ejecución en esta sesión con revisión independiente final.
Base: 97e68e1523a3b9a20e8cd9aa90f4e5fc4ff72eca (origin/main).
Rama: codex/ios-kmp-foundation.

Pre-flight: Tarea 1 produce CompaneroShared y contratos para Tareas 2/3; Tarea 2 produce puente para Tarea 3; Tarea 3 produce esquema para Tarea 4. Sin conflictos.
Baseline aislado: en ejecución; ver ios-baseline.log local (ignorado).

Ruling: ledger versionado en docs/ios/EXECUTION.md en vez de scratch externo — conserva evidencia con el PR y evita un helper Bash que no es necesario en Windows — no contiene secretos.

Tareas 1/2: pruebas de cliente/configuración fallaron sin clases nuevas (ios-kmp-red.log); 13 pruebas JVM pasan después de implementación y también tras añadir @Throws al constructor del puente.
Tareas 3/4: proyecto Xcode y pipeline creados; Native/Xcode se verificarán en macOS.
Ruling: añadir ci-ios GitHub con el mismo script de Codemagic — permite validar en macOS desde el PR sin sustituir ni declarar un build Codemagic — si imagen Xcode falta, el job falla explícitamente.
Ruling: Swift/UI y proyecto Xcode se verifican con XCTest y compilación real macOS; no existe compilador Swift iOS en Windows. No se presentan tests estáticos como compilación.
Revisión independiente: un hallazgo P2, propiedad del motor Ktor. Reproducción: closingClientClosesItsEngine falla porque HttpClient(engine) no administra su engine. Fix: cliente retiene y cierra explícitamente el engine. Prueba adicional XCTest: URL inválida cruza el constructor @Throws y produce error controlado en Swift.
Codemagic confirmado conectado por usuario: https://codemagic.io/app/6ac8735db82b0d75c91e6626/settings. Alcance final: preparar rama/PR y dar pasos para la primera compilación allí, no declarar ejecución Codemagic aprobada.
Baseline aislado final: BUILD SUCCESSFUL, 1735 tareas; tests Android/API/compartidos y lint sin errores; APK Debug y Release generados. GitHub Android (incluyendo instrumentación) y API PASS en a8e0991.
Primer macOS: framework + Swift app BUILD SUCCEEDED, 16 tests JVM + 16 Native PASS. Error de script al poner lipo -verify_arch antes del archivo. Corregido según el uso de lipo mostrado por el runner: lipo <archivo> -verify_arch <arquitectura>. Revalidar pipeline completa.
