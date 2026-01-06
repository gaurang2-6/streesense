package com.example.stressdetection

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnSkip: TextView
    private lateinit var indicator1: View
    private lateinit var indicator2: View
    private lateinit var indicator3: View

    private val onboardingItems = listOf(
        OnboardingItem(
            "🧠",
            "Understand Your Stress",
            "Our AI analyzes your typing patterns, touch behavior, and app usage to detect stress levels in real-time."
        ),
        OnboardingItem(
            "📊",
            "Track Your Patterns",
            "View daily and weekly trends to understand when you're most stressed and identify triggers."
        ),
        OnboardingItem(
            "🔒",
            "Privacy First",
            "All analysis happens on your device. Your data never leaves your phone. You're in complete control."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if already onboarded
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarded", false)) {
            navigateToMain()
            return
        }
        
        setContentView(R.layout.activity_onboarding)
        
        initViews()
        setupViewPager()
        setupClickListeners()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btn_next)
        btnSkip = findViewById(R.id.tv_skip)
        indicator1 = findViewById(R.id.indicator1)
        indicator2 = findViewById(R.id.indicator2)
        indicator3 = findViewById(R.id.indicator3)
    }

    private fun setupViewPager() {
        viewPager.adapter = OnboardingAdapter(onboardingItems)
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButton(position)
            }
        })
    }

    private fun updateIndicators(position: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.accent_purple)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)
        
        indicator1.setBackgroundColor(if (position == 0) activeColor else inactiveColor)
        indicator2.setBackgroundColor(if (position == 1) activeColor else inactiveColor)
        indicator3.setBackgroundColor(if (position == 2) activeColor else inactiveColor)
    }

    private fun updateButton(position: Int) {
        if (position == onboardingItems.size - 1) {
            btnNext.text = "Get Started"
        } else {
            btnNext.text = "Next"
        }
    }

    private fun setupClickListeners() {
        btnNext.setOnClickListener {
            if (viewPager.currentItem < onboardingItems.size - 1) {
                viewPager.currentItem = viewPager.currentItem + 1
            } else {
                completeOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarded", true)
            .apply()
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    data class OnboardingItem(
        val emoji: String,
        val title: String,
        val description: String
    )

    class OnboardingAdapter(private val items: List<OnboardingItem>) : 
        RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val emoji: TextView = view.findViewById(R.id.tv_emoji)
            val title: TextView = view.findViewById(R.id.tv_title)
            val description: TextView = view.findViewById(R.id.tv_description)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.emoji.text = item.emoji
            holder.title.text = item.title
            holder.description.text = item.description
        }

        override fun getItemCount() = items.size
    }
}
