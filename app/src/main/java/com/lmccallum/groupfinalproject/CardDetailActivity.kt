package com.lmccallum.groupfinalproject

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.lmccallum.groupfinalproject.databinding.ActivityCardDetailBinding
import com.lmccallum.groupfinalproject.viewmodel.CardDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CardDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCardDetailBinding
    private lateinit var viewModel: CardDetailViewModel

    companion object {
        const val SCANNED_CARDS_DIR = "scanned_cards"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCardDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CardDetailViewModel::class.java]

        setupUI()
        observeViewModel()

        val scannedText = intent.getStringExtra("SCANNED_TEXT")
        scannedText?.let {
            viewModel.searchCardFromScan(it)
        }
    }

    private fun setupUI() {
        binding.btnSearch.setOnClickListener {
            val searchText = binding.etSearch.text.toString().trim()
            if (searchText.isNotEmpty())
                viewModel.searchCard(searchText)
        }

        binding.btnGoBack.setOnClickListener {
            finish()
        }

        //Translate button saves card IMAGE as PDF
        binding.btnTranslate.setOnClickListener {
            viewModel.currentCard.value?.let { card ->
                saveCardAsPdf(card)
            }
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val searchText = binding.etSearch.text.toString().trim()
                if (searchText.isNotEmpty())
                {
                    viewModel.searchCard(searchText)
                    true
                }
                else
                    false

            }
            else
                false

        }
    }

    //Save card IMAGE as PDF
    @SuppressLint("SetTextI18n")
    private fun saveCardAsPdf(card: com.lmccallum.groupfinalproject.model.ScryfallCard) {
        lifecycleScope.launch {
            try {
                //Create scanned_cards directory if it doesn't exist
                val scannedDir = File(filesDir, SCANNED_CARDS_DIR)
                if (!scannedDir.exists()) scannedDir.mkdirs()

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val safeCardName = card.name.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val fileName = "search_${safeCardName}_$timeStamp.pdf"
                val outputFile = File(scannedDir, fileName)

                card.image_uris?.get("normal")?.let { imageUrl ->
                    downloadAndCreatePdf(imageUrl, outputFile, card.name)
                } ?: run {
                    createCardInfoPdf(card, outputFile)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                binding.tvError.text = "Failed to save card as PDF"
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    //Download card image and create PDF
    @SuppressLint("SetTextI18n")
    private fun downloadAndCreatePdf(imageUrl: String, outputFile: File, cardName: String) {
        lifecycleScope.launch {
            try {
                // Download the card image
                val bitmap = downloadImage(imageUrl)
                if (bitmap != null)
                {
                    //Create PDF with the card image
                    createImagePdf(bitmap, outputFile, cardName)

                    val intent = Intent(this@CardDetailActivity, TranslationActivity::class.java).apply {
                        putExtra("SCANNED_PDF_PATH", outputFile.absolutePath)
                    }
                    startActivity(intent)
                }
                else
                {
                    binding.tvError.text = "Failed to download card image"
                    binding.tvError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.tvError.text = "Failed to create PDF"
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    //Download image from URL
    private suspend fun downloadImage(imageUrl: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val inputStream = connection.inputStream
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                null
            }
        }
    }

    //Create PDF with card image
    private fun createImagePdf(bitmap: Bitmap, outputFile: File, cardName: String) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        //Draw the card image on the entire PDF page
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        document.finishPage(page)

        //Save PDF
        document.writeTo(FileOutputStream(outputFile))
        document.close()
    }

    //A fallback if no image is available
    private fun createCardInfoPdf(card: com.lmccallum.groupfinalproject.model.ScryfallCard, outputFile: File) {
        val document = PdfDocument()

        val pageInfo = PdfDocument.PageInfo.Builder(300, 400, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = android.graphics.Paint()
        paint.textSize = 12f

        var yPos = 20f
        canvas.drawText("Card: ${card.name}", 10f, yPos, paint)
        yPos += 20f
        canvas.drawText("Mana Cost: ${card.mana_cost ?: "None"}", 10f, yPos, paint)
        yPos += 20f
        canvas.drawText("Type: ${card.type_line}", 10f, yPos, paint)
        yPos += 20f
        canvas.drawText("Text: ${card.oracle_text ?: "No abilities"}", 10f, yPos, paint)

        document.finishPage(page)
        document.writeTo(FileOutputStream(outputFile))
        document.close()

        val intent = Intent(this, TranslationActivity::class.java).apply {
            putExtra("SCANNED_PDF_PATH", outputFile.absolutePath)
        }
        startActivity(intent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.currentCard.collect { card ->
                card?.let {
                    displayCard(it)
                } ?: run {
                    hideCard()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnSearch.isEnabled = !isLoading
                binding.etSearch.isEnabled = !isLoading
            }
        }

        lifecycleScope.launch {
            viewModel.searchError.collect { error ->
                error?.let {
                    binding.tvError.text = it
                    binding.tvError.visibility = View.VISIBLE
                } ?: run {
                    binding.tvError.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayCard(card: com.lmccallum.groupfinalproject.model.ScryfallCard) {
        binding.btnTranslate.visibility = View.VISIBLE

        card.image_uris?.get("normal")?.let { imageUrl ->
            binding.ivCardImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
                .into(binding.ivCardImage)
        } ?: run {
            binding.ivCardImage.visibility = View.GONE
        }

        //Card Name
        binding.tvCardName.text = card.name

        //Mana Cost
        binding.tvManaCost.text = if (!card.mana_cost.isNullOrEmpty())
            "Mana Cost: ${convertManaCostToText(card.mana_cost)}"
        else
            "Mana Cost: None"


        //Card Type
        binding.tvType.text = card.type_line

        //Card Text with readable symbols
        binding.tvCardText.text = if (!card.oracle_text.isNullOrEmpty())
            "Card Text:\n${convertCardTextToReadable(card.oracle_text)}"
        else
            "Card Text: No abilities"


        //Flavor Text
        if (!card.flavor_text.isNullOrEmpty())
        {
            binding.tvFlavorText.text = "Flavor Text:\n\"${card.flavor_text}\""
            binding.tvFlavorText.visibility = View.VISIBLE
        }
        else
            binding.tvFlavorText.visibility = View.GONE


        //Power/Toughness
        if (!card.power.isNullOrEmpty() && !card.toughness.isNullOrEmpty())
        {
            binding.tvPowerToughness.text = "Power/Toughness: ${card.power}/${card.toughness}"
            binding.tvPowerToughness.visibility = View.VISIBLE
        }
        else
            binding.tvPowerToughness.visibility = View.GONE

        binding.tvError.visibility = View.GONE
    }

    //Function to convert mana cost symbols to readable text
    private fun convertManaCostToText(manaCost: String): String {
        val symbols = mutableListOf<String>()

        //Extract all symbols and convert them
        val regex = Regex("\\{([^}]+)\\}")
        regex.findAll(manaCost).forEach { match ->
            val symbol = match.groupValues[1]
            val readableSymbol = when (symbol) {
                "T" -> "Tap"
                "Q" -> "Untap"
                "W" -> "White"
                "U" -> "Blue"
                "B" -> "Black"
                "R" -> "Red"
                "G" -> "Green"
                "C" -> "Colorless"
                "X" -> "X"
                "Y" -> "Y"
                "Z" -> "Z"
                "S" -> "Snow"
                "P" -> "Phyrexian"
                "W/U" -> "White/Blue"
                "W/B" -> "White/Black"
                "U/B" -> "Blue/Black"
                "U/R" -> "Blue/Red"
                "B/R" -> "Black/Red"
                "B/G" -> "Black/Green"
                "R/G" -> "Red/Green"
                "R/W" -> "Red/White"
                "G/W" -> "Green/White"
                "G/U" -> "Green/Blue"
                "2/W" -> "Two or White"
                "2/U" -> "Two or Blue"
                "2/B" -> "Two or Black"
                "2/R" -> "Two or Red"
                "2/G" -> "Two or Green"
                else -> if (symbol.matches(Regex("\\d+"))) symbol else symbol
            }
            symbols.add(readableSymbol)
        }

        //Group and count identical symbols, then join with commas
        val groupedSymbols = symbols.groupBy { it }
            .map { (symbol, instances) ->
                if (symbol.matches(Regex("\\d+"))) {
                    val total = instances.sumOf { it.toInt() }
                    if (total > 0) total.toString() else null
                } else {
                    when (instances.size) {
                        1 -> symbol
                        else -> "${instances.size} $symbol"
                    }
                }
            }
            .filterNotNull()

        return if (groupedSymbols.isNotEmpty()) {
            groupedSymbols.joinToString(", ")
        }
        else
            "None"
    }

    //Function to convert card text symbols to readable text
    private fun convertCardTextToReadable(cardText: String): String {
        return cardText.replace(Regex("\\{([^}]+)\\}")) { match ->
            when (val symbol = match.groupValues[1]) {
                "T" -> "Tap"
                "Q" -> "Untap"
                "W" -> "White"
                "U" -> "Blue"
                "B" -> "Black"
                "R" -> "Red"
                "G" -> "Green"
                "C" -> "Colorless"
                "X" -> "X"
                "S" -> "Snow"
                "P" -> "Phyrexian"
                else -> if (symbol.matches(Regex("\\d+"))) symbol else symbol
            }
        }
    }

    private fun hideCard()
    {
        binding.ivCardImage.visibility = View.GONE
        binding.tvCardName.text = ""
        binding.tvManaCost.text = ""
        binding.tvType.text = ""
        binding.tvCardText.text = ""
        binding.tvFlavorText.text = ""
        binding.tvPowerToughness.visibility = View.GONE
        binding.btnTranslate.visibility = View.GONE
    }
}