# V10 Smart Role Login — auditoría y verificación

Base inicial: `0ac109118e87740872b6275dc8f6836047115a0a`. Rama `feat/v10-smart-role-login`, worktree aislado; checkout principal con trabajo ajeno preservado. Integrado main hasta `3d5b016d3b5e5910bcc938ad402ebed0a674e4f1`: privacidad Tutor, límites/cancelación PDF y revisión humana de presencia, más autoridad docente #134. El SHA exacto de entrega y hashes de APK pertenecen al PR y manifiesto de artefactos, evitando una referencia circular en este documento.

| Componente auditado | Acción real |
|---|---|
| LoginScreen/AuthV8Layout | Un formulario estable y carrusel con cinco intenciones; sin servicios duplicados |
| RoleExperienceResolver/Destination/experiencias | Reutilizados; intención validada contra autoridad del backend |
| Perfil/ActiveExperiencePreferences | Reutilizados; preferencia autorizada por usuario y cambio de experiencia |
| Ktor/JWT/Keystore/refresh/logout | Protocolos conservados; auth/me al restaurar, cancelación y concurrencia corregidas |
| Campus/crimson/panel mate/tokens V9 | Reutilizados; adaptación clara opt-in sólo del login |
| AppearancePreferences/CompaneroTheme/movimiento | Reutilizados; es/en, contraste, escala, reducción y transición existente de 220 ms |
| Autoridad servidor V10.1/limitación intentos | Endpoints y revocación existentes conservados, incluido 429/Retry-After |
| MFA/proveedor institucional productivo | No implementados por esta misión |

## Comportamiento implementado

Alumno, Docente, Tutor, Admin y Super Admin cambian mediante swipe, flechas e indicadores sobre un único formulario. La selección expresa intención y no concede roles. Coordinación conserva su experiencia autorizada independiente, aunque no sea una sexta tarjeta. `auth/me` publica identidad y roles vigentes, también al restaurar sesión. Una intención no autorizada abre el selector existente; la elección autorizada se guarda por cuenta y puede cambiarse desde Perfil.

Se impide doble envío, se propaga cancelación de persistencia/refresh y una respuesta tardía de login no sustituye la autoridad nueva de `auth/me`. Logout no permite reapertura por respuestas tardías. Usuario e intención se conservan durante recreación; la contraseña no se guarda en estado restaurable.

Login respeta es/en, tema claro/oscuro/automático, alto contraste, escala y movimiento reducido existentes. Registro conserva su superficie oscura V8 original. El idioma guardado se aplica después de `Activity.onCreate`, sin cambio de configuración desde el constructor de preferencias. Controles con semántica y objetivos de 48 dp; TalkBack humano no se ha ejecutado.

## Pruebas y evidencia

Wrapper local: `testDebugUnitTest`, `:services:api:test`, `:shared:contracts:test`, `:shared:models:test`, `:shared:validation:test`, `lint`, `:apps:android:app:assembleDebug`, `assembleRelease` y `assembleDebugAndroidTest`. Reportes: Android 191, API 195, contratos 16, modelos 3 y validación 7, sin fallos, errores ni omitidos; lint 0 errores y 55 avisos. Verificación combinada con #134: `v10-main134-full-verification.log`, BUILD SUCCESSFUL en 4m10s; 412 pruebas JVM. El log anterior `v10-final-main-verification.log` conserva la verificación previa. Debug entregado usa URL predeterminada; Release minificado sin firma conserva `https://invalid.invalid/` y no se publica.

Instrumentación propia API 30: 17 pruebas de login, carrusel, estado, idioma, capturas de Activity real, smoke, privacidad Tutor y verdad de asistencia. Log final: `v10-instrumentation-final-main.log`, `OK (17 tests)`, sin fallos ni omitidos. CI ejecuta la suite en API 31 y 35; su resultado del SHA publicado debe aprobarse antes de merge. Capturas reales 360, 390, 430, 768 y 1024 dp: top/formulario, cinco accesos, claro/contraste/inglés y texto grande/movimiento reducido. Son 19 PNG en `build/v10-device-evidence/v10-evidence/`; comparación original en `build/v10-evidence/original-360.png`. Export desde filesDir con run-as; sin permisos de almacenamiento adicionales.

