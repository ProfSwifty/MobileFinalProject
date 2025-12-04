package com.lmccallum.groupfinalproject

import android.annotation.SuppressLint
import android.content.Intent
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
    private var currentTranslation: String? = null
    private var currentCardName: String? = null
    private var currentCardType: String? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranslationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupLanguageSpinner()

        if (savedInstanceState != null)
            restoreState(savedInstanceState)
         else
         {
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

            binding.tvTranslationResult.text = "Translating..."

            extractTextFromBitMap(bitmap) { extractedText ->
                if (extractedText.isBlank())
                {
                    showError("No Text detected.")
                    return@extractTextFromBitMap
                }

                detectSourceLanguage(extractedText) { sourceLang ->
                    if (sourceLang == null)
                    {
                        showError("Problem detecting Language.")
                        return@detectSourceLanguage
                    }

                    translateText(extractedText, sourceLang, languageCode)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        //Save current state
        outState.putString("currentTranslation", currentTranslation)
        outState.putString("currentCardName", currentCardName)
        outState.putString("currentCardType", currentCardType)
        outState.putString("currentPdfPath", currentPdfFile?.absolutePath)

        //Save spinner position
        outState.putInt("spinnerPosition", binding.LanguageSpinner.selectedItemPosition)
    }

    private fun restoreState(savedInstanceState: Bundle) {
        //Restore translation text
        currentTranslation = savedInstanceState.getString("currentTranslation")
        currentTranslation?.let {
            binding.tvTranslationResult.text = it
        }

        //Restore card info
        currentCardName = savedInstanceState.getString("currentCardName")
        currentCardType = savedInstanceState.getString("currentCardType")

        currentCardName?.let {
            binding.tvCardName.text = it
        }

        currentCardType?.let {
            binding.tvCardType.text = it
        }

        //Restore PDF file
        val pdfPath = savedInstanceState.getString("currentPdfPath")
        if (pdfPath != null) {
            val pdfFile = File(pdfPath)
            if (pdfFile.exists()) {
                currentPdfFile = pdfFile
                displayCardFromPdf(pdfFile)
            }
        }

        //Restore spinner position
        val spinnerPosition = savedInstanceState.getInt("spinnerPosition", 0)
        binding.LanguageSpinner.setSelection(spinnerPosition)
    }

    @SuppressLint("SetTextI18n")
    private fun displayCardFromPdf(pdfFile: File) {
        try {
            // Extract and display the image from PDF
            val bitmap = extractFirstPageAsBitmap(pdfFile)
            if (bitmap != null) {
                binding.ivCardDisplay.visibility = android.view.View.VISIBLE
                Glide.with(this)
                    .load(bitmap)
                    .into(binding.ivCardDisplay)

                //Save current card info for state restoration
                if (pdfFile.name.startsWith("search_"))
                {
                    val cardName = extractCardNameFromFilename(pdfFile.name)
                    currentCardName = "Card: $cardName"
                    currentCardType = "Ready for translation"
                }
                else
                {
                    currentCardName = "Scanned Card"
                    currentCardType = "File: ${pdfFile.name}"
                }
            }
            else
                binding.ivCardDisplay.visibility = android.view.View.GONE


        } catch (e: Exception) {
            e.printStackTrace()
            currentCardName = "Error displaying card"
            currentCardType = "Could not load PDF image"
            binding.ivCardDisplay.visibility = android.view.View.GONE
        }

        currentCardName?.let { binding.tvCardName.text = it }
        currentCardType?.let { binding.tvCardType.text = it }
    }

    private fun translateText(text: String, sourceLanguage: String, targetLanguage: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        val formatted = formatTranslatedText(translated)

                        currentTranslation = formatted

                        binding.tvTranslationResult.text = formatted
                    }
                    .addOnFailureListener {
                        showError("Problem with translation.")
                    }
            }
            .addOnFailureListener {
                showError("Failed to download translation model.")
            }
    }

    private fun formatTranslatedText(text: String): String {
        val lines = text.split("\n")

        if (lines.size > 1)
        {
            return lines.joinToString("\n") { line ->
                line.trim()
                    .replace("  ", " ")
                    .replace(" .", ".")
                    .replace(" ,", ",")
            }
        }

        var formattedText = text.trim()

        val namePatterns = listOf(
            Regex("""^(\p{L}+)\s+(Token\s+Creature)"""),
            Regex("""^(\p{L}+)\s+(Creature)"""),
            Regex("""^([A-ZÀ-ÿ][a-zà-ÿ]+)\s+""")
        )

        for (pattern in namePatterns) {
            val match = pattern.find(formattedText)
            if (match != null && match.groups.size > 1)
            {
                val name = match.groupValues[1]
                val rest = formattedText.substring(name.length).trim()
                formattedText = "$name\n$rest"
                break
            }
        }

        formattedText = formattedText.replace(Regex("""\s+(\d+)\s+"""), "\n\$1\n")

        val abilities = listOf("Protection", "Haste", "Flanking", "Flying", "Trample", "Vigilance")
        for (ability in abilities) {
            if (formattedText.contains(ability) && !formattedText.contains("\n$ability"))
                formattedText = formattedText.replace("$ability ", "\n$ability ")
        }

        formattedText = formattedText.replace(Regex("""\((.*?)\)""")) { match ->
            val inside = match.groupValues[1]
            if (inside.length > 30)
                "\n(${inside.replace(". ", ".\n").replace("; ", ";\n")})\n"
             else
                "\n($inside)\n"

        }

        formattedText = formattedText.replace(Regex("""(TSR|M\d+|P\d+)[: ].*?(\d+/\d+)"""), "\n\$0")
        formattedText = formattedText.replace(Regex("""\s+(\d+/\d+)\s*$"""), "\n\n\$1")

        return formattedText.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun setupClickListeners() {
        binding.btnSelectFile.setOnClickListener {
            showFileSelectionDialog()
        }

        binding.goBackButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupLanguageSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.languages,
            android.R.layout.simple_spinner_item
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.LanguageSpinner.adapter = adapter
    }

    private fun getMLkitLanguage(selection:String):String {
        return when (selection) {
            "English" -> TranslateLanguage.ENGLISH
            "French" -> TranslateLanguage.FRENCH
            "Spanish" -> TranslateLanguage.SPANISH
            else -> TranslateLanguage.ENGLISH
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

                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
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

    private fun extractTextFromBitMap(bitmap: Bitmap, callback: (String) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { result -> callback(result.text) }
            .addOnFailureListener { showError("Failed to read text.") }
    }

    private fun detectSourceLanguage(text: String, callback: (String?) -> Unit) {
        val languageDetector = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()

        languageDetector.identifyLanguage(text)
            .addOnSuccessListener { langCode ->
                if (langCode == "und")
                    callback(null)
                 else
                    callback(langCode)

            }
            .addOnFailureListener {
                callback(null)
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
                currentPdfFile = file
                displayCardFromPdf(file)
                binding.tvTranslationResult.text = "Select 'Translate' to begin translation"
                currentTranslation = null // Clear previous translation
            }
            .setNegativeButton("Delete") { _, _ ->
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
        if (file.delete())
        {
            if (currentPdfFile?.absolutePath == file.absolutePath)
            {
                currentPdfFile = null
                currentTranslation = null
                currentCardName = null
                currentCardType = null
                binding.ivCardDisplay.visibility = android.view.View.GONE
                binding.tvCardName.text = "No file selected"
                binding.tvCardType.text = "Select a file to translate"
                binding.tvTranslationResult.text = "Select 'Translate' to begin translation"
            }

            Toast.makeText(
                this,
                "File deleted successfully",
                Toast.LENGTH_SHORT
            ).show()

            showFileSelectionDialog()
        } else
            showError("Failed to delete file")

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