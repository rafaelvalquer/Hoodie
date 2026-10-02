package com.hoodie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.hoodie.app.core.database.DatabaseBootstrapResult
import com.hoodie.app.core.database.DatabaseGate
import com.hoodie.app.core.security.LocalDataReset
import com.hoodie.app.presentation.navigation.HoodieRoot
import com.hoodie.app.presentation.navigation.SplashScreen
import com.hoodie.app.presentation.screens.recovery.DatabaseRecoveryScreen
import com.hoodie.app.presentation.theme.HoodieTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var gate: DatabaseGate
    @Inject lateinit var reset: LocalDataReset

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HoodieTheme { SecureDatabaseHost(gate, reset) { HoodieRoot() } } }
    }
}

/**
 * Nada que use o banco é composto antes de o [DatabaseGate] confirmar que ele abre
 * cifrado. Falhou → tela de recuperação (as ViewModels do app nem são criadas).
 */
@Composable
private fun SecureDatabaseHost(gate: DatabaseGate, reset: LocalDataReset, content: @Composable () -> Unit) {
    var result by remember { mutableStateOf<DatabaseBootstrapResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt) {
        busy = true
        result = withContext(Dispatchers.IO) { if (attempt == 0) gate.ensure() else gate.retry() }
        busy = false
    }

    when (val r = result) {
        null -> SplashScreen()
        is DatabaseBootstrapResult.Ready -> content()
        is DatabaseBootstrapResult.Failed -> DatabaseRecoveryScreen(
            state = r.state,
            busy = busy,
            onRetry = { attempt++ },
            onReset = {
                scope.launch {
                    busy = true
                    reset.wipe()
                    reset.restart()
                }
            },
        )
    }
}
