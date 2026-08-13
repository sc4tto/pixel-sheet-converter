# Pixel Sheet Converter 1.2

[![Tests](https://github.com/sc4tto/pixel-sheet-converter/actions/workflows/tests.yml/badge.svg)](https://github.com/sc4tto/pixel-sheet-converter/actions/workflows/tests.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Applicazione grafica portatile per Windows 10/11 a 64 bit. Converte fotografie e immagini in matrici di celle quadrate colorate ed esporta file PNG e XLSX compatibili con Excel e Google Fogli.

L'interfaccia utilizza uno stile Windows 2000 modernizzato: menu e barra strumenti classici, controlli compatti, area di anteprima ad alto contrasto e barra di stato, mantenendo la cornice nativa di Windows per compatibilità con DPI e ridimensionamento.

## Funzioni

- Input: JPEG, PNG, TIFF, WebP e BMP.
- Anteprima originale e quantizzata.
- Risoluzione configurabile mantenendo automaticamente le proporzioni.
- Ricampionamento Lanczos, bicubico, bilineare o nearest-neighbor.
- Palette RGB, RGB con bianco/nero, CMY, scala di grigi e palette personalizzate.
- Dithering: nessuno, Floyd-Steinberg, Floyd-Steinberg serpentino, Atkinson e Bayer 4x4.
- Confronto colore selezionabile: RGB classico, RGB lineare, OKLab percettivo e CIELAB Delta E 2000.
- Dithering aggiuntivi: Sierra Lite, Stucki e Jarvis–Judice–Ninke.
- Calcolo della dimensione fisica in millimetri mediante il passo pixel, utile per pen plotter.
- Statistiche e legenda per colore.
- Esportazione PNG.
- Esportazione XLSX con una cella per pixel e riempimenti statici.
- Nessuna formattazione condizionale: maggiore compatibilità con Google Fogli.
- Controllo manuale degli aggiornamenti dalla finestra del programma.

## Aggiornare la versione portatile

1. Premi `Controlla aggiornamenti...` nel programma.
2. Se è disponibile una nuova versione, apri la pagina proposta e scarica `PixelSheetConverter-Windows-x64.zip`.
3. Chiudi Pixel Sheet Converter.
4. Estrai il nuovo archivio in una cartella separata.
5. Conserva temporaneamente la vecchia cartella finché hai verificato la nuova versione; il programma non memorizza progetti o immagini dentro la propria cartella.

L'app non si sovrascrive mentre è in esecuzione: per una distribuzione portatile la sostituzione della cartella è più affidabile e facilmente reversibile.

## Uso della versione portatile

1. Estrai integralmente `PixelSheetConverter-Windows-x64.zip` in una cartella.
2. Apri la cartella `PixelSheetConverter`.
3. Avvia `PixelSheetConverter.exe`.
4. Se desideri un'icona sul desktop, esegui `Crea collegamento sul desktop.bat`.

Non spostare soltanto il file EXE: le cartelle che lo accompagnano contengono le librerie necessarie. L'applicazione non richiede installazione né privilegi di amministratore.

## Creare il pacchetto portatile su Windows

Il progetto sorgente include `build_portable.bat`. Serve soltanto per generare l'EXE; chi usa il pacchetto già compilato non deve eseguirlo.

Prerequisito: Python 3.12 a 64 bit installato da python.org.

1. Estrai il progetto in una cartella locale.
2. Fai doppio clic su `build_portable.bat`.
3. Attendi i test e la compilazione.
4. Troverai il risultato in:
   - `dist\PixelSheetConverter\PixelSheetConverter.exe`
   - `dist\PixelSheetConverter-Windows-x64.zip`

Lo script crea un ambiente Python isolato e installa automaticamente le dipendenze necessarie.

## Prestazioni e limiti

- 180 x 240: rapido e molto stabile.
- 543 x 724: alta risoluzione consigliata, circa 393.000 celle.
- Oltre 500.000 celle: il programma mostra un'indicazione `ALTO` e l'esportazione può richiedere tempo.
- Limite di sicurezza: 2.000.000 celle.

Google Fogli può rallentare con documenti molto grandi. Il programma usa riempimenti statici e soltanto gli stili necessari, ma il limite dipende anche dal computer e dal browser.

## Sviluppo in Visual Studio Code

Apri la cartella del progetto e usa il terminale:

```powershell
py -3 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt pytest
python main.py
pytest -q
```

## Privacy

La conversione avviene interamente sul computer. Il programma non carica immagini o risultati su Internet.

## Documentazione tecnica

La cartella `docs` contiene il sorgente LaTeX e il PDF che descrivono l'algoritmo di conversione, la quantizzazione RGB e la diffusione dell'errore Floyd-Steinberg.

## Licenza

Distribuito secondo la licenza [MIT](LICENSE).
