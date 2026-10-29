@echo off
title Copia protegida de HMAC para Render
powershell.exe -NoProfile -STA -File "%~dp0Use-SiiiHmacRotation.ps1" -Action CopyRenderSettings
if errorlevel 1 (
  echo No se pudo completar la copia. Comparte solo los diagnosticos de etapa, nunca valores.
  pause
  exit /b 1
)
