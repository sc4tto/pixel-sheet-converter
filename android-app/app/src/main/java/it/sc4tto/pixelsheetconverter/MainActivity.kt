package it.sc4tto.pixelsheetconverter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import it.sc4tto.pixelsheetconverter.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var sourceBitmap: Bitmap? = null
    private var conversion: ConversionResult? = null
    private var safeInsets: Insets = Insets.NONE

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else status("Permesso fotocamera negato: puoi usare la galleria.")
    }
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadImage(it) }
    }
    private val pngCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { savePng(it) }
    }
    private val xlsxCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { saveXlsx(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            safeInsets = safe
            applySafeInsets(binding.screenFlipper.displayedChild == 0)
            view.post { resizePreviews(view.width - safe.left - safe.right) }
            insets
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.metricSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ImageConverter.metrics)
        binding.metricSpinner.setSelection(2)
        binding.ditherSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ImageConverter.dithers)
        binding.ditherSpinner.setSelection(2)

        binding.navCameraButton.setOnClickListener { showScreen(0) }
        binding.navConvertButton.setOnClickListener {
            if (sourceBitmap != null) showScreen(1) else status("Scatta o importa prima un'immagine")
        }
        binding.navResultButton.setOnClickListener {
            if (conversion != null) showScreen(2) else status("Genera prima una conversione")
        }
        binding.cameraContinueButton.setOnClickListener { showScreen(1) }
        binding.gridCheck.setOnCheckedChangeListener { _, enabled ->
            binding.cameraGrid.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        showScreen(0)

        binding.captureButton.setOnClickListener { capture() }
        binding.galleryButton.setOnClickListener { galleryPicker.launch("image/*") }
        binding.cameraSwitchButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        binding.convertButton.setOnClickListener { convert() }
        binding.exportPngButton.setOnClickListener { pngCreator.launch("pixel_sheet_${System.currentTimeMillis()}.png") }
        binding.exportXlsxButton.setOnClickListener { xlsxCreator.launch("pixel_sheet_${System.currentTimeMillis()}.xlsx") }
        binding.backToCameraButton.setOnClickListener { showCamera() }
        binding.flashCheck.setOnCheckedChangeListener { _, checked ->
            imageCapture?.flashMode = if (checked) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        }
        binding.zoomSeek.setOnSeekBarChangeListener(simpleSeek { value -> camera?.cameraControl?.setLinearZoom(value / 100f) })
        binding.exposureSeek.setOnSeekBarChangeListener(simpleSeek { value ->
            val state = camera?.cameraInfo?.exposureState ?: return@simpleSeek
            if (state.isExposureCompensationSupported) camera?.cameraControl?.setExposureCompensationIndex(value + state.exposureCompensationRange.lower)
        })
        binding.cameraPreview.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val point = binding.cameraPreview.meteringPointFactory.createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                status("Messa a fuoco sul punto selezionato")
            }
            true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun simpleSeek(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) action(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun resizePreviews(safeWindowWidth: Int) {
        // Il contenuto ha 12 dp di margine per lato. La proporzione 0,70 mantiene
        // visibile la fotocamera senza occupare l'intero display verticale.
        val contentWidth = (safeWindowWidth - dp(24)).coerceAtLeast(dp(280))
        val adaptiveHeight = (contentWidth * 0.70f).roundToInt().coerceIn(dp(210), dp(320))
        binding.sourcePreview.layoutParams = binding.sourcePreview.layoutParams.apply { height = adaptiveHeight }
        binding.resultPreview.layoutParams = binding.resultPreview.layoutParams.apply { height = adaptiveHeight }
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.cameraPreview.surfaceProvider) }
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture)
                configureCameraControls()
                showCamera()
            } catch (exc: Exception) {
                status("Fotocamera non disponibile: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun configureCameraControls() {
        val state = camera?.cameraInfo?.exposureState
        if (state != null && state.isExposureCompensationSupported) {
            val range = state.exposureCompensationRange
            binding.exposureSeek.max = range.upper - range.lower
            binding.exposureSeek.progress = -range.lower
            binding.exposureSeek.isEnabled = true
            binding.cameraInfo.text = "EV ${range.lower}…${range.upper}  ·  Tocca per focus"
        } else {
            binding.exposureSeek.max = 0; binding.exposureSeek.isEnabled = false
            binding.cameraInfo.text = "EV non disponibile | tocca per focus"
        }
        binding.flashCheck.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
    }

    private fun capture() {
        val capture = imageCapture ?: return status("Fotocamera non pronta")
        binding.captureButton.isEnabled = false
        status("Acquisizione in corso...")
        val file = File.createTempFile("pixel-sheet-", ".jpg", cacheDir)
        capture.flashMode = if (binding.flashCheck.isChecked) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runOnUiThread { binding.captureButton.isEnabled = true; loadImage(Uri.fromFile(file)) }
                }
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread { binding.captureButton.isEnabled = true; status("Errore scatto: ${exception.message}") }
                }
            })
    }

    private fun loadImage(uri: Uri) {
        try {
            val decoded = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: error("Immagine non leggibile")
            val orientation = contentResolver.openInputStream(uri).use { input ->
                if (input == null) ExifInterface.ORIENTATION_NORMAL else ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            sourceBitmap = rotate(decoded, orientation)
            conversion = null
            binding.sourcePreview.setImageBitmap(sourceBitmap)
            binding.statisticsText.text = "Immagine: ${sourceBitmap!!.width} × ${sourceBitmap!!.height} px\nScegli i parametri e premi Converti."
            binding.exportPngButton.isEnabled = false; binding.exportXlsxButton.isEnabled = false
            showScreen(1)
            status("Immagine acquisita")
        } catch (exc: Exception) { status("Errore immagine: ${exc.message}") }
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun convert() {
        val source = sourceBitmap ?: return status("Scatta o importa prima un'immagine")
        val width = binding.widthInput.text.toString().toIntOrNull() ?: return status("Larghezza non valida")
        val pitch = binding.pitchInput.text.toString().replace(',', '.').toDoubleOrNull() ?: return status("Passo non valido")
        if (pitch <= 0) return status("Il passo deve essere positivo")
        binding.convertButton.isEnabled = false; binding.progressBar.progress = 0
        status("Conversione in corso...")
        cameraExecutor.execute {
            try {
                val result = ImageConverter.convert(source, width, binding.metricSpinner.selectedItem.toString(), binding.ditherSpinner.selectedItem.toString()) { value ->
                    runOnUiThread { binding.progressBar.progress = value }
                }
                conversion = result
                val total = result.width * result.height
                val report = "Risoluzione: ${result.width} × ${result.height}\n" +
                    "Dimensione: %.2f × %.2f mm\n".format(result.width * pitch, result.height * pitch) +
                    "Rosso: ${result.counts[0]} (%.1f%%)\n".format(result.counts[0] * 100.0 / total) +
                    "Verde: ${result.counts[1]} (%.1f%%)\n".format(result.counts[1] * 100.0 / total) +
                    "Blu: ${result.counts[2]} (%.1f%%)".format(result.counts[2] * 100.0 / total)
                runOnUiThread {
                    binding.resultPreview.setImageBitmap(result.bitmap)
                    binding.statisticsText.text = report
                    binding.exportPngButton.isEnabled = true; binding.exportXlsxButton.isEnabled = true
                    binding.convertButton.isEnabled = true
                    showScreen(2)
                    status("Conversione completata")
                }
            } catch (exc: Exception) {
                runOnUiThread { binding.convertButton.isEnabled = true; status("Errore conversione: ${exc.message}") }
            }
        }
    }

    private fun savePng(uri: Uri) {
        val result = conversion ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            toast("PNG salvato")
        } catch (exc: Exception) { status("Errore PNG: ${exc.message}") }
    }

    private fun saveXlsx(uri: Uri) {
        val result = conversion ?: return
        status("Esportazione XLSX...")
        cameraExecutor.execute {
            try {
                contentResolver.openOutputStream(uri)?.use { XlsxExporter.write(result, it) }
                runOnUiThread { toast("XLSX salvato"); status("Esportazione completata") }
            } catch (exc: Exception) { runOnUiThread { status("Errore XLSX: ${exc.message}") } }
        }
    }

    private fun showCamera() {
        showScreen(0)
        status("Fotocamera pronta")
    }

    private fun showScreen(index: Int) {
        binding.screenFlipper.displayedChild = index
        val cameraMode = index == 0
        binding.appHeader.visibility = if (cameraMode) View.GONE else View.VISIBLE
        binding.navigationBar.visibility = if (cameraMode) View.GONE else View.VISIBLE
        binding.statusText.visibility = if (cameraMode) View.GONE else View.VISIBLE
        WindowCompat.getInsetsController(window, binding.root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (cameraMode) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
        applySafeInsets(cameraMode)
        listOf(binding.navCameraButton, binding.navConvertButton, binding.navResultButton)
            .forEachIndexed { buttonIndex, button ->
                button.alpha = if (buttonIndex == index) 1f else 0.58f
                button.isSelected = buttonIndex == index
            }
    }

    private fun applySafeInsets(cameraMode: Boolean) {
        val safe = safeInsets
        if (cameraMode) {
            binding.rootLayout.setPadding(0, 0, 0, 0)
            binding.cameraTopBar.setPadding(dp(12) + safe.left, dp(7) + safe.top, dp(12) + safe.right, dp(7))
            binding.cameraBottomBar.setPadding(dp(12) + safe.left, dp(7), dp(12) + safe.right, dp(10) + safe.bottom)
        } else {
            val breathingRoom = dp(4)
            binding.rootLayout.setPadding(
                safe.left,
                safe.top + breathingRoom,
                safe.right,
                safe.bottom + breathingRoom,
            )
        }
    }
    private fun status(text: String) { binding.statusText.text = text }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}
