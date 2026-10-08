# Informe Android QA V8

## Entorno y trazabilidad

Repositorio `C:\proyectos\esc`, remoto `ErickRFM/Compa-ero-de-esc`. Se consultaron main remoto y PR recientes; base `9445d2487c0b0b682b98420ba4266df134d342ff` (#82), sin PR abierto duplicado al comenzar. Checkout limpio inicial; no se descartaron cambios ajenos. Rama `codex/v8-emulator-audit-20261007`. Código evaluado: **`05a14de6a71c68cc3358bb30b99b12a51a68eee9`** (incluye `f38cbcd`, `06f9eb8`, `bae2461` y main `75b436d`). El commit final de evidencia/herramientas no cambia el código compilado de la app; APK final identificada por commit/SHA en manifiesto junto al archivo entregado.

Android Studio SDK/JBR instalados. JDK21 ejecuta Gradle8.14.3; toolchains/targets existentes conservados. AVD disponible: `manecomb_ptt_api30`, dispositivo `emulator-5554`, Android11/API30, x86_64. App `org.companerodeescuela/.MainActivity`. Original: 1080×1920,420dpi,font1.0. Auditoría principal:1080×2400,443dpi≈390dp;360dp con480dpi;430dp con402dpi; tablet1600×2560,320dpi≈800dp; fuente del sistema2.0. Navegación del AVD de tres botones; no se presume validación física de gestos. Zona QA `America/Mexico_City`; tzdata del AVD puede diferir del host. Se restauran overrides/escala/zona previos al cerrar.

API exclusivamente local `127.0.0.1:8080`, app usa `http://10.0.2.2:8080/`. `APP_ENV=local`, proveedores de desarrollo existentes, almacén en memoria, JWT local. Usuarios existentes `ana.lopez`, `elena.rios` y cuenta vacía `qa.alumno`. Inicio de sesión a través del formulario. Sin credenciales de producción, sin sesión inyectada. Fixtures y documentos llevan etiqueta QA y no se consideran datos escolares reales.

## Automatización

Comando estable aprobado:

```powershell
$env:COMPANERO_API_BASE_URL='http://10.0.2.2:8080/'
.\gradlew.bat :apps:android:app:compileDebugKotlin :apps:android:app:assembleDebug testDebugUnitTest :services:api:test :shared:contracts:test :shared:models:test :shared:validation:test lint --continue --console=plain
```

Resultado: **BUILD SUCCESSFUL**, 301 tests JVM,0 fallos/errores/skipped. Agregado por XML en `v8-evidence/logs/unit-results.json`. Log final reconciliado guardado en `v8-evidence/logs/v8-reconciled-final.txt`; log de290 tests de la base anterior conservado como historial. Lint sin errores; advertencias existentes de vectors y APIs deprecadas no se ocultan con baseline nuevo.

Instrumentación configurada: `:apps:android:app:connectedDebugAndroidTest`; 2 tests de `LoginV8SmokeTest` (Login real y apertura de registro), aprobados sobre `05a14de` en ejecución secuencial de45s; log `v8-evidence/logs/v8-reconciled-instrumentation.txt`, XML `instrumentation-results.xml` (2 tests,0 fallos/errores/skipped). El smoke antiguo V5 falló contra V8; se actualizó a los textos/ruta reales y se añadió espera determinista, sin eliminar pruebas para obtener verde.

Intentos fallidos documentados: (1) pruebas rojas de navegación/estado/grid reprodujeron el defecto antes del cambio; (2) primer smoke V5 obsoleto; (3) dos procesos Gradle compartieron `classes.jar` y bloquearon instrumentación antes de ejecutar; (4) lint durante edición produjo análisis inconsistente de una función retirada; (5) prueba nueva “Ingles” reprodujo el recorte usando clase compilada anterior. Reejecución secuencial con fuentes estables aprobada. No se cuentan esos intentos como PASS.

## Recorrido funcional y visual

| Pantalla | Renderiza / navegación | Función comprobada | Similar a referencia | Pendientes |
|---|---|---|---|---|
| Login | PASS | Cuenta QA, password, teclado, sesión; registro abre por instrumentación | PARCIAL | Asset/decoración/glow; recuperación endpoint ausente; alta externa NO VALIDADO |
| Inicio | PASS | Perfil/nombre real, agenda local+institucional, importación se refleja al volver | PARCIAL | Avatar/notices/porcentaje/pendientes sin fuente; composición difiere |
| Horario | PASS | Semana/día, scroll, filtros, geometría, PDF e imagen con revisión/guardado | PARCIAL | Periodo no integrado; arrastre/CRUD completos y formatos de documentos según evidencia específica |
| Canal | PASS | Avisos lectura; Chat respuestas permitidas; Entendido aceptado; servidor403 al publicar como alumno | PARCIAL | Recursos externos y comunicación institucional real NO VALIDADO; no inventar reacciones |
| Pase de lista | PASS | Panel, QR/cámara/galería/permiso, estados honestos y política de expiración | PARCIAL | Wi-Fi escolar, QR institucional, sesión/inscripción/tiempo/veredicto real NO VALIDADO |

Baseline antes/después y método de comparación: [auditoría visual](V8-UI-EMULATOR-AUDIT.md). Matriz de cada componente: [integración](V8-IMPLEMENTATION-MATRIX.md). No hay porcentaje de fidelidad ni una pantalla etiquetada completa.

PDF e imagen de QA: seis días, lunes15min, viernes3h, sábado23:30; render inspeccionado y texto extraído antes de subir por ADB. El flujo real encontró un fallo del reconstructor y otro del parser, ambos corregidos con pruebas. La revisión contiene datos OCR editables antes de confirmar; el aula en la misma línea permanece parte del nombre reconocido y no se presenta como aula estructurada. El backend académico y Room se fusionan, conservando institucional. La clase QA del jueves apareció en Inicio con el conteo actualizado.

Red escolar/signing no configurados: suites del servicio prueban validación/autorización/temporalidad y outbox, pero no sustituyen una red autorizada ni un QR físico. Sesión creada por docente vía JWT local es fixture, no asistencia confirmada. Nunca se marcaron ubicación/red/asistencia verificadas sin evidencia. `serverTimeEpochSeconds` es aditivo; frente a servidor viejo que no lo envía, el badge queda pendiente.

Crashes: buffer `logcat -b crash` revisado durante recorrido; archivo `v8-evidence/logs/crashes.txt` contiene una excepción de `com.android.commands.uiautomator.DumpCommand`: dos dumps simultáneos intentaron registrar UiAutomation. Corresponde al harness, no al proceso de la app; se corrigió serializando dumps y usando XML único. No se observaron stacks de crash de `org.companerodeescuela`; no se afirma buffer vacío ni ausencia de crashes en otras configuraciones.

## Cierre

301 tests JVM y 2 smoke Android no prueban fidelidad del contrato. Se entrega rama, PR borrador, documentos, imágenes y APK identificable para revisión. **Sin merge automático mientras fidelidad/infraestructura y comprobaciones marcadas NO VALIDADO permanezcan abiertas.** El APK es debug para API local de QA, no una distribución de producción.

## Evidencia adicional y alcance

- `after/login/auth-error`: credenciales erróneas rechazadas; `recovery-unavailable`: limitación explícita. `responsive/<config>/login[-scroll]`: formulario y acciones accesibles por scroll.
- `after/home/qa-empty-account`, `after/schedule/qa-empty-account`, `after/channel/qa-empty-account`: cuenta QA sin horario/inscripciones, con sus estados vacíos reales.
- `after/schedule/image-import-review`: seis clases, “QA Ingles” completo tras corrección; confirmación en UI y conteo2 en Inicio. `day-classes-390`: conflicto jueves en dos carriles con duraciones proporcionales.
- `after/attendance/active-qa-session`: sesión creada por docente de desarrollo para el día actual; el horario académico era09:00–10:30 y la apertura fue posterior. Solo prueba la presentación del objeto activo; no asistencia temporal válida.
- `responsive/360`, `390`, `430`, `tablet-800`, `390-font2`: captura de cuatro destinos además de Login; canal/asistencia incluyen scroll. las cinco configuraciones se repiten con APK y API del código reconciliado `05a14de`. Las pruebas de Login/error/estado vacío y OCR previas se identifican por su etapa; el merge conservó esas implementaciones.

**NO VALIDADO en runtime**: TalkBack hablado/orden de foco, gestos de sistema físicos, todas las preferencias y roles, carga intermitente completa, red desconectada/reintentos/outbox, edición/borrado/deshacer y arrastre de extremo a extremo, documentos institucionales variados, alta externa, recursos remotos, Wi-Fi y QR reales. Tests de reglas y geometría no sustituyen estos recorridos. Estos pendientes son explícitos; no son PASS globales.

Main inicial era `9445d24`; al cierre se actualizó a `75b436d`. Las diferencias de merge se resolvieron por función, conservando servicios de V9 y tutoría. Primera compilación de reconciliación FAIL por un conflicto de Canal; `v8-reconciled-first-failed.txt` preserva el fallo. Corrección revisada por segundo lector; nueva compilación/tests/lint **BUILD SUCCESSFUL en1m30s**,301 tests sin fallos. El revisor no ejecutó pruebas y esa revisión estática no se confunde con evidencia runtime.

En fuente200% el acceso “Canal de clase” de Clases exige desplazar su contenido. Un intento del harness buscó únicamente el primer viewport y falló al localizarlo; se corrigió el recorrido para desplazar hasta el control y repetir. Ese intento no se cuenta como fallo de navegación ni como PASS: el recorrido posterior y la captura muestran el acceso real.

Las cinco comparativas fueron inspeccionadas visualmente (referencia/Android/overlay/diff). Resultado **PARCIAL en todas**: el fondo no coincide; el Login sitúa formulario más abajo y carece de decoración; Inicio no tiene fuentes de avisos/porcentajes; la semana exige scroll y los bloques cortos no contienen todo el texto; Canal cambia densidad; Pase carece de cohortes/progreso y su panel es más alto. No se obtuvo ni se afirma equivalencia visual.

APK final y manifiesto local: `dist/v8-audit/V8-audit-debug.apk`, `dist/v8-audit/manifest.json`. El manifiesto se genera después del commit final para evitar referencia circular, con SHA-256, commit de entrega, commit de código, base de main, variante/baseURL, verificaciones e instalación. No se empaquetan las referencias o fixtures en la app.

CI inicial del PR#100 falló antes de compilar por whitespace de logs y PDF ReportLab detectado como texto. Se preserva el PDF sin editar sus bytes, declarando `*.pdf binary` en atributos Git, como el resto de recursos binarios; se retiran espacios finales de logs. El check del rango completo del PR se repite, sin desactivar checks ni borrar fallos. Las compilaciones y pruebas locales siguen válidas porque no cambia el código compilado.

## Actualización: vidrio global y Render (2026-10-08)

La entrega de la ampliación visual usa `https://compa-ero-de-esc.onrender.com/` mediante `COMPANERO_API_BASE_URL`; reemplaza la configuración local de las APK anteriores solo para este binario de entrega. Cuenta de desarrollo autorizada y login por formulario. Alcance, comprobaciones y evidencia nuevos en [V8-GLOBAL-GLASS-QA.md](V8-GLOBAL-GLASS-QA.md). Los resultados de las etapas anteriores conservan sus propios commits y configuración; no se presentan como verificaciones del nuevo binario.
