package com.lmccallum.groupfinalproject

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

class ScannerActivity : AppCompatActivity() {
    private lateinit var scannerBinding: ActivityScannerBinding
    private lateinit var scanner: GmsDocumentScanner
    private var scannedPdfUri: Uri? = null

    companion object {
        const val SCANNED_CARDS_DIR = "scanned_cards"
    }

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
        startScanner()
    }

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->

            if (result.resultCode == RESULT_OK) {
                scannerBinding.feedbackText.setText("Card scanned successfully!")
                val scanResult =
                    GmsDocumentScanningResult.fromActivityResultIntent(result.data)

                //Display the scanned image preview
                scanResult?.pages?.forEach { page ->
                    val imageUri = page.imageUri
                    Glide.with(this)
                        .load(imageUri)
                        .into(scannerBinding.cardScannedInImage)
                }

                //Save the PDF
                scanResult?.pdf?.let { pdf ->
                    scannedPdfUri = pdf.uri
                    savePdfToInternalStorage(pdf.uri)
                }
            }
            else{
                scannerBinding.feedbackText.setText("Error: Unable to scan card")
            }
        }

    private fun startScanner() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener {
                scannerBinding.feedbackText.setText("ERROR: Unable to build scanner")
            }
    }

    private fun savePdfToInternalStorage(pdfUri: Uri) {
        try {
            //SAVES TO: app's internal storage in /scanned_cards/ folder
            // ILE NAME: scan_YYYYMMDD_HHmmss.pdf (timestamped)
            //ACCESS IN TRANSLATION: Use getLatestScannedPdf() or pass file path via Intent
            val scannedDir = File(filesDir, SCANNED_CARDS_DIR)
            if (!scannedDir.exists()) {
                scannedDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "scan_${timeStamp}.pdf"
            val outputFile = File(scannedDir, fileName)

            //Copy PDF from scanner to internal storage
            val inputStream = contentResolver.openInputStream(pdfUri)
            val outputStream = FileOutputStream(outputFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            //Remove this println in final version
            println("PDF saved to: ${outputFile.absolutePath}")
            scannerBinding.feedbackText.setText("PDF saved: $fileName")

        } catch (e: Exception) {
            scannerBinding.feedbackText.setText("Error: Failed to save PDF")
        }
    }

    private fun getLatestScannedPdf(): File? {
        val scannedDir = File(filesDir, SCANNED_CARDS_DIR)
        if (!scannedDir.exists()) {
            return null
        }

        return scannedDir.listFiles { file ->
            file.isFile && file.name.endsWith(".pdf")
        }?.maxByOrNull { it.lastModified() }
    }

    private fun setupClickListeners() {
        scannerBinding.scannerButton.setOnClickListener {
            startScanner()
        }

        scannerBinding.translateCardButton.setOnClickListener {
            val latestPdf = getLatestScannedPdf()

            if (latestPdf != null && latestPdf.exists()) {
                val intent = Intent(this, TranslationActivity::class.java).apply {
                    putExtra("SCANNED_PDF_PATH", latestPdf.absolutePath)
                }
                startActivity(intent)
            } else {
                scannerBinding.feedbackText.setText("ERROR: No scanned PDF found")
            }
        }

        scannerBinding.goBackButton.setOnClickListener {
            finish()
        }
    }
}