package com.example.stressdetection

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.view.Gravity

/**
 * Displays mental health resources and helpline numbers.
 * Always accessible from main navigation per spec requirement.
 */
class ResourcesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F7"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val scrollView = android.widget.ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F5F5F7"))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(40))
        }

        // Back button
        val backRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = TextView(this).apply {
            text = "← Back"
            setTextColor(Color.parseColor("#0066CC"))
            textSize = 16f
            setPadding(0, dp(8), dp(16), dp(16))
            setOnClickListener { finish() }
        }
        backRow.addView(backBtn)
        content.addView(backRow)

        // Title
        content.addView(TextView(this).apply {
            text = "🫂 Mental Health Resources"
            setTextColor(Color.parseColor("#1D1D1F"))
            textSize = 26f
            setPadding(0, dp(8), 0, dp(8))
        })

        // Disclaimer
        content.addView(TextView(this).apply {
            text = "This app provides an experimental, non-clinical estimate of stress based on phone usage patterns. It is not a medical diagnosis. If you're struggling, please reach out to a counselor or mental health professional."
            setTextColor(Color.parseColor("#7A7A7A"))
            textSize = 13f
            setPadding(0, 0, 0, dp(24))
            setLineSpacing(4f, 1.3f)
        })

        // Crisis helplines
        addSectionTitle(content, "🆘 Crisis Helplines")

        addResourceCard(content,
            title = "National Suicide Prevention Lifeline",
            subtitle = "Available 24/7 for emotional distress",
            phone = "988",
            emoji = "📞"
        )

        addResourceCard(content,
            title = "Crisis Text Line",
            subtitle = "Text HOME to 741741 for crisis support",
            phone = null,
            emoji = "💬"
        )

        addResourceCard(content,
            title = "SAMHSA National Helpline",
            subtitle = "Free, confidential treatment referral",
            phone = "1-800-662-4357",
            emoji = "🏥"
        )

        addResourceCard(content,
            title = "National Alliance on Mental Illness",
            subtitle = "NAMI Helpline for information & support",
            phone = "1-800-950-6264",
            emoji = "🤝"
        )

        // Student-specific
        addSectionTitle(content, "🎓 Student Resources")

        addResourceCard(content,
            title = "Your Campus Counseling Center",
            subtitle = "Most universities offer free counseling — check your school's website",
            phone = null,
            emoji = "🏛️"
        )

        addResourceCard(content,
            title = "JED Foundation",
            subtitle = "Emotional health resources for teens & young adults",
            phone = null,
            emoji = "📘",
            url = "https://www.jedfoundation.org"
        )

        addResourceCard(content,
            title = "Active Minds",
            subtitle = "Student-led mental health advocacy organization",
            phone = null,
            emoji = "🧠",
            url = "https://www.activeminds.org"
        )

        // Self-help tips
        addSectionTitle(content, "💡 Quick Stress-Relief Tips")

        val tips = listOf(
            "🌬️ Try box breathing: Inhale 4 sec → Hold 4 sec → Exhale 4 sec → Hold 4 sec",
            "🚶 Take a 10-minute walk — even a short walk reduces cortisol levels",
            "📵 Set a 30-minute \"digital sunset\" before bed — no screens",
            "📝 Write down 3 things you're grateful for each morning",
            "🎵 Listen to calming music or nature sounds for 5 minutes",
            "💤 Aim for 7-9 hours of sleep — sleep deprivation amplifies stress"
        )

        for (tip in tips) {
            content.addView(TextView(this).apply {
                text = tip
                setTextColor(Color.parseColor("#333333"))
                textSize = 14f
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setLineSpacing(2f, 1.2f)
            })
        }

        // International
        addSectionTitle(content, "🌍 International Helplines")

        content.addView(TextView(this).apply {
            text = "• UK: Samaritans — 116 123\n• India: iCall — 9152987821\n• Canada: Crisis Services — 1-833-456-4566\n• Australia: Lifeline — 13 11 14\n• EU: Emergency — 112"
            setTextColor(Color.parseColor("#7A7A7A"))
            textSize = 13f
            setPadding(dp(16), dp(8), dp(16), dp(24))
            setLineSpacing(6f, 1.3f)
        })

        scrollView.addView(content)
        setContentView(scrollView)
    }

    private fun addSectionTitle(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#1D1D1F"))
            textSize = 18f
            setPadding(0, dp(20), 0, dp(12))
        })
    }

    private fun addResourceCard(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        phone: String?,
        emoji: String,
        url: String? = null
    ) {
        val card = CardView(this).apply {
            setCardBackgroundColor(Color.WHITE)
            radius = dp(16).toFloat()
            cardElevation = dp(4).toFloat()
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            layoutParams = params
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER_VERTICAL
        }

        cardContent.addView(TextView(this).apply {
            text = emoji
            textSize = 28f
            setPadding(0, 0, dp(16), 0)
        })

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textCol.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#1D1D1F"))
            textSize = 15f
        })

        textCol.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#7A7A7A"))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })

        if (phone != null) {
            textCol.addView(TextView(this).apply {
                text = "📞 $phone"
                setTextColor(Color.parseColor("#0066CC"))
                textSize = 14f
                setPadding(0, dp(8), 0, 0)
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    } catch (e: Exception) { /* no dialer */ }
                }
            })
        }

        if (url != null) {
            textCol.addView(TextView(this).apply {
                text = "🔗 Visit Website"
                setTextColor(Color.parseColor("#0066CC"))
                textSize = 13f
                setPadding(0, dp(6), 0, 0)
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) { /* no browser */ }
                }
            })
        }

        cardContent.addView(textCol)
        card.addView(cardContent)
        parent.addView(card)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
