package com.carpoolr.app.ui

import android.view.HapticFeedbackConstants
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.carpoolr.app.ui.components.CarpoolrBackground
import com.carpoolr.app.ui.components.CarpoolrBottomBar
import com.carpoolr.app.ui.components.CarpoolrTopBar
import com.carpoolr.app.ui.components.MenuContent
import com.carpoolr.app.ui.components.ProfileContent
import com.carpoolr.app.ui.components.SideSheet
import com.carpoolr.app.ui.components.Tab
import com.carpoolr.app.ui.screens.HomeScreen
import com.carpoolr.app.ui.screens.PlaceholderScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun CarpoolrApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current

    // backgroundBackdrop: only the green field, sampled by glass cards inside the content.
    // contentBackdrop: everything on screen, sampled by the floating top bar, tab bar and sheets.
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
                    Tab.Find -> PlaceholderScreen(
                        "Find a pool",
                        "Search routes that overlap yours and leave within 15 minutes.",
                        contentPadding
                    )
                    Tab.Rides -> PlaceholderScreen(
                        "No rides yet",
                        "Pools you join or start will show up here.",
                        contentPadding
                    )
                    Tab.Chats -> PlaceholderScreen(
                        "Quiet in here",
                        "Chat with your pool once you're matched.",
                        contentPadding
                    )
                }
            }
        }

        CarpoolrTopBar(
            backdrop = contentBackdrop,
            onMenu = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                menuOpen = true
            },
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
            visible = menuOpen,
            fromStart = true,
            backdrop = contentBackdrop,
            onDismiss = { menuOpen = false }
        ) { MenuContent(onClose = { menuOpen = false }) }

        SideSheet(
            visible = profileOpen,
            fromStart = false,
            backdrop = contentBackdrop,
            onDismiss = { profileOpen = false }
        ) { ProfileContent(onClose = { profileOpen = false }) }
    }
}
