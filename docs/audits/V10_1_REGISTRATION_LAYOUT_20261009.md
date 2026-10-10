# V10.1 — Registro V8 con texto ampliado

## Problema y corrección

El Registro integrado ya reutiliza `AuthV8Layout`, fondo universitario,
encabezado, panel de vidrio, campos y acciones V8. En el APK verificado,
a 360dp y fuente 200%, las opciones de cuenta repartían «Alumno» y «Docente»
entre líneas dentro de dos columnas estrechas.

Se mantienen esas mismas opciones, selección, colores y acciones. Su disposición
se adapta al ancho disponible y a la escala de fuente: dos tarjetas por fila
cuando hay espacio, una tarjeta de ancho completo cuando el texto requiere más.
Se conserva el grupo semántico de selección única y el desplazamiento existente.

## Evidencia

- Captura real de emulador API 30: partición de palabras reproducida; las acciones
  eran alcanzables y «Ya tengo cuenta» regresaba a Login.
- Compose sobre el Registro real a 360dp: con fuente 200%, `TextLayoutResult`
  registró dos líneas para «Alumno»; falló 1 de 2 pruebas. La disposición normal
  ya conservaba ambas opciones en una fila.
- Después del ajuste pasan las dos: etiquetas completas, opciones separadas con
  fuente ampliada, fila original con fuente normal, selección docente y acceso
  a la acción de regreso. El formulario permanece vacío; no se crea una cuenta.
- Revisión independiente sobre árbol inmutable: ningún defecto Critical,
  Important o Minor verificado. No ejecutó pruebas ni alteró archivos.
- Batería completa: 174 Android + 195 API + 23 compartidas = 392 unitarias;
  15 instrumentadas en API 30, cero fallos. Lint: cero errores y 52 advertencias
  en 22 informes. APK debug y release R8 sin firma: `BUILD SUCCESSFUL` (4m30s).
- APK final inspeccionado en emulador: a 360dp/fuente 200% ambas etiquetas
  quedan completas, cada opción ocupa su fila y las acciones siguen alcanzables.
  Captura: `build/v10-1-qa/registration-layout-font200.png` (evidencia local).
- Los 404 archivos seleccionados de runtime/build coinciden con el candidato
  aislado; escaneo de secretos y comprobación de formato: PASS.
- Base integrada: `3d5b016d3b5e5910bcc938ad402ebed0a674e4f1` (squash #134),
  árbol idéntico al padre revisado `10feb32`. Solo se incluyen estos tres archivos
  de Registro; no se duplica la implementación docente. CI del SHA final pendiente.

## Alcance

Sin cambios de autorización, contratos, API, credenciales o configuración local.
Reutiliza todos los recursos gráficos y componentes existentes. Este control
de disposición no certifica TalkBack, contraste, idiomas, todos los perfiles,
dispositivo físico ni toda la matriz de accesibilidad. La configuración temporal
del emulador se restaura al terminar la inspección.
