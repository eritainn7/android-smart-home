package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.app.data.ApiClient
import com.example.app.data.AuthRepository
import com.example.app.data.DeviceEvents
import com.example.app.data.HomeRepository
import com.example.app.data.SessionStore
import com.example.app.ui.AuthScreen
import com.example.app.ui.SmartHomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SmartHomeApp()
                }
            }
        }
    }
}

@Composable
private fun SmartHomeApp() {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val api = remember { ApiClient() }
    val authRepository = remember { AuthRepository(api) }
    val homeRepository = remember { HomeRepository(api) }
    val deviceEvents = remember { DeviceEvents() }
    val scope = rememberCoroutineScope()

    var token by rememberSaveable { mutableStateOf<String?>(null) }
    var isCheckingSession by remember { mutableStateOf(true) }

    LaunchedEffect(session) {
        token = session.token()
        isCheckingSession = false
    }

    if (isCheckingSession) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val savedToken = token
    if (savedToken == null) {
        AuthScreen(
            auth = authRepository,
            onAuthenticated = { accessToken ->
                session.saveToken(accessToken)
                token = accessToken
            }
        )
    } else {
        SmartHomeScreen(
            repository = homeRepository,
            session = session,
            deviceEvents = deviceEvents,
            onLogout = {
                scope.launch {
                    session.clear()
                    token = null
                }
            }
        )
    }
}
