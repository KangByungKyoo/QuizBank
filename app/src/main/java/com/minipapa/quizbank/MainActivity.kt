package com.minipapa.quizbank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.minipapa.quizbank.navigation.BankViewModel
import com.minipapa.quizbank.data.FirebaseAnswerReviewer
import com.minipapa.quizbank.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as QuizBankApplication
        val factory = viewModelFactory { initializer { BankViewModel(app.repository, createSavedStateHandle(), FirebaseAnswerReviewer()) } }
        setContent { QuizTheme { val model: BankViewModel = viewModel(factory = factory); QuizBankApp(model) } }
    }
}
