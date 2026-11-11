package com.carpoolr.app.ui

import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.carpoolr.app.connect.ConnectionStore
import com.carpoolr.app.connect.Provider
import com.carpoolr.app.connect.WebLoginActivity
import com.carpoolr.app.connect.openProviderApp
import com.carpoolr.app.auth.FirebasePhoneAuth
import com.carpoolr.app.data.Backend
import com.carpoolr.app.data.Profile
import com.carpoolr.app.pool.MatchService
import com.carpoolr.app.profile.ProfileStore
import com.carpoolr.app.ui.components.CarpoolrBackground
import com.carpoolr.app.ui.components.CarpoolrBottomBar
import com.carpoolr.app.ui.components.CarpoolrTopBar
import com.carpoolr.app.ui.components.ProfileContent
import com.carpoolr.app.ui.components.SideSheet
import com.carpoolr.app.ui.components.Tab
import com.carpoolr.app.ui.screens.ChatScreen
import com.carpoolr.app.ui.screens.HomeScreen
import com.carpoolr.app.ui.screens.RidesScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@Composable
fun CarpoolrApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var connectionsVersion by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val context = LocalContext.current
    val connections = remember { ConnectionStore(context) }
    var pendingProvider by rememberSaveable { mutableStateOf<String?>(null) }
    val webLogin = rememberLauncherForActivityResult(WebLoginActivity.Contract()) { outcome ->
        val p = pendingProvider?.let(Provider::valueOf)
        pendingProvider = null
        if (p != null) when (outcome) {
            WebLoginActivity.Outcome.SignedIn -> {
                connectionsVersion++
                Toast.makeText(context, "${p.label} connected", Toast.LENGTH_SHORT).show()
            }
            WebLoginActivity.Outcome.TappedDone -> {
                val ok = connections.markSignedIn(p)
                connectionsVersion++
                if (ok) Toast.makeText(context, "${p.label} connected", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "${p.label} doesn't look signed in yet", Toast.LENGTH_SHORT).show()
            }
            WebLoginActivity.Outcome.Cancelled -> Unit
        }
    }
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(ProfileStore.loadCached(context)) }
    var savingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(profileOpen) {
        val cached = ProfileStore.loadCached(context)
        if (cached != null) profile = cached
        if (profileOpen || profile == null) {
            val synced = ProfileStore.syncFromServer(context)
            if (synced != null) profile = synced
        }
    }

    val backgroundBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()

    val status = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val contentPadding = PaddingValues(
        top = status.calculateTopPadding() + 84.dp,
        bottom = nav.calculateBottomPadding() + 108.dp
    )

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
            CarpoolrBackground(Modifier.layerBackdrop(backgroundBackdrop))
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    val slide = spring<IntOffset>(dampingRatio = 0.85f, stiffness = 380f)
                    (slideInHorizontally(slide) { it / 6 * dir } +
                        fadeIn(spring(stiffness = 380f)) +
                        scaleIn(spring(dampingRatio = 0.85f, stiffness = 380f), initialScale = 0.98f)) togetherWith
                        (slideOutHorizontally(slide) { -it / 6 * dir } +
                            fadeOut(spring(stiffness = 600f)) +
                            scaleOut(spring(stiffness = 600f), targetScale = 0.98f))
                },
                label = "tabs"
            ) { index ->
                when (Tab.entries[index]) {
                    Tab.Home -> HomeScreen(backgroundBackdrop, contentPadding)
                    Tab.Rides -> RidesScreen(contentPadding)
                    Tab.Chats -> ChatScreen(contentPadding)
                }
            }
        }

        CarpoolrTopBar(
            backdrop = contentBackdrop,
            onProfile = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                profileOpen = true
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        CarpoolrBottomBar(
            backdrop = contentBackdrop,
            selected = { tab },
            onSelect = { index ->
                if (index != tab) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    tab = index
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        SideSheet(
            visible = profileOpen,
            fromStart = false,
            backdrop = contentBackdrop,
            onDismiss = { profileOpen = false }
        ) {
            ProfileContent(
                visible = profileOpen,
                onClose = { profileOpen = false },
                isConnected = { p ->
                    connectionsVersion
                    connections.isConnected(p)
                },
                onConnect = { p ->
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (p.signsInOnWeb) { pendingProvider = p.name; webLogin.launch(p) } else openProviderApp(context, p)
                },
                onDisconnect = { p ->
                    connections.disconnect(p)
                    connectionsVersion++
                    Toast.makeText(context, "${p.label} disconnected", Toast.LENGTH_SHORT).show()
                },
                onSignOut = {
                    profileOpen = false
                    MatchService.stop(context)
                    FirebasePhoneAuth.signOut(context)
                    ProfileStore.clearAll(context)
                    Provider.entries.forEach { connections.disconnect(it) }
                    runCatching {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        WebStorage.getInstance().deleteAllData()
                    }
                    scope.launch {
                        runCatching { Backend.leavePool() }
                        runCatching { Backend.signOut() }
                    }
                },
                profile = profile,
                saving = savingProfile,
                onSaveProfile = { updated, toastMsg ->
                    profile = updated
                    ProfileStore.saveCached(context, updated, syncedRichSchema = false)
                    savingProfile = true
                    scope.launch {
                        ProfileStore.persist(context, updated)
                            .onSuccess { saved ->
                                profile = saved
                                if (!toastMsg.isNullOrBlank()) {
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                            .onFailure { e ->
                                val msg = e.message?.lineSequence()?.firstOrNull()?.take(100)?.lowercase() ?: "couldn't save"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        savingProfile = false
                    }
                },
                onRow = { label ->
                    when (label) {
                        "ride history" -> { profileOpen = false; tab = Tab.Rides.ordinal }
                        "notifications" -> runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                        "help" -> runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO,
                                android.net.Uri.parse("mailto:chiragrasane15@gmail.com?subject=carpoolr%20help")))
                        }
                        "about" -> Toast.makeText(context, "carpoolr v0.1.0", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
