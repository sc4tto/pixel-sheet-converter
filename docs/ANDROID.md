# Pixel Sheet Converter per Android

La build 0.9 adotta l'interfaccia **MSN Companion**: riprende la struttura della versione Windows e il linguaggio visivo di Windows Live Messenger, con superfici azzurro-vetro, riflessi bianchi, bolle trasparenti, pannelli ghiaccio sovrapposti e accenti verde lime. Il flusso è diviso in tre schermate — Fotocamera, Conversione e Risultato — raggiungibili dalla barra superiore e collegate da passaggi automatici.

Il tema mantiene un'impostazione elegante e leggibile: le decorazioni rimangono a bassa opacità, i pannelli utilizzano doppi bordi luminosi e il verde MSN identifica selezione, anteprime e azioni principali senza dominare l'interfaccia.

La fotocamera non usa più fasce ondulate separate: il pannello superiore incorpora il proprio bordo curvo e i controlli inferiori sono contenuti in una nuvola sospesa con fotografia visibile intorno. I pulsanti utilizzano raggi più generosi, da 12 a 16 dp, senza diventare completamente ovali.

Il bordo superiore dispone di due linee ondulate non coincidenti e lascia maggiore respiro sotto i pulsanti. La nuvola inferiore è ondulata sui quattro lati e raccordata da fillet ampi agli angoli.

Il layout conserva bordi sottili, pannelli chiari e comandi con altezza uniforme. Gestisce inoltre le aree riservate alle barre di sistema e al foro della fotocamera Samsung. Le anteprime cambiano altezza in base alla larghezza utile del dispositivo e le etichette principali restano su una sola riga per evitare disallineamenti anche con caratteri di sistema ingranditi.

La modalità Fotocamera è immersiva: l'anteprima CameraX si estende fino ai bordi fisici, eliminando la fascia vuota superiore, mentre i controlli rispettano automaticamente foro della fotocamera e aree di sistema. Zoom, esposizione, flash, scatto, galleria e cambio camera sono sovrapposti all'immagine. Una griglia fotografica 3×3 può essere attivata dal comando **Griglia**. Le barre di sistema possono ricomparire temporaneamente con uno scorrimento dal bordo.

La prima build Android è una versione di prova destinata inizialmente al Samsung Galaxy A36 e ai dispositivi Android 8 o successivi.

## Funzioni della build di prova

- Anteprima CameraX e scatto diretto.
- Fotocamera posteriore o anteriore.
- Anteprima fotografica immersiva a tutto schermo e griglia 3×3 attivabile.
- Zoom, compensazione EV, flash e tap-to-focus quando supportati dal dispositivo.
- Importazione dalla galleria.
- Navigazione a tre fasi con anteprima separata dell'immagine originale e del risultato.
- Conversione locale e offline con RGB primari, RGB con bianco e nero, CMY e scala di grigi a 8 livelli.
- Anteprime adattive al rapporto d'aspetto dell'immagine, con limiti coerenti con lo spazio disponibile.
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
