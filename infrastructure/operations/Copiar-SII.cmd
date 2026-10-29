@echo off
title Copia protegida de HMAC para SII
powershell.exe -NoProfile -STA -File "%~dp0Use-SiiiHmacRotation.ps1" -Action CopySiiTarget
if errorlevel 1 (
  echo No se pudo completar la copia. Comparte solo los diagnosticos de etapa, nunca valores.
  pause
  exit /b 1
)
