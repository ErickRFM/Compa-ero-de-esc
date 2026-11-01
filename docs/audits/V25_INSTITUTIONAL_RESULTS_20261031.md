# V25: resultados institucionales y aprovisionamiento QA

## Implementado

- Afiliación de estudiante solicitada en registro, autorizada por administrador institucional y MFA ligado al comando completo. No crea matrícula ni inscripción ficticia.
- Gestión Android permite aprobar afiliación y vincular las cuentas reales mediante el endpoint institucional existente y MFA. Se conservan cuentas y credenciales independientes de SIII.
- Bootstrap administrativo opt-in solo en staging/local y base exclusiva QA; requiere candidato verificado, autenticador, prueba vigente, reserva persistente, CAS y auditoría. No modifica la cuenta personal ni permite producción/base compartida.
- Archivo HMAC de propósito exclusivo attendance:archive, paginado por identidad estable. El contrato v1 de lectura sigue sin campos nuevos.
- Registro de primer cierre inmutable. Padrón validado antes del CAS, continuación durable y replay idempotente tras caída; las clases a medio cerrar no se exportan como realizadas. El cierre administrativo conserva su distinción.
- PostgreSQL SIII conserva revisiones append-only de decisiones y clases, con prueba del snapshot original, RLS, separación de credenciales, trabajos por alumno/docente, leases, reintentos y recuperación auditada.
- Historiales con filtros, paginación, decisiones docentes y métricas; clases realizadas con cierre docente, pendientes, abiertas y canceladas. Incluye clases sin asistencia; no infiere ausencias de fallos del transporte.
- Materialización oficial desde patrones existentes, con capacidad, conflictos, cancelación conservada y publicación transaccional.

## Evidencia local

- SIII: 1059 pruebas unitarias; TypeScript, lint y build de producción aprobados.
- PostgreSQL/HTTP real local: 9 pruebas nuevas aprobadas; revisiones fuera de orden, fuente congelada, autorización, revocación, clase sin asistencia, RLS y cancelación.
- Ktor: pruebas de asistencia, permisos, afiliación y bootstrap con Mongo real aprobadas; incluye caída a mitad de cierre y lectura desde repositorios independientes. XML conservado localmente en build/v25-evidence/closure.
- Android: pruebas unitarias Home/Admin aprobadas; empaquetado Academic/Management Debug ejecutado. Los APK conservan el destino existente y Firebase no configurado; no acreditan el entorno QA.
- Revisión independiente: corregidos RETURNING del replay docente y cierre parcial; sin otros P0/P1/P2 materiales en la revisión estática final.
- Blueprint QA validado contra esquema oficial de Render. No se creó aún el recurso remoto.

## Pendiente verificable

No hay cuentas nativas QA verificadas, primer administrador autorizado ni vínculo QA ejecutado. Las cuentas SIII existentes se conservaron. No se publicó SEC-QA1 remotamente, no se aplicó 0027 a producción, ni se verificó FCM/dispositivos reales. No se acredita certificación E2E remota.

Pasos y botones del alta oficial: [QA inicial](../qa/V25_QA_INITIAL_BOOTSTRAP.md). CI, merge y despliegues deben comprobarse con los SHA finales antes de declarar liberación.