QA HTTP real Ktor local: fixtures públicas existentes Alumno, Docente, Coordinación y Admin completaron login/me/logout; credenciales erróneas 401 y tokens revocados 401. Android real: cuenta sólo Coordinación con intención Admin recibió únicamente Coordinación, entró a Home y conservó acceso tras rotación y arranque en frío con nueva validación auth/me. Logout, Atrás y relanzamiento conservaron desconexión; contraseña incorrecta mostró error español. Evidencia: `build/v10-live-ui/`, `v10-live-ui-fresh.log`, `v10-live-ui-restoration-final.log`, `build/v10-live-ui-logout-final.log`, `v10-live-ui-invalid.log`, `v10-live-api-smoke-current.log`.

Una captura blanca inicial no demostró causa de producto: se corrigió la detección de jerarquía fresca del probe y se verificó Home real nuevamente. Revisión independiente corrigió contraste de Registro en claro, intención sin consumir tras recreación y carrera de persistencia tardía versus autoridad actual; regresiones automatizadas cubren las correcciones. Secret scan, whitespace y checks deben aprobar el SHA publicado. Logs/PNG/APK locales quedan ignorados; no se añaden secretos ni credenciales nuevas.

## Pendientes reales

MFA y proveedor institucional productivo pendientes. No existen fixtures locales Tutor/Super Admin/multirrol: sus decisiones de autorización y navegación tienen pruebas automatizadas, sin afirmar sesiones reales ejecutadas. TalkBack manual y aprovisionamiento productivo pendientes. Conexión, expiración, renovación y revocación tienen cobertura automatizada, que no equivale a QA manual en red productiva. PR iOS #127 permanece separado, draft y sin merge. Este cambio no certifica iOS ni producción.

## Integración del runner de nube

