package com.lmccallum.groupfinalproject

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.lmccallum.groupfinalproject.databinding.ActivityTranslationBinding
import java.io.File

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.*
import com.google.mlkit.vision.common.InputImage

class TranslationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTranslationBinding
    private var currentPdfFile: File? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranslationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupLanguageSpinner()
        //Check if a PDF was passed from ScannerActivity or CardDetailActivity
        val pdfPath = intent.getStringExtra("SCANNED_PDF_PATH")
        if (pdfPath != null)
        {
            val pdfFile = File(pdfPath)
            if (pdfFile.exists())
            {
                currentPdfFile = pdfFile
                displayCardFromPdf(pdfFile)
            }
        }

        binding.btnTranslate.setOnClickListener {
            val pdf = currentPdfFile
            if (pdf == null)
            {
                showError("No file selected.")
                return@setOnClickListener
            }

            val bitmap = extractFirstPageAsBitmap(pdf)
            if (bitmap == null)
            {
                showError("Unable to read PDF")
                return@setOnClickListener
            }

            val selectedLanguage = binding.LanguageSpinner.selectedItem.toString()
            val languageCode = getMLkitLanguage(selectedLanguage)

            binding.tvTranslationResult.text ="Translating..."

            extractTextFromBitMap(bitmap) {
                    extractedText -> if (extractedText.isBlank()){
                showError("No Text detected.")
                return@extractTextFromBitMap
            }

                detectSourceLanguage(extractedText) { sourceLang ->
                    if(sourceLang == null){
                        showError("Problem detecting Language.")
                        return@detectSourceLanguage
                    }

                    translateText(extractedText,sourceLang,languageCode)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectFile.setOnClickListener {
            showFileSelectionDialog()
        }

        binding.goBackButton.setOnClickListener {
            finish()
        }
    }

    private fun setupLanguageSpinner()
    {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.languages,
            android.R.layout.simple_spinner_item
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
        binding.LanguageSpinner.adapter = adapter
    }

    private fun getMLkitLanguage(selection:String):String {
        return when (selection)
        {
            "English" -> TranslateLanguage.ENGLISH
            "French" -> TranslateLanguage.FRENCH
            "Spanish" -> TranslateLanguage.SPANISH
            else -> TranslateLanguage.ENGLISH
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayCardFromPdf(pdfFile: File) {
        try {
            //Extract and display the image from PDF
            val bitmap = extractFirstPageAsBitmap(pdfFile)
            if (bitmap != null)
            {
                binding.ivCardDisplay.visibility = android.view.View.VISIBLE
                Glide.with(this)
                    .load(bitmap)
                    .into(binding.ivCardDisplay)
            } else
                binding.ivCardDisplay.visibility = android.view.View.GONE

            //Display file info
            if (pdfFile.name.startsWith("search_"))
            {
                val cardName = extractCardNameFromFilename(pdfFile.name)
                binding.tvCardName.text = "Card: $cardName"
                binding.tvCardType.text = "Ready for translation"
            }
            else
            {
                binding.tvCardName.text = "Scanned Card"
                binding.tvCardType.text = "File: ${pdfFile.name}"
            }

        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvCardName.text = "Error displaying card"
            binding.tvCardType.text = "Could not load PDF image"
            binding.ivCardDisplay.visibility = android.view.View.GONE
        }
    }

    @SuppressLint("UseKtx")
    private fun extractFirstPageAsBitmap(pdfFile: File): Bitmap? {
        var parcelFileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            parcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(parcelFileDescriptor)

            if (pdfRenderer.pageCount > 0)
            {
                page = pdfRenderer.openPage(0)

                //Create a bitmap with the same dimensions as the PDF page
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

                //Render the PDF page to the bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                return bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            page?.close()
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        }
        return null
    }

    private fun extractTextFromBitMap(bitmap: Bitmap, callback: (String) -> Unit)
    {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { result -> callback(result.text) }
            .addOnFailureListener { showError("Failed to read text.") }
    }

    private fun detectSourceLanguage(text: String, callback: (String?) -> Unit)
    {
        val languageDetector = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()

        languageDetector.identifyLanguage(text)
            .addOnSuccessListener { langCode ->
                if (langCode == "und")
                {
                    callback(null)
                }
                else
                {
                    callback(langCode)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun translateText(text: String, sourceLanguage: String, targetLanguage: String)
    {
        val options = TranslatorOptions.Builder()
            //Detect the language input
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        val formatted = translated
                            .replace(". ", ".\n\n")
                            .replace("? ", "?\n\n")
                            .replace("! ", "!\n\n")
                            .trim()

                        binding.tvTranslationResult.text = formatted
                    }
                    .addOnFailureListener {
                        showError("Problem with translation.")
                    }
            }
    }

    private fun extractCardNameFromFilename(filename: String): String {
        return filename.removePrefix("search_")
            .removeSuffix(".pdf")
            .split("_")
            .dropLast(2)
            .joinToString(" ")
            .replace("_", " ")
    }

    @SuppressLint("SetTextI18n")
    private fun showFileSelectionDialog() {
        val scannedDir = File(filesDir, ScannerActivity.SCANNED_CARDS_DIR)

        if (!scannedDir.exists())
        {
            showError("No scanned cards folder found")
            return
        }

        val pdfFiles = scannedDir.listFiles { file ->
            file.isFile && file.name.endsWith(".pdf")
        }?.sortedByDescending { it.lastModified() }

        if (pdfFiles.isNullOrEmpty())
        {
            showError("No PDF files found")
            return
        }

        val fileNames = pdfFiles.map { it.name }

        AlertDialog.Builder(this)
            .setTitle("Select Card to Translate")
            .setItems(fileNames.toTypedArray()) { _, which ->
                // Use .get() instead of [] for List<File>
                showFileOptionsDialog(pdfFiles[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun showFileOptionsDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle("File: ${file.name}")
            .setMessage("What would you like to do with this file?")
            .setPositiveButton("Select") { _, _ ->
                // Select the file
                currentPdfFile = file
                displayCardFromPdf(file)
                binding.tvTranslationResult.text = "Select 'Translate' to begin translation"
            }
            .setNegativeButton("Delete") { _, _ ->
                // Confirm deletion
                AlertDialog.Builder(this)
                    .setTitle("Confirm Delete")
                    .setMessage("Are you sure you want to delete '${file.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteFileAndRefresh(file)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun deleteFileAndRefresh(file: File) {
        if (file.delete()) {
            // If the current file is deleted, clear the display
            if (currentPdfFile?.absolutePath == file.absolutePath) {
                currentPdfFile = null
                binding.ivCardDisplay.visibility = android.view.View.GONE
                binding.tvCardName.text = "No file selected"
                binding.tvCardType.text = "Select a file to translate"
                binding.tvTranslationResult.text = "Select 'Translate' to begin translation"
            }

            // Show success message
            Toast.makeText(
                this,
                "File deleted successfully",
                Toast.LENGTH_SHORT
            ).show()

            // Refresh the file selection dialog
            showFileSelectionDialog()
        } else {
            showError("Failed to delete file")
        }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}