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

        binding.tvCardName.text = card.name
        binding.tvManaCost.text = card.mana_cost ?: ""
        binding.tvType.text = card.type_line
        binding.tvCardText.text = card.oracle_text ?: "No card text"
        binding.tvFlavorText.text = card.flavor_text ?: ""

        if (!card.power.isNullOrEmpty() && !card.toughness.isNullOrEmpty())
        {
            binding.tvPowerToughness.text = "${card.power}/${card.toughness}"
            binding.tvPowerToughness.visibility = View.VISIBLE
        } else
            binding.tvPowerToughness.visibility = View.GONE


        binding.tvError.visibility = View.GONE
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