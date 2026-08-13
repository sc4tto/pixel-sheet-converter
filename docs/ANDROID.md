# Pixel Sheet Converter per Android

La build 0.2 adotta il layout mobile **Soft Classic**: pannelli chiari, bordi sottili, comandi con altezza uniforme e gestione delle aree riservate alle barre di sistema e al foro della fotocamera Samsung. L'anteprima cambia altezza in base alla larghezza utile del dispositivo; le etichette dei pulsanti sono brevi e ridimensionabili per evitare ritorni a capo anche con caratteri di sistema ingranditi.

La prima build Android è una versione di prova destinata inizialmente al Samsung Galaxy A36 e ai dispositivi Android 8 o successivi.

## Funzioni della build di prova

- Anteprima CameraX e scatto diretto.
- Fotocamera posteriore o anteriore.
- Zoom, compensazione EV, flash e tap-to-focus quando supportati dal dispositivo.
- Importazione dalla galleria.
- Conversione locale e offline con palette RGB primaria.
- RGB classico, RGB lineare, OKLab e CIELAB Delta E 2000.
- Nessun dithering, Floyd-Steinberg, Floyd serpentino, Atkinson, Bayer 4x4, Sierra Lite, Stucki e Jarvis-Judice-Ninke.
- Calcolo delle dimensioni fisiche mediante passo pixel.
- Statistiche RGB.
- Esportazione PNG e XLSX con riempimenti statici.

## Installazione sul Samsung Galaxy A36

1. Scaricare `PixelSheetConverter-Android-test.apk` dalla Release o dall'artefatto della GitHub Action.
2. Spostare il file nella cartella `Download` del telefono, se è stato scaricato da PC.
3. Aprire **Archivio > Download** e toccare il file APK.
4. Se richiesto, autorizzare **Archivio** in **Sicurezza e privacy > Altre impostazioni di sicurezza > Installa app sconosciute**.
5. Se il Blocco automatico Samsung impedisce l'installazione, disattivarlo temporaneamente, installare e riattivarlo.
6. Concedere il permesso Fotocamera al primo avvio.

## Firma e aggiornamenti

L'APK iniziale è firmato con la chiave di debug generata durante la compilazione ed è destinato al collaudo. Prima della prima release stabile verrà configurata una chiave di firma permanente custodita nei GitHub Actions Secrets. Da quel momento gli aggiornamenti potranno essere installati sopra la versione precedente senza disinstallarla.

## Sviluppo locale

Aprire la cartella `android-app` con Android Studio. Il progetto usa Kotlin, View Binding, CameraX e Android API 35, con `minSdk 26`.
