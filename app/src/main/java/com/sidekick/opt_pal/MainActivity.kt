package com.sidekick.opt_pal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sidekick.opt_pal.core.session.SessionViewModel
import com.sidekick.opt_pal.di.AppModule
import com.sidekick.opt_pal.ui.theme.OPTPalTheme

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels { SessionViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OPTPalTheme {
                OPTPalApp(
                    sessionViewModel = sessionViewModel,
                    securitySessionManager = AppModule.securitySessionManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppModule.securitySessionManager.onForegrounded()
    }

    override fun onStop() {
        AppModule.securitySessionManager.onBackgrounded()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        AppModule.securitySessionManager.recordUserInteraction()
    }
}
