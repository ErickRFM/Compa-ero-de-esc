# Compañero — Auditoría PDF/horario y F0 de integridad (2026-10-20)

## Evidencia del PDF original
El usuario ya proporcionó en otra conversación el PDF 9.º A (27/08/2026).
La página contiene lunes a sábado, y la suite del proyecto espera **25
bloques consolidados** (p.ej., sábado Programación Móvil 07:00–09:00,
miércoles Seguridad Informática 17:00–20:00, jueves Programación Móvil
18:00–20:00). El archivo original **NO se subió al repositorio** por
privacidad; hay una fixture sintética existente para pruebas Kotlin.

## Hallazgos verificables en main anterior
- El lector nativo PdfBox + TextPosition de #112 está integrado.
- ScheduleImportProcessor retornaba tan pronto como el parser nativo
  producía una sola clase: eso bloqueaba el segundo pase OCR aunque
  faltaran días completos.
- El diálogo de revisión solo mostraba el número total de clases,
  sin desglose por día ni advertir sustitución de clases anteriores.
- Algunos proveedores SAF omiten application/pdf.
- V8WeekGrid muestra cinco columnas pero **no es la vista activa**.
  WeeklyAgendaGrid usa lunes-sábado con scroll horizontal y selector
  de días; no duplicar ni reemplazar esa UI sin más evidencia.

## Cambios
- PDF digital: texto primero; si hay menos de 25 bloques o falta alguno
  de los seis días, ejecutar también la extracción raster PdfRenderer
  + ML Kit existente.
- Nunca juntar listas de fuentes contradictorias. Preferir la fuente OCR
  solo cuando añade días sin perder los de PDF nativo o añade tres o más
  bloques, manteniendo proporción razonable de docentes válidos.
- Si falla OCR pero el nativo recuperó clases, conservar ese resultado.
  Cancelar la importación sigue propagándose.
- Revisión muestra conteo de bloques por día y advertencias de cobertura.
  Para importar un horario con días sin detectar o reemplazar un
  horario importado más completo, exige aceptación humana explícita.
- No se modifica SIII, firmas de asistencia, JWT, Room, horario institucional,
  ni la lógica de clases que ya existe.

## Límites pendientes
- Las pruebas reproducen el horario desde la fixture 9A del repositorio,
  no el PDF original ejecutado en Android: **sin validación física E2E**.
- El umbral 25/6 es una heurística conservadora para activar el OCR
  adicional, no una regla escolar universal: los horarios legítimamente
  más pequeños siguen permitidos tras revisión humana.
- Para afirmar importación íntegra, comparar manualmente contra la página
  original en el teléfono. Deben conservarse miércoles, jueves y sábado,
  las materias, docentes y horas de inicio/fin.

## Repositorios revisados
- TomRoush/PdfBox-Android: motor actual adecuado para glifos y coordenadas.
- tobiasschuerg/android-week-view: interesante para zoom y superposiciones
  Compose, pero no soluciona una importación incompleta.
- barteksc/AndroidPdfViewer / PdfiumAndroid: visualización, no sustituyen
  por sí mismos el parser espacial de horarios.
Recomendación: mantener las bibliotecas actuales y mejorar pruebas reales.
