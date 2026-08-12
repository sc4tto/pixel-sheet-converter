@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   Pixel Sheet Converter - Build portatile Windows x64
echo ========================================================
echo.

where py >nul 2>nul
if errorlevel 1 (
  echo ERRORE: Python non e stato trovato.
  echo Installa Python 3.12 a 64 bit da https://www.python.org/downloads/windows/
  echo Durante l'installazione seleziona "Add python.exe to PATH".
  pause
  exit /b 1
)

if not exist ".build-venv\Scripts\python.exe" (
  echo [1/6] Creazione ambiente di compilazione...
  py -3.12 -m venv .build-venv
  if errorlevel 1 py -3 -m venv .build-venv
  if errorlevel 1 goto :error
)

echo [2/6] Aggiornamento strumenti...
call .build-venv\Scripts\python.exe -m pip install --upgrade pip
if errorlevel 1 goto :error

echo [3/6] Installazione dipendenze...
call .build-venv\Scripts\python.exe -m pip install -r requirements.txt pytest
if errorlevel 1 goto :error

echo [4/6] Esecuzione test...
call .build-venv\Scripts\python.exe -m pytest -q
if errorlevel 1 goto :error

echo [5/6] Creazione programma portatile...
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
call .build-venv\Scripts\pyinstaller.exe --noconfirm PixelSheetConverter.spec
if errorlevel 1 goto :error

copy /y README.md "dist\PixelSheetConverter\LEGGIMI.txt" >nul
copy /y crea_collegamento_desktop.bat "dist\PixelSheetConverter\Crea collegamento sul desktop.bat" >nul

echo [6/6] Creazione archivio ZIP...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "if (Test-Path 'dist\PixelSheetConverter-Windows-x64.zip') { Remove-Item 'dist\PixelSheetConverter-Windows-x64.zip' }; Compress-Archive -Path 'dist\PixelSheetConverter\*' -DestinationPath 'dist\PixelSheetConverter-Windows-x64.zip' -CompressionLevel Optimal"
if errorlevel 1 goto :error

echo.
echo BUILD COMPLETATA.
echo Cartella: dist\PixelSheetConverter
echo Archivio: dist\PixelSheetConverter-Windows-x64.zip
echo Il programma e: dist\PixelSheetConverter\PixelSheetConverter.exe
pause
exit /b 0

:error
echo.
echo BUILD NON RIUSCITA. Leggi il messaggio precedente.
pause
exit /b 1

