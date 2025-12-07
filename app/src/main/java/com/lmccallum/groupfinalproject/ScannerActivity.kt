package com.lmccallum.groupfinalproject

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.documentscanner.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.lmccallum.groupfinalproject.databinding.ActivityScannerBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

class ScannerActivity : AppCompatActivity() {
    private lateinit var scannerBinding: ActivityScannerBinding
    private lateinit var scanner: GmsDocumentScanner
    private var scannedPdfUri: Uri? = null
    private var lastScannedFileName: String? = null
    private var lastScannedImageUri: Uri? = null
    private var feedbackText: String? = null

    companion object {
        const val SCANNED_CARDS_DIR = "scanned_cards"
        private const val STATE_SCANNED_URI = "scanned_uri"
        private const val STATE_FILENAME = "scanned_filename"
        private const val STATE_IMAGE_URI = "scanned_image_uri"
        private const val STATE_FEEDBACK = "feedback_text"
    }

    //Method to initialize the document scanner and restore states
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scannerBinding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(scannerBinding.root)

        val scannerOptions = Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .build()
        scanner = GmsDocumentScanning.getClient(scannerOptions)

        setupClickListeners()

        if (savedInstanceState != null)
            restoreState(savedInstanceState)
         else
            startScanner()
    }

    //Method to save the state of the instance
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        scannedPdfUri?.let { uri ->
            outState.putString(STATE_SCANNED_URI, uri.toString())
        }

        outState.putString(STATE_FILENAME, lastScannedFileName)
        outState.putString(STATE_FEEDBACK, scannerBinding.feedbackText.text.toString())

        lastScannedImageUri?.let { uri ->
            outState.putString(STATE_IMAGE_URI, uri.toString())
        }
    }

    //Method to restore the state of the instance if switched from portrait -> landscape or vice versa
    @SuppressLint("SetTextI18n")
    private fun restoreState(savedInstanceState: Bundle) {
        val savedUri = savedInstanceState.getString(STATE_SCANNED_URI)
        savedUri?.let {
            scannedPdfUri = it.toUri()
        }

        lastScannedFileName = savedInstanceState.getString(STATE_FILENAME)

        feedbackText = savedInstanceState.getString(STATE_FEEDBACK)
        feedbackText?.let {
            scannerBinding.feedbackText.text = it
        }

        val savedImageUri = savedInstanceState.getString(STATE_IMAGE_URI)
        savedImageUri?.let {
            lastScannedImageUri = it.toUri()
            Glide.with(this)
                .load(lastScannedImageUri)
                .into(scannerBinding.cardScannedInImage)
        }

        lastScannedFileName?.let { fileName ->
            if (scannerBinding.feedbackText.text.isNullOrEmpty())
                scannerBinding.feedbackText.text = "PDF saved: $fileName"
        }
    }

    //Method to handle the scanner result
    @SuppressLint("SetTextI18n")
    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->

            if (result.resultCode == RESULT_OK)
            {
                scannerBinding.feedbackText.text = "Card scanned successfully!"
                feedbackText = "Card scanned successfully!"

                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

                scanResult?.pages?.forEach { page ->
                    val imageUri = page.imageUri
                    lastScannedImageUri = imageUri
                    Glide.with(this)
                        .load(imageUri)
                        .into(scannerBinding.cardScannedInImage)
                }

                scanResult?.pdf?.let { pdf ->
                    scannedPdfUri = pdf.uri
                    savePdfToInternalStorage(pdf.uri)
                }
            }
            else
            {
                scannerBinding.feedbackText.text = "Error: Unable to scan card"
                feedbackText = "Error: Unable to scan card"
            }
        }

    //Method to start the scanner
    @SuppressLint("SetTextI18n")
    private fun startScanner() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener {
                scannerBinding.feedbackText.text = "ERROR: Unable to build scanner"
                feedbackText = "ERROR: Unable to build scanner"
            }
    }

    //Method to save the PDF to internal storage
    @SuppressLint("SetTextI18n")
    private fun savePdfToInternalStorage(pdfUri: Uri) {
        try {
            val scannedDir = File(filesDir, SCANNED_CARDS_DIR)
            if (!scannedDir.exists())
                scannedDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "scan_${timeStamp}.pdf"
            val outputFile = File(scannedDir, fileName)

            val inputStream = contentResolver.openInputStream(pdfUri)
            val outputStream = FileOutputStream(outputFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            lastScannedFileName = fileName
            scannerBinding.feedbackText.text = "PDF saved: $fileName"
            feedbackText = "PDF saved: $fileName"

        } catch (e: Exception) {
            scannerBinding.feedbackText.text = "Error: Failed to save PDF"
            feedbackText = "Error: Failed to save PDF"
        }
    }

    //Method to grab the latest scanned PDF
    private fun getLatestScannedPdf(): File? {
        val scannedDir = File(filesDir, SCANNED_CARDS_DIR)
        if (!scannedDir.exists())
            return null

        return scannedDir.listFiles { file ->
            file.isFile && file.name.endsWith(".pdf")
        }?.maxByOrNull { it.lastModified() }
    }

    //Method to setup the button listeners
    @SuppressLint("SetTextI18n")
    private fun setupClickListeners() {
        scannerBinding.scannerButton.setOnClickListener {
            startScanner()
        }

        scannerBinding.translateCardButton.setOnClickListener {
            val pdfToUse = if (lastScannedFileName != null) {
                val scannedDir = File(filesDir, SCANNED_CARDS_DIR)
                if (scannedDir.exists()) {

                    val file = File(scannedDir, lastScannedFileName!!)
                    if (file.exists()) file else getLatestScannedPdf()
                }
                else
                    null
            }
            else
                getLatestScannedPdf()

            if (pdfToUse != null && pdfToUse.exists())
            {
                val intent = Intent(this, TranslationActivity::class.java).apply {
                    putExtra("SCANNED_PDF_PATH", pdfToUse.absolutePath)
                }
                startActivity(intent)
            }
            else
                scannerBinding.feedbackText.text = "ERROR: No scanned PDF found"
        }

        scannerBinding.goBackButton.setOnClickListener {
            finish()
        }
    }
}