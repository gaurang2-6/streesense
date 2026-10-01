package com.stresssense.app.presentation.ui.questionnaire

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.stresssense.app.presentation.components.GradientButton
import com.stresssense.app.presentation.components.getStressColor
import com.stresssense.app.presentation.theme.*

/**
 * PSS-10 Stress Questionnaire Screen.
 * Displays questions sequentially with beautiful option selector cards and a progress bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireScreen(
    onNavigateBack: () -> Unit,
    viewModel: QuestionnaireViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentQuestionIndex = uiState.currentQuestionIndex
    val totalQuestions = viewModel.questions.size
    val currentQuestion = viewModel.questions[currentQuestionIndex]
    val selectedOption = uiState.answers[currentQuestionIndex]

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stress Survey",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CardBackground.copy(alpha = 0.5f),
                            Background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Question ${currentQuestionIndex + 1} of $totalQuestions",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${((currentQuestionIndex + 1) * 100) / totalQuestions}%",
                        fontSize = 14.sp,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (currentQuestionIndex + 1).toFloat() / totalQuestions },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AccentBlue,
                    trackColor = DividerColor
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Question Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Animated Question Text
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.3f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentQuestion,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextPrimary,
                                lineHeight = 26.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Answer Options
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.7f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            viewModel.options.forEachIndexed { optionIndex, optionText ->
                                val isSelected = selectedOption == optionIndex
                                OptionCard(
                                    text = optionText,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectOption(optionIndex) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation Buttons at Bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    Button(
                        onClick = { viewModel.previousQuestion() },
                        enabled = currentQuestionIndex > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            contentColor = TextSecondary,
                            disabledContentColor = TextTertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous Question")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }

                    // Next / Submit Button
                    if (currentQuestionIndex == totalQuestions - 1) {
                        GradientButton(
                            text = "Submit",
                            onClick = { viewModel.submitSurvey() },
                            enabled = selectedOption != null && !uiState.isSaving,
                            modifier = Modifier.width(140.dp)
                        )
                    } else {
                        Button(
                            onClick = { viewModel.nextQuestion() },
                            enabled = selectedOption != null,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                disabledContainerColor = DividerColor,
                                contentColor = SurfaceColor,
                                disabledContentColor = TextTertiary
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text("Next", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next Question")
                        }
                    }
                }
            }
        }
    }

    // Result Dialog
    if (uiState.showResultDialog) {
        ResultDialog(
            rawScore = uiState.rawScore,
            finalScore = uiState.finalScore,
            stressLevel = uiState.stressLevel,
            onDismiss = {
                viewModel.dismissResultDialog()
                onNavigateBack()
            }
        )
    }

    // Loading overlay
    if (uiState.isSaving) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AccentBlue)
        }
    }
}

@Composable
private fun OptionCard(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, AccentBlue)
    } else {
        BorderStroke(1.dp, DividerColor)
    }
    
    val containerColor = if (isSelected) {
        AccentBlue.copy(alpha = 0.08f)
    } else {
        SurfaceColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                color = if (isSelected) AccentBlue else TextPrimary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AccentBlue,
                    unselectedColor = TextTertiary
                )
            )
        }
    }
}

@Composable
private fun ResultDialog(
    rawScore: Int,
    finalScore: Int,
    stressLevel: String,
    onDismiss: () -> Unit
) {
    val stressColor = getStressColor(finalScore)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Success",
                    tint = LowStress,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Survey Completed",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Based on the Perceived Stress Scale (PSS-10):",
                    fontSize = 13.sp,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Score Badge
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(stressColor.copy(alpha = 0.12f), RoundedCornerShape(50.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$rawScore",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = stressColor
                        )
                        Text(
                            text = "/ 40",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stressLevel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = stressColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Equivalent Stress Score: $finalScore / 100",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(28.dp))

                GradientButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
