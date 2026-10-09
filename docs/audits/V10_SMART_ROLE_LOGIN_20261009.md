# V10 Smart Role Login — auditoría y verificación

Base inicial: `0ac109118e87740872b6275dc8f6836047115a0a`. Rama `feat/v10-smart-role-login`, worktree aislado; checkout principal con trabajo ajeno preservado. Integrado main hasta `12da6ba8c7299802372601433c49451ec48125b2`: privacidad Tutor y límites/cancelación PDF. El SHA exacto de entrega y hashes de APK pertenecen al PR y manifiesto de artefactos, evitando una referencia circular en este documento.

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

Wrapper local: `testDebugUnitTest`, `:services:api:test`, `:shared:contracts:test`, `:shared:models:test`, `:shared:validation:test`, `lint`, `:apps:android:app:assembleDebug`, `assembleRelease` y `assembleDebugAndroidTest`. Reportes: Android 177, API 184, contratos 16, modelos 3 y validación 7, sin fallos, errores ni omitidos; lint 0 errores y 55 avisos. Verificación final tras cambio de idioma: `v10-final-locale-verification.log`. Debug entregado usa URL predeterminada; Release minificado sin firma conserva `https://invalid.invalid/` y no se publica.

Instrumentación propia API 30: 13 pruebas de login, carrusel, estado, idioma, capturas de Activity real, smoke y privacidad Tutor. CI ejecuta la suite en API 31 y 35; su resultado del SHA publicado debe aprobarse antes de merge. Capturas reales 360, 390, 430, 768 y 1024 dp: top/formulario, cinco accesos, claro/contraste/inglés y texto grande/movimiento reducido. Son 19 PNG en `build/v10-device-evidence/v10-evidence/`; comparación original en `build/v10-evidence/original-360.png`. Export desde filesDir con run-as; sin permisos de almacenamiento adicionales.

QA HTTP real Ktor local: fixtures públicas existentes Alumno, Docente, Coordinación y Admin completaron login/me/logout; credenciales erróneas 401 y tokens revocados 401. Android real: cuenta sólo Coordinación con intención Admin recibió únicamente Coordinación, entró a Home y conservó acceso tras rotación y arranque en frío con nueva validación auth/me. Logout, Atrás y relanzamiento conservaron desconexión; contraseña incorrecta mostró error español. Evidencia: `build/v10-live-ui/`, `v10-live-ui-fresh.log`, `v10-live-ui-restoration-final.log`, `build/v10-live-ui-logout-final.log`, `v10-live-ui-invalid.log`, `v10-live-api-smoke-current.log`.

Una captura blanca inicial no demostró causa de producto: se corrigió la detección de jerarquía fresca del probe y se verificó Home real nuevamente. Revisión independiente corrigió contraste de Registro en claro, intención sin consumir tras recreación y carrera de persistencia tardía versus autoridad actual; regresiones automatizadas cubren las correcciones. Secret scan, whitespace y checks deben aprobar el SHA publicado. Logs/PNG/APK locales quedan ignorados; no se añaden secretos ni credenciales nuevas.

## Pendientes reales

MFA y proveedor institucional productivo pendientes. No existen fixtures locales Tutor/Super Admin/multirrol: sus decisiones de autorización y navegación tienen pruebas automatizadas, sin afirmar sesiones reales ejecutadas. TalkBack manual y aprovisionamiento productivo pendientes. Conexión, expiración, renovación y revocación tienen cobertura automatizada, que no equivale a QA manual en red productiva. PR iOS #127 permanece separado, draft y sin merge. Este cambio no certifica iOS ni producción.
