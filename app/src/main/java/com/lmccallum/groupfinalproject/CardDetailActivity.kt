package com.lmccallum.groupfinalproject

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.lmccallum.groupfinalproject.databinding.ActivityCardDetailBinding
import com.lmccallum.groupfinalproject.viewmodel.CardDetailViewModel
import kotlinx.coroutines.launch

class CardDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCardDetailBinding
    private lateinit var viewModel: CardDetailViewModel

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

        binding.btnTranslate.setOnClickListener {
            val intent = Intent(this, TranslationActivity::class.java)
            startActivity(intent)
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val searchText = binding.etSearch.text.toString().trim()
                if (searchText.isNotEmpty())
                {
                    viewModel.searchCard(searchText)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
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
        binding.tvManaCost.text = if (!card.mana_cost.isNullOrEmpty()) {
            "Mana Cost: ${convertManaCostToText(card.mana_cost)}"
        } else {
            "Mana Cost: None"
        }

        //Card Type
        binding.tvType.text = card.type_line

        //Card Text with readable symbols
        binding.tvCardText.text = if (!card.oracle_text.isNullOrEmpty()) {
            "Card Text:\n${convertCardTextToReadable(card.oracle_text)}"
        } else {
            "Card Text: No abilities"
        }

        //Flavor Text
        if (!card.flavor_text.isNullOrEmpty()) {
            binding.tvFlavorText.text = "Flavor Text:\n\"${card.flavor_text}\""
            binding.tvFlavorText.visibility = View.VISIBLE
        } else {
            binding.tvFlavorText.visibility = View.GONE
        }

        //Power/Toughness
        if (!card.power.isNullOrEmpty() && !card.toughness.isNullOrEmpty()) {
            binding.tvPowerToughness.text = "Power/Toughness: ${card.power}/${card.toughness}"
            binding.tvPowerToughness.visibility = View.VISIBLE
        } else {
            binding.tvPowerToughness.visibility = View.GONE
        }

        binding.tvError.visibility = View.GONE
    }

    //Function to convert mana cost symbols to readable text
    private fun convertManaCostToText(manaCost: String): String {
        val symbols = mutableListOf<String>()

        // Extract all symbols and convert them
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
        } else {
            "None"
        }
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