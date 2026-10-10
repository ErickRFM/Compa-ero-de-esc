# V10 Smart Role Login — auditoría y verificación

Base inicial `0ac109118e87740872b6275dc8f6836047115a0a`; rama `feat/v10-smart-role-login`, PR #133, worktree aislado. Integrado main `03fbe9f24a0f48e0801d8a848e5e75d303621762`, incluyendo #132, autoridad docente #134 y Registro accesible #135. Checkout principal y trabajo ajeno preservados; sin force push. SHA de entrega y hashes de APK se registran en PR/manifiesto para evitar una referencia circular.

| Componente auditado | Clasificación y cambio real |
|---|---|
| LoginScreen/AuthV8Layout | MODIFICAR: un formulario estable y cinco intenciones en HorizontalPager |
| RoleExperienceResolver/Destination/experiencias | REUTILIZAR: intención comprobada contra autoridad vigente |
| Perfil/ActiveExperiencePreferences | REUTILIZAR: experiencia autorizada por cuenta y cambio desde Perfil |
| Ktor/JWT/Keystore/refresh/logout | EXISTE/REUTILIZAR: protocolos conservados; auth/me, cancelación y concurrencia corregidos |
| Campus/crimson/panel mate/tokens V9 | REUTILIZAR: adaptación clara opt-in sólo del login |
| AppearancePreferences/CompaneroTheme/movimiento | REUTILIZAR: es/en, contraste, escala, reducción y transición de 220 ms |
| Autoridad V10.1/limitación de intentos | EXISTE: endpoints, revocación y 429/Retry-After conservados |
| Registro #135 | REUTILIZAR: tres archivos idénticos a main, layout adaptable al 200 % |
| MFA/proveedor institucional productivo | FALTA: no implementados por esta misión |

## Comportamiento implementado

Alumno, Docente, Tutor, Admin y Super Admin cambian mediante swipe, flechas e indicadores sobre un único formulario. La intención no concede roles. Coordinación mantiene su experiencia autorizada aunque no sea una sexta tarjeta; TEACHER_PENDING mantiene sus restricciones. Una intención no autorizada abre el selector existente. La elección autorizada se guarda por cuenta y puede cambiarse desde Perfil, sin crear sesiones duplicadas ni duplicar Home.

`auth/me` publica identidad y roles vigentes, también al restaurar sesión. No se confía en el rol del carrusel ni en claims JWT para restaurar autoridad. Se evita doble envío, se propaga cancelación de persistencia/refresh y una respuesta tardía de login no sustituye la autoridad nueva. Logout no permite reapertura por respuestas tardías. Usuario e intención sobreviven recreación; la contraseña no se guarda en estado restaurable.

Se reutilizan validación, mostrar/ocultar contraseña, carga y errores localizados. Crear cuenta sólo para Alumno/Docente. Login respeta es/en, tema claro/oscuro/automático, alto contraste, escala y movimiento reducido. Registro conserva superficie oscura V8 y el ajuste adaptable de #135. Idioma guardado aplicado después de Activity.onCreate; controles con semántica y objetivos de 48 dp. TalkBack humano no ejecutado.

## Verificación ejecutada

Código productivo compilado `93e303a4020d8937e9ac67d1bdeb1c5af1c9db0a`. Los commits posteriores sólo afectan CI, diagnóstico y documentación; su diff en apps/services/shared es cero.

Wrapper: testDebugUnitTest, :services:api:test, :shared:contracts:test, :shared:models:test, :shared:validation:test, lint, :apps:android:app:assembleDebug, assembleRelease y assembleDebugAndroidTest. `v10-main135-full-verification.log`: BUILD SUCCESSFUL en 2m40s. Android 191, API 195, contratos 16, modelos 3 y validación 7: **412 pruebas JVM**, sin fallos, errores ni omitidos; lint 0 errores/55 avisos. Debug usa URL predeterminada; Release minificado sin firma conserva https://invalid.invalid/ y no se publica.

Emulador propio API30: `v10-instrumentation-main135.log`, **OK (23 tests)** en 137.079 s, salida 0. Incluye login/carrusel/estado/idioma, Activity real, Registro al 100/200 %, asistencia, privacidad Tutor y autoridad docente. AVD propio cerrado al terminar.

**19 PNG reales** en build/v10-device-evidence/v10-evidence/: top/formulario en 360/390/430/768/1024 dp, cinco accesos, claro/contraste/inglés y texto grande/movimiento reducido. Export desde filesDir con run-as, sin permisos de almacenamiento adicionales; integridad PNG comprobada mediante CRC/IDAT/IEND. Comparación V9 en build/v10-evidence/original-360.png. APK Debug/Release, hashes y procedencia: build/v10-release/BUILD_PROVENANCE.json. Logs/PNG/APK locales quedan ignorados.

