package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.GalleryTab
import com.example.ui.GalleryViewModel
import com.example.ui.components.FloatingNavigation
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.PhotoDetailDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UploadDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.components.GlassmorphismBackground
import com.example.ui.components.LocalGlassStyle
import com.example.ui.components.LocalIsDarkMode
import com.example.ui.components.getGlassStyle
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OledBlack

class MainActivity : FragmentActivity() {

    private val viewModel: GalleryViewModel by viewModels {
        GalleryViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val darkModeSetting by viewModel.darkModeSetting.collectAsStateWithLifecycle()
            val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (darkModeSetting) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark
            }
            val glassStyle = remember(themePreset) { getGlassStyle(themePreset) }

            CompositionLocalProvider(
                LocalGlassStyle provides glassStyle,
                LocalIsDarkMode provides isDark
            ) {
                MyApplicationTheme(darkTheme = isDark) {
                    MainAppScreen(viewModel = viewModel, isDark = isDark)
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: GalleryViewModel, isDark: Boolean = true) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val selectedPhoto by viewModel.selectedPhoto.collectAsStateWithLifecycle()
    val decryptedPreview by viewModel.decryptedPreview.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadInitialEncrypted by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearStatusMessage()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(currentTab, isAppLocked) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        if (currentTab == GalleryTab.VAULT || isAppLocked) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                // Instantly lock vault when app leaves foreground
                viewModel.lockVault(silent = true)
            }
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.lockApp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    GlassmorphismBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Screen View Content with Liquid Glass Slide Effect
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val slideIn = slideInHorizontally(
                    initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(300))
                val slideOut = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth },
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(animationSpec = tween(220))
                (slideIn togetherWith slideOut).using(
                    SizeTransform(clip = false)
                )
            },
            label = "glass_tab_transition",
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // Specular glass edge highlight during tab transitions
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.035f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.02f)
                                )
                            )
                        )
                    }
            ) {
                when (tab) {
                    GalleryTab.GALLERY -> GalleryScreen(
                        viewModel = viewModel,
                        onOpenUploadDialog = {
                            uploadInitialEncrypted = false
                            showUploadDialog = true
                        }
                    )
                    GalleryTab.VAULT -> VaultScreen(
                        viewModel = viewModel,
                        onOpenUploadDialog = {
                            uploadInitialEncrypted = true
                            showUploadDialog = true
                        }
                    )
                    GalleryTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }

        // 3-Tab Liquid Glass Floating Navigation Bar (Gallery, Vault, Settings)
        FloatingNavigation(
            currentTab = currentTab,
            isVaultUnlocked = isVaultUnlocked,
            onTabSelected = { newTab ->
                if (currentTab == GalleryTab.VAULT && newTab != GalleryTab.VAULT) {
                    viewModel.lockVault(silent = true)
                }
                viewModel.setTab(newTab)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }

    // Photo Viewer Dialog
    if (selectedPhoto != null) {
        PhotoDetailDialog(
            photo = selectedPhoto!!,
            decryptedBitmap = decryptedPreview,
            viewModel = viewModel,
            onDismiss = { viewModel.selectPhoto(null) }
        )
    }

    // Upload & Encrypt Dialog
    if (showUploadDialog) {
        UploadDialog(
            initialEncrypted = uploadInitialEncrypted,
            viewModel = viewModel,
            onDismiss = { showUploadDialog = false }
        )
    }

    // Application-Level Biometric Lock Screen Overlay
    if (isAppLocked) {
        AppLockScreen(
            viewModel = viewModel,
            onUnlock = { viewModel.unlockApp() }
        )
    }
}
