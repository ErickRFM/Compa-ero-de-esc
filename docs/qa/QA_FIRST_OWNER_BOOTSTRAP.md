# Primera autoridad global: ensayo aislado

**Borrador de diseño: no ejecutar.** La lógica todavía no está conectada al arranque ni lista para despliegue. Quedan pendientes una recuperación segura tras la caducidad de una reserva incompleta y evidencia de aislamiento de credenciales y base. El código experimental rechaza producción y cualquier base cuyo nombre no termine en `_qa`; ese nombre por sí solo no acredita aislamiento. No crea cuentas ni reemplaza contraseñas. El rol `SUPER_ADMIN` se añade a los roles existentes; la institución académica se conserva. No activa registro de la institución.

## Antes de ejecutar

- Verificar servicio `staging`, base aislada, compilación y pruebas; no utilizar el servicio compartido.
- El candidato debe tener una cuenta nativa existente, correo verificado y autenticador configurado. El operador debe tener autorización de administración del servicio de pruebas. No transmitir contraseña, OTP ni prueba por chat.
- El valor `DeclaredOperator` es una identificación **declarada**, no una identidad autenticada por la aplicación. Correlacionar la operación con los registros de auditoría del proveedor. Este límite impide usar el mecanismo como solución de producción.
- La reserva global es permanente y una interrupción puede requerir intervención especializada; no borrar reservas ni editar roles a mano para recuperarse.

## Preparación

Una vez resueltos y certificados los bloqueos anteriores, el procedimiento previsto usaría una imagen ya compilada y este script local:

```powershell
.\infrastructure\scripts\Prepare-QaFirstOwner.ps1 -QaApiOrigin 'https://companero-qa-univ-v25.onrender.com/' -CandidateEmail 'erik55509@gmail.com' -DeclaredOperator 'operador-qa' -Reason 'Alta inicial QA autorizada'
```

El titular introduce la contraseña y el código MFA en el equipo. El script prepara un comando ligado a cuenta, correo, revisión, operación, operador declarado, motivo, caducidad y rol. Copia temporalmente la configuración privada al portapapeles; el operador la pega en **Environment → Add from .env** del servicio QA y despliega la imagen ya compilada. La prueba MFA dura cinco minutos. Después del arranque, desactivar `QA_OWNER_BOOTSTRAP_ENABLED`, eliminar `QA_OWNER_BOOTSTRAP_PROOF` y desplegar nuevamente. El script borra el portapapeles tras confirmar la operación.

La escritura solo ocurre si el correo está verificado, la cuenta está activa, los roles son no privilegiados, no existe otro súper administrador, la prueba MFA coincide con el comando y la reserva atómica global no presenta conflicto. Un reintento exacto recupera el resultado ya confirmado, pero una revocación posterior no restaura el rol. La revisión de autenticación cambia y las sesiones anteriores deben dejar de servir.

Antes de afirmar éxito, verificar en QA la identidad, los roles, la revisión, la reserva, la auditoría y un nuevo inicio de sesión en ambas aplicaciones. Confirmar también el cierre de la sesión antigua. No ejecutar este procedimiento en producción; allí faltan el diseño y la validación independientes de la autorización del operador y del procedimiento de recuperación.