QA HTTP Ktor local: fixtures públicas existentes Alumno/Docente/Coordinación/Admin completaron login/me/logout; credenciales erróneas 401 y tokens revocados 401. Android real: cuenta sólo Coordinación con intención Admin recibió únicamente Coordinación, entró a Home y conservó acceso tras rotación y arranque frío con nueva validación auth/me. Logout/Atrás/relanzamiento mantuvieron desconexión; contraseña incorrecta mostró error español. Evidencia build/v10-live-ui/, v10-live-ui-restoration-final.log, build/v10-live-ui-logout-final.log, v10-live-ui-invalid.log y v10-live-api-smoke-current.log. Se observó una pantalla blanca inicial sin causa determinada; tras corregir el probe de jerarquía fresca se verificó Home nuevamente en arranque frío y rotación.

Revisión independiente corrigió contraste de Registro en claro, intención sin consumir tras recreación y persistencia tardía versus autoridad actual; regresiones automatizadas cubren esos casos. Fuente integrada #134/#135 y diagnóstico revisados sin bloqueantes. Whitespace/secret scan aprobados. No se añaden secretos ni credenciales nuevas.

## CI y diagnóstico del emulador

CI compila APK antes de arrancar el AVD, ejecuta un único helper Bash, conserva su estado de salida y retiene APK para exportar filesDir antes del descarte del runner. Se exigen **23 o más pruebas, cero fallos/errores/omitidos y 19 PNG** en API31 y API35. La captura sincroniza reloj Compose y desplaza la Activity mediante semántica, evitando coordenadas antiguas tras cambiar resolución.

API31 de `51b57db` aprobó rama y PR; artifacts del PR comprobados: 23 casos/0 fallos/0 errores/0 omitidos y 19 PNG. Backend y empaquetado también aprobaron. Estos resultados anteriores no sustituyen los checks del SHA vigente.

API35 perdió QEMU con distintos perfiles, Vulkan predeterminado/override y modos software. Pin a Emulator 36.4.9/build14788078 tampoco bastó. `51b57db` confirmó SIGSEGV del proceso nativo; `f278338` confirmó core con frames sin símbolos suficientes para atribuir la causa. Hubo fallos incluso en asistencia anterior al login; no se atribuye un mecanismo específico ni OOM de host sin evidencia. Logs detallados v10-ci-*-failure.log conservan cada ejecución fallida.

La configuración compara el backend soportado [swangle (SwiftShader con ANGLE)](https://developer.android.com/studio/run/emulator-acceleration), manteniendo versión/API/hardware/suite. [emulator-build](https://github.com/ReactiveCircus/android-emulator-runner) fija el runtime de [Emulator estable 36.4.9](https://developer.android.com/studio/releases/emulator). Comparación `c9e4878`: API31 y API35 aprobaron rama y PR; los cuatro artifacts se descargaron y comprobaron, cada uno con 23 casos/0 fallos/0 errores/0 omitidos y 19 PNG. El cambio de backend evita el cierre en estas ejecuciones; no identifica por sí solo la causa interna del runtime. La matriz del SHA final sigue siendo requisito de merge.

En 447fee2, API35 de rama completó 23 pruebas sin fallos/errores/omitidos, pero la lectura ADB del tar de capturas llegó truncada (Unexpected EOF; sólo un PNG parcial); se mantiene como ejecución fallida. API35 del PR del mismo SHA completó pruebas y 19 PNG. El exportador valida rutas, archivos únicos, CRC, IDAT e IEND de al menos 19 PNG antes de publicarlos y permite hasta tres lecturas de evidencia; nunca reintenta las pruebas. Diez regresiones del exportador cubren truncación, corrupción, conjunto incompleto, rutas/symlinks, nombres duplicados/aliases de plataforma y errores ADB sin publicar payloads. También se verificó recuperación con las 19 capturas API30 reales. Todos los checks del nuevo HEAD siguen siendo requisito de merge.

Diagnóstico: memoria/RSS por nombre de proceso, eventos de kernel, señales/salida nativa y metadata de excepción. Sólo en runner efímero GitHub se permite core privado; GDB desactiva auto-loading y publica funciones/módulos sin argumentos/locales/registros. Core eliminado tras inspección, fuera de todas las rutas de artifacts; no se sube memoria cruda, entorno ni payloads. Fallos diagnósticos no convierten pruebas fallidas en PASS.

## Pendientes reales

MFA, proveedor institucional productivo, TalkBack manual y aprovisionamiento/firma/configuración productiva de Release. Sin fixtures locales Tutor/Super Admin/multirrol: sus decisiones tienen cobertura automatizada, sin afirmar sesiones reales ejecutadas. Cobertura automatizada de conexión/expiración/renovación/revocación no equivale a QA manual en red productiva. PR iOS #127 sigue separado, draft y sin merge. Esta entrega no certifica iOS ni producción. Merge V10 condicionado a la matriz completa del SHA vigente aprobada.
