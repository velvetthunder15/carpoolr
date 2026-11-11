package com.carpoolr.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.carpoolr.app.auth.FirebasePhoneAuth
import com.carpoolr.app.data.Backend
import com.carpoolr.app.ui.CarpoolrApp
import com.carpoolr.app.ui.screens.PhoneVerifyScreen
import com.carpoolr.app.ui.screens.SignInScreen
import com.carpoolr.app.ui.theme.CarpoolrColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Backend.client.handleDeeplinks(intent)
        setContent {
            val status by Backend.client.auth.sessionStatus.collectAsState()
            when (val current = status) {
                is SessionStatus.Authenticated -> {
                    val uid = current.session.user?.id ?: Backend.currentUserId()
                    var verified by remember(uid) {
                        mutableStateOf(FirebasePhoneAuth.isVerified(this@MainActivity, uid))
                    }
                    var skippedUnconfigured by rememberSaveable(uid) { mutableStateOf(false) }

                    if (verified || skippedUnconfigured) {
                        CarpoolrApp()
                    } else {
                        PhoneVerifyScreen(
                            activity = this@MainActivity,
                            userId = uid,
                            onVerified = { verified = true },
                            onSkipUnconfigured = { skippedUnconfigured = true }
                        )
                    }
                }
                is SessionStatus.Initializing -> Box(Modifier.fillMaxSize().background(CarpoolrColors.Green))
                else -> SignInScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Backend.client.handleDeeplinks(intent)
    }
}
