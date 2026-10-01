package com.stresssense.app.presentation.ui.questionnaire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.domain.usecases.SaveScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the PSS-10 Questionnaire screen.
 * Handles questionnaire navigation, answer storage, PSS scoring calculations,
 * and saving results to the database.
 */
@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val saveScanUseCase: SaveScanUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionnaireUiState())
    val uiState: StateFlow<QuestionnaireUiState> = _uiState.asStateFlow()

    val questions = listOf(
        "In the last month, how often have you been upset because of something that happened unexpectedly?",
        "In the last month, how often have you felt that you were unable to control the important things in your life?",
        "In the last month, how often have you felt nervous and stressed?",
        "In the last month, how often have you felt confident about your ability to handle your personal problems?",
        "In the last month, how often have you felt that things were going your way?",
        "In the last month, how often have you found that you could not cope with all the things that you had to do?",
        "In the last month, how often have you been able to control irritations in your life?",
        "In the last month, how often have you felt that you were on top of things?",
        "In the last month, how often have you been angered because of things that happened that were outside of your control?",
        "In the last month, how often have you felt difficulties were piling up so high that you could not overcome them?"
    )

    val options = listOf(
        "Never",
        "Almost Never",
        "Sometimes",
        "Fairly Often",
        "Very Often"
    )

    private val reverseCodedIndices = setOf(3, 4, 6, 7) // 0-indexed: Q4, Q5, Q7, Q8

    fun selectOption(optionIndex: Int) {
        _uiState.update { state ->
            val newAnswers = state.answers.toMutableMap()
            newAnswers[state.currentQuestionIndex] = optionIndex
            state.copy(answers = newAnswers)
        }
    }

    fun nextQuestion() {
        _uiState.update { state ->
            if (state.currentQuestionIndex < questions.size - 1) {
                state.copy(currentQuestionIndex = state.currentQuestionIndex + 1)
            } else {
                state
            }
        }
    }

    fun previousQuestion() {
        _uiState.update { state ->
            if (state.currentQuestionIndex > 0) {
                state.copy(currentQuestionIndex = state.currentQuestionIndex - 1)
            } else {
                state
            }
        }
    }

    fun submitSurvey() {
        val state = _uiState.value
        if (state.answers.size < questions.size) return

        var rawScore = 0
        for (i in questions.indices) {
            val answer = state.answers[i] ?: 0
            val score = if (reverseCodedIndices.contains(i)) {
                4 - answer
            } else {
                answer
            }
            rawScore += score
        }

        // PSS-10 Classification:
        // 0-13: Low stress
        // 14-26: Moderate stress
        // 27-40: High stress
        val stressLevel = when {
            rawScore <= 13 -> "Low Stress"
            rawScore <= 26 -> "Moderate Stress"
            else -> "High Stress"
        }

        // Map PSS 0-40 scale to 0-100 scale (multiply by 2.5)
        val finalScore = (rawScore * 2.5).toInt().coerceIn(0, 100)

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val scan = StressScan(
                timestamp = System.currentTimeMillis(),
                finalScore = finalScore,
                scanType = "QUESTIONNAIRE",
                hrvScore = rawScore // Persist the raw PSS-10 score (out of 40) in hrvScore field for history details
            )
            saveScanUseCase(scan)
            _uiState.update { 
                it.copy(
                    isSaving = false, 
                    showResultDialog = true, 
                    finalScore = finalScore, 
                    rawScore = rawScore, 
                    stressLevel = stressLevel
                ) 
            }
        }
    }

    fun dismissResultDialog() {
        _uiState.update { it.copy(showResultDialog = false) }
    }
}

data class QuestionnaireUiState(
    val currentQuestionIndex: Int = 0,
    val answers: Map<Int, Int> = emptyMap(), // Maps question index -> selected option index (0..4)
    val isSaving: Boolean = false,
    val showResultDialog: Boolean = false,
    val finalScore: Int = 0,
    val rawScore: Int = 0,
    val stressLevel: String = ""
)
