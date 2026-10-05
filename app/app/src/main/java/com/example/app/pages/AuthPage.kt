package com.example.app.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.viewmodels.AuthEvent
import com.example.app.viewmodels.AuthViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun AuthPage(
    nav: String,
    onNavChange: (String) -> Unit,
    onSuccess: ()-> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.NavigateToSuccess -> onSuccess()
            }
        }
    }
    Column(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.4f),  shape = RoundedCornerShape(30.dp))
            //.height(IntrinsicSize.Min)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment =  Alignment.CenterHorizontally
    ) {
        Text(
            color = Color(0xFF8A2BE2),
            text = if (nav == "reg_page") "Регистрация" else "Вход",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField (
            value = state.email,
            placeholder = {Text("Почта")},
            onValueChange = {}

        )

        OutlinedTextField (
            value = "",
            placeholder = {Text("Пароль")},
            onValueChange = {}

        )

        if (nav == "reg_page") OutlinedTextField (
            value = "",
            placeholder = {Text("Повторите пароль")},
            onValueChange = {}

        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {  },
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            if (nav == "reg_page") Text("Зарегистрироваться") else Text("Войти")
        }

        Button(
            onClick = { if (nav == "reg_page") onNavChange("login_page") else onNavChange("reg_page")},
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Green,
                contentColor = Color.Black
            )
        ) {
            if (nav == "reg_page") Text("Есть аккаунт?") else Text("Нет аккаунта?")
        }
    }
}