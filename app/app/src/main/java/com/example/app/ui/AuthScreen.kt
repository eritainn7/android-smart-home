package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.app.data.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(auth: AuthRepository, onAuthenticated: (String) -> Unit) {
    var registration by remember { mutableStateOf(false) }
    var awaitingConfirmation by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101E2B), Color(0xFF1A4050))))
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 54.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Дом начинается здесь", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Устройства, комнаты и состояние дома — в одном месте.", color = Color(0xFFB4CBD1))
        Spacer(Modifier.height(32.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when { awaitingConfirmation -> "Подтвердите email"; registration -> "Создать аккаунт"; else -> "Войти" },
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(email, { email = it; error = null }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (awaitingConfirmation) {
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Код из письма") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                } else {
                    OutlinedTextField(password, { password = it; error = null }, label = { Text("Пароль") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    if (registration) OutlinedTextField(confirm, { confirm = it }, label = { Text("Повторите пароль") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        error = when {
                            email.isBlank() -> "Введите email"
                            awaitingConfirmation && code.length != 6 -> "Введите шестизначный код"
                            !awaitingConfirmation && password.length < 10 -> "Пароль должен содержать минимум 10 символов"
                            registration && password != confirm -> "Пароли не совпадают"
                            else -> null
                        }
                        if (error != null) return@Button
                        busy = true
                        scope.launch {
                            runCatching {
                                when {
                                    awaitingConfirmation -> {
                                        auth.confirm(email, code)
                                        auth.login(email, password)
                                    }
                                    registration -> {
                                        auth.register(email, password)
                                        ""
                                    }
                                    else -> auth.login(email, password)
                                }
                            }.onSuccess { token ->
                                if (token.isNotBlank()) onAuthenticated(token)
                                else awaitingConfirmation = true
                            }.onFailure { error = it.message ?: "Не удалось выполнить запрос" }
                            busy = false
                        }
                    }
                ) {
                    if (busy) CircularProgressIndicator()
                    else Text(if (awaitingConfirmation) "Подтвердить" else if (registration) "Зарегистрироваться" else "Войти")
                }
                if (!awaitingConfirmation) TextButton(onClick = { registration = !registration; error = null }) {
                    Text(if (registration) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться")
                }
            }
        }
    }
}