CI compila los APK antes de arrancar el AVD y ejecuta un único helper Bash. Se retienen los APK durante la ejecución (`android.injected.androidTest.leaveApksInstalledAfterRun=true`) para exportar filesDir antes de que el runner efímero se descarte. El helper conserva el estado de salida y exige 19 PNG; el job exige 21 o más casos, incluidos los cuatro nuevos de autoridad docente, sin fallos ni omitidos. La prueba visual desplaza la Activity real mediante semántica Compose y comprueba formulario, contraseña y envío visibles tras cada cambio de tamaño, evitando coordenadas antiguas del gesto UiScrollable. El runner conserva renderizado software; la documentación oficial marca [swiftshader_indirect como obsoleto](https://developer.android.com/studio/run/emulator-acceleration), por lo que su uso aquí se limita a compatibilidad de CI y no altera dispositivos reales. Los primeros fallos de CI y su diagnóstico quedan en logs locales `v10-ci-*-failure.log`; no se cuentan como aprobados.

Helper completo verificado localmente: `v10-instrumentation-ci-helper-final.log`, BUILD SUCCESSFUL en 2m45s, 17 casos sin fallos/omitidos, estado de salida 0 y 19 PNG exportados. Prueba visual focalizada: `v10-visual-clock-targeted.log`, OK (1 test). La regla Compose sincroniza su reloj antes de consultar o capturar la superficie nativa. Código productivo y APK Debug permanecen iguales a la verificación completa anterior.

CI `3084e59`: API 31 aprobó rama y PR, con suite y exportación; API 35 perdió el proceso del emulador durante LoginLocaleActivityTest tras cuatro pruebas, con fallo XML vacío y device not found. No se atribuye a un fallo de autorización ni se declara OOM sin datos del host. El helper registra memoria/RSS por nombre de proceso y eventos OOM/segfault del kernel, sin argumentos ni variables de entorno, para distinguir fallo del runner. Merge sigue condicionado a la matriz completa.

Diagnóstico `7bbab41`: QEMU desaparece con ~10 GB disponibles y sin eventos OOM/segfault registrados. Esto no prueba el mecanismo nativo del cierre, pero no respalda una corrección de memoria de la app. La ejecución API 35 antigua que completó 17 casos tenía Vulkan predeterminado habilitado; la configuración fallida lo deshabilitaba explícitamente. Se elimina solamente `-feature -Vulkan` para comprobar esa diferencia, manteniendo renderer, hardware, suite y gates. Resultado de nube pendiente antes de merge.

A/B `acdbef1`: retirar la restricción Vulkan no evitó la pérdida de QEMU; se descarta esa diferencia como solución suficiente. Se compara ahora el renderer de compatibilidad `swiftshader_indirect`, usado en el run antiguo completo, manteniendo hardware, suite y gates. El diagnóstico lee sólo metadatos de excepción nativa y módulo del minidump, sin publicar memoria cruda ni variables de entorno. Cloud y merge siguen pendientes.

A/B `16d3a61`: el renderer de compatibilidad también pierde QEMU con perfil Pixel 2; no se atribuye mecanismo porque no se encontró minidump. Se vuelve al hardware virtual predeterminado del runner del run antiguo completo. La suite visual conserva sus cinco anchuras reales y el gate de 21 pruebas tras integrar #134. Los 14 archivos de #134 coinciden exactamente con main.

Verificación local posterior a #134: `v10-instrumentation-main134.log`, API 30, `OK (21 tests)` en 100.721 s y 19 PNG reales exportados. Se incluyen las cuatro nuevas pruebas de autoridad docente. APK y capturas corresponden al código productivo `0ba6058`; el manifiesto ignorado `build/v10-release/BUILD_PROVENANCE.json` conserva SHA y hashes.

A/B `c6d4153`: hardware predeterminado tampoco evitó la pérdida de QEMU en API 35, tanto en rama como en PR. En la rama ocurrió durante la primera prueba existente de asistencia, antes del login. API 31, compilación Android y backend aprobaron ambas ejecuciones. Se fija el runtime nativo a Emulator 36.4.9 estable, build `14788078`, en ambas API, sin reducir la suite ni sus gates. La [documentación oficial del action](https://github.com/ReactiveCircus/android-emulator-runner) permite `emulator-build`; la [versión estable](https://developer.android.com/studio/releases/emulator) y el [archivo Google](https://dl.google.com/android/repository/emulator-linux_x64-14788078.zip) permiten reproducir esta comparación. Es una prueba de entorno; sólo una ejecución completa puede confirmar su resultado.

Integrado #135, main `03fbe9f24a0f48e0801d8a848e5e75d303621762`, sin conflictos ni cambios a sus tres archivos de Registro. Wrapper del código combinado `93e303a`: `v10-main135-full-verification.log`, BUILD SUCCESSFUL en 2m40s, 412 pruebas JVM sin fallos/errores/omitidos y lint 0 errores/55 avisos; Debug y Release regenerados. La corrección añade dos casos: el gate final exige 23 o más, sin fallos ni omitidos.

Comparación `e24a1fe`: API 31, backend y empaquetado aprobaron rama y PR; API 35 perdió QEMU tras 15 casos aprobados en ambas ejecuciones, durante el primer caso de revisión docente. No se encontraron minidumps ni eventos OOM del host. El pin no es una solución suficiente. Se añade diagnóstico de señales/salida del proceso nativo mediante [strace](https://github.com/strace/strace/blob/master/doc/strace.1.in), excluyendo syscalls, memoria, argumentos y entorno, más salida del kernel virtual. Es diagnóstico pendiente, no una ejecución aprobada ni una relajación de los gates.

Verificación local final tras #135: `v10-instrumentation-main135.log`, `OK (23 tests)` en 137.079 s, salida 0; 19 PNG nuevos exportados y comprobados (CRC de chunks, IDAT y cierre IEND). APK Debug/Release proceden de `93e303a`, hashes y procedencia en `build/v10-release/BUILD_PROVENANCE.json`. AVD propio cerrado al terminar.

Diagnóstico `51b57db`: se confirma `SIGSEGV (core dumped)` del proceso nativo QEMU en API 35; API 31 aprueba la suite. La causa dentro del runtime aún no está identificada. En el siguiente run se permite un core efímero únicamente en el runner aislado GitHub, se extraen nombres de funciones sin argumentos/locales/registros mediante GDB y se elimina el core tras inspección. El directorio privado `build/v10-native-cores` está excluido de todas las rutas de artifacts; sólo se guarda metadata sanitizada. Los gates no cambian y el merge continúa bloqueado hasta la matriz vigente verde.
