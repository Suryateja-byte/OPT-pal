package com.sidekick.opt_pal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sidekick.opt_pal.core.casestatus.EXTRA_USCIS_CASE_ID
import com.sidekick.opt_pal.core.policy.EXTRA_POLICY_ALERT_ID
import com.sidekick.opt_pal.core.session.SessionViewModel
import com.sidekick.opt_pal.di.AppModule
import com.sidekick.opt_pal.ui.theme.OPTPalTheme

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels { SessionViewModel.Factory }
    private var pendingUscisCaseId by mutableStateOf<String?>(null)
    private var pendingPolicyAlertId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUscisCaseId = intent.extractPendingUscisCaseId()
        pendingPolicyAlertId = intent.extractPendingPolicyAlertId()
        enableEdgeToEdge()
        setContent {
            OPTPalTheme {
                OPTPalApp(
                    sessionViewModel = sessionViewModel,
                    securitySessionManager = AppModule.securitySessionManager,
                    pendingUscisCaseId = pendingUscisCaseId,
                    onPendingUscisCaseHandled = { pendingUscisCaseId = null },
                    pendingPolicyAlertId = pendingPolicyAlertId,
                    onPendingPolicyAlertHandled = { pendingPolicyAlertId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUscisCaseId = intent.extractPendingUscisCaseId()
        pendingPolicyAlertId = intent.extractPendingPolicyAlertId()
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

private fun android.content.Intent?.extractPendingUscisCaseId(): String? {
    return this?.getStringExtra(EXTRA_USCIS_CASE_ID)?.takeIf { it.isNotBlank() }
}

private fun android.content.Intent?.extractPendingPolicyAlertId(): String? {
    return this?.getStringExtra(EXTRA_POLICY_ALERT_ID)?.takeIf { it.isNotBlank() }
}
