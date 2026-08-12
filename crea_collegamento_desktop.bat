@echo off
setlocal
cd /d "%~dp0"
if not exist "PixelSheetConverter.exe" (
  echo PixelSheetConverter.exe non trovato nella cartella corrente.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$shell = New-Object -ComObject WScript.Shell; $link = $shell.CreateShortcut([Environment]::GetFolderPath('Desktop') + '\Pixel Sheet Converter.lnk'); $link.TargetPath = (Join-Path (Get-Location) 'PixelSheetConverter.exe'); $link.WorkingDirectory = (Get-Location).Path; $link.Save()"
if errorlevel 1 (
  echo Impossibile creare il collegamento.
) else (
  echo Collegamento creato sul desktop.
)
pause

