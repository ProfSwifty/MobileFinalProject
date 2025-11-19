package com.lmccallum.groupfinalproject

import android.content.Intent
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.lmccallum.groupfinalproject.databinding.ActivityMainBinding
import com.lmccallum.groupfinalproject.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //MVVM - Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        // Set app description
        binding.tvAppDescription.text = viewModel.appDescription

        // Display team members
        displayTeamMembers()
    }


    //Displays our team members
    private fun displayTeamMembers() {
        val container = binding.teamMembersContainer

        viewModel.teamMembers.forEach { member ->
            val memberView = TextView(this).apply {
                text = "${member.name} - #${member.studentNumber} - ${member.section}"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 8, 0, 8)
                gravity = Gravity.CENTER
            }
            container.addView(memberView)
        }
    }


    //All the buttons for the main menu that lead to the other activities
    private fun setupClickListeners() {
        binding.btnScanner.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        }

        binding.btnTranslation.setOnClickListener {
            val intent = Intent(this, TranslationActivity::class.java)
            startActivity(intent)
        }

        binding.btnCardDetails.setOnClickListener {
            val intent = Intent(this, CardDetailActivity::class.java)
            startActivity(intent)
        }
    }
}