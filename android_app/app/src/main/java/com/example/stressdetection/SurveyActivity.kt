package com.example.stressdetection

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SurveyActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var btnSubmit: Button
    
    // PSS-10 Questions
    // Questions 4, 5, 7, 8 are positively stated (reverse coding needed)
    private val questions = listOf(
        "In the last month, how often have you been upset because of something that happened unexpectedly?",
        "In the last month, how often have you felt that you were unable to control the important things in your life?",
        "In the last month, how often have you felt nervous and stressed?",
        "In the last month, how often have you felt confident about your ability to handle your personal problems?", // Reverse
        "In the last month, how often have you felt that things were going your way?", // Reverse
        "In the last month, how often have you found that you could not cope with all the things that you had to do?",
        "In the last month, how often have you been able to control irritations in your life?", // Reverse
        "In the last month, how often have you felt that you were on top of things?", // Reverse
        "In the last month, how often have you been angered because of things that happened that were outside of your control?",
        "In the last month, how often have you felt difficulties were piling up so high that you could not overcome them?"
    )
    
    private val reverseCodedIndices = setOf(3, 4, 6, 7) // 0-indexed
    private val answers = IntArray(10) { -1 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_survey)

        container = findViewById(R.id.survey_container)
        btnSubmit = findViewById(R.id.btn_submit_survey)

        setupQuestions()
        
        btnSubmit.setOnClickListener {
            submitSurvey()
        }
    }

    private fun setupQuestions() {
        val options = listOf("Never", "Almost Never", "Sometimes", "Fairly Often", "Very Often")
        
        for ((index, question) in questions.withIndex()) {
            val qView = layoutInflater.inflate(R.layout.item_survey_question, container, false)
            
            val tvQuestion = qView.findViewById<TextView>(R.id.tv_question)
            val rgOptions = qView.findViewById<RadioGroup>(R.id.rg_options)
            
            tvQuestion.text = "${index + 1}. $question"
            
            for ((optIndex, option) in options.withIndex()) {
                val rb = RadioButton(this)
                rb.text = option
                rb.id = optIndex 
                rgOptions.addView(rb)
            }
            
            rgOptions.setOnCheckedChangeListener { _, checkedId ->
                answers[index] = checkedId // Stores 0-4
            }
            
            container.addView(qView)
        }
    }

    private fun submitSurvey() {
        if (answers.contains(-1)) {
            Toast.makeText(this, "Please answer all questions", Toast.LENGTH_SHORT).show()
            return
        }

        var totalScore = 0
        for (i in answers.indices) {
            var score = answers[i]
            if (reverseCodedIndices.contains(i)) {
                score = 4 - score
            }
            totalScore += score
        }
        
        // PSS-10 Classification
        // 0-13: Low stress
        // 14-26: Moderate stress
        // 27-40: High stress
        
        val stressLevel = when {
            totalScore <= 13 -> "Low Stress"
            totalScore <= 26 -> "Moderate Stress"
            else -> "High Stress"
        }

        Toast.makeText(this, "Score: $totalScore ($stressLevel)", Toast.LENGTH_LONG).show()
        
        // Save result
        val prefs = getSharedPreferences("stress_history", Context.MODE_PRIVATE)
        prefs.edit().putInt("last_pss_score", totalScore).apply()
        
        finish()
    }
}
