# V8: vidrio global y encabezados compactos

Entrega del 2026-10-08. El usuario pidió globalizar el glassmorfismo previo, actualizar e integrar las ramas y retirar el bloque de logo, nombre y lema de **todas las pantallas internas**. Login conserva la marca.

La rama `codex/v8-global-glass-compact-20261008` parte de main `eb9c8f3`, que incluye la auditoría #100 y la integración docente #106. Se integran únicamente los ajustes pendientes de vidrio sobre ese código actual; se conservan las correcciones de navegación, roles, lista docente, calificaciones, autenticación y canales.

## Cambios

- `V8FrostedGlassPanel` centraliza tinte oscuro, reflejo suave, borde iluminado, sombra y énfasis. `V8GlassCard` delega el acabado y el color de contenido en ese componente.
- Filtros de horario, selectores de materia y recursos/respuestas del canal usan chips compartidos con la semántica y acciones nativas. Alto contraste mantiene superficie opaca, borde sólido y marca de selección.
- Alumno y docente comparten un solo fondo de campus entre destinos. Las vistas conservan el fondo local cuando se usan fuera de ese scaffold.
- Se elimina `V8BrandHeader` de Inicio, Horario, Asistencia, Clases, Canal, Perfil y Evaluación, incluido Inicio docente. Se conserva en Login.
- El acceso docente a Perfil se integra junto al título para evitar que se superponga a Actualizar. La etiqueta seleccionada de la barra inferior usa una línea con elipsis al aumentar la fuente; conserva el nombre completo para accesibilidad.

El acabado funciona en API30 y no aplica blur al texto. Es un acabado dibujado; no se declara como desenfoque nativo del fondo. Errores, advertencias y éxito conservan sus colores semánticos. No se añaden bibliotecas externas.

## Verificación

Comando local, con la API HTTPS de Render:

```powershell
$env:COMPANERO_API_BASE_URL='https://compa-ero-de-esc.onrender.com/'
.\gradlew.bat :apps:android:app:assembleDebug testDebugUnitTest :apps:android:app:lintDebug --no-parallel --max-workers=1 --no-daemon --console=plain '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'
```

**PASS local sobre el código `99b198c`: 126 pruebas en 32 suites, cero fallos/errores/skipped; lint sin Fatal/Error y 8 advertencias existentes.** El log final es `build-final.txt` (1m37s, 995 tareas).

Resultados y capturas finales se registran en `v8-glass-evidence/verification.json` y en el manifiesto local `dist/v8-audit/glass/manifest.json`. Se mantienen los intentos previos en la carpeta local `intermediate-evidence-before-main`; no se presentan como prueba del binario final.

La corrección de `SessionViewModelTest` espera la cancelación de los ViewModels antes de retirar el dispatcher Main. Conserva las aserciones y no modifica la autenticación de producción.

La revisión independiente detectó dos riesgos al retirar la marca: el botón superpuesto de Perfil tapaba Actualizar en Canal docente y el estado sin rol perdía Perfil al retirar ese overlay. Se corrigieron integrando la acción en `V8ScreenHeader`, incluido `RoleUnavailableScreen`. La segunda revisión no encontró problemas Critical/Important pendientes. Los demás roles conservan el acceso por sus títulos; su validación aquí es estática, no un recorrido completo de cada rol.

El AVD aislado `GlassQA_API30`, serial `emulator-5556`, utiliza Android11/API30, 1080×1920 y 420dpi. El recorrido visual comprueba las vistas internas, tema claro, alto contraste y fuente200%, restaurando las preferencias. La cuenta existente `ana.lopez` y el login por formulario están autorizados por el usuario. No se escriben asistencias ni publicaciones en estos recorridos.

El recorrido de alumno **PASS** comprueba Inicio, Horario, Asistencia, Clases, Canal, Perfil y Login; tema Claro, alto contraste con estado checked verificado, fuente de Android200%, retorno a la escala1.0 y restauración de sesión tras reiniciar. `final-ui-qa-current-labels.txt` conserva el recorrido. Un intento previo usaba MAT-101; main ahora presenta 101-A y 204-B. Se corrigió el harness, conservando el fallo en `final-ui-qa.txt`; no era un error de navegación de la app.

El recorrido docente **PASS** usa `elena.rios`, cuenta mock de desarrollo documentada en el repositorio, autenticada por formulario en Render dentro del AVD aislado. Comprueba Inicio docente, Canal, Horario, Clases y Asistencia sin marca, navegación a Perfil, retorno y fuente200%. Los targets nativos de Actualizar `[694,96][912,222]` y Perfil `[913,96][1039,222]` no se intersectan. Pulsar Actualizar permanece en Canal y pulsar Perfil abre Mi perfil. `teacher-ui-qa-5556.txt` conserva el resultado. No se abren pases ni se publica contenido.

Las capturas `12-teacher-*before` corresponden al emulador principal antes de la actualización; las capturas finales de las pruebas se toman en `emulator-5556`. El emulador principal recibió la misma APK y conserva sus datos.

La APK se instala con `adb install -r`, sin borrar datos. Su SHA-256 es `4b70cad85ec335b11996a0081460c96399a3f2caf236f31621652fa08fa8e2fd`; coincide con el `base.apk` extraído del AVD. `BuildConfig.API_BASE_URL` contiene la URL HTTPS de Render. La consulta `/health` final informa servicio up y MongoDB up.

El horario semanal con fuente200% conserva desplazamiento y acceso a clases, pero las etiquetas largas de columnas y bloques se parten en líneas. Estas capturas no se presentan como fidelidad completa o como prueba de que toda la semana quepa sin desplazamiento.

## Límites

La fidelidad completa de las cinco referencias continúa parcial según la auditoría anterior. Wi-Fi/QR institucionales, TalkBack hablado, alta externa y CRUD/arrastre completos no se validan en esta entrega. Los datos y disponibilidad del backend se registran tal como aparecen. La petición posterior del usuario autoriza el merge de estos cambios; sustituye la restricción de mantener la auditoría inicial en borrador.
