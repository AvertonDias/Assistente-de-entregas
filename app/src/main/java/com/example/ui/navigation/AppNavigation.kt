package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.DeliveryApp
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.delivery.DeliveryModeScreen
import com.example.ui.screens.delivery.DeliveryModeViewModel
import com.example.ui.screens.diagnostic.DiagnosticScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.lab.AutomationLabScreen
import com.example.ui.screens.people.PeopleListScreen
import com.example.ui.screens.people.PeopleViewModel
import com.example.ui.screens.people.PersonEditScreen
import com.example.ui.screens.settings.AccessibilitySettingsScreen
import com.example.ui.screens.settings.OverlaySettingsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.signature.SignatureScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    app: DeliveryApp,
    initialAction: String? = null,
    initialAddress: String? = null
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(app.authRepository)
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.deliveryRepository, app.settingsRepository)
    )
    val deliveryModeViewModel: DeliveryModeViewModel = viewModel(
        factory = DeliveryModeViewModel.Factory(app.personRepository, app.deliveryRepository)
    )
    val peopleViewModel: PeopleViewModel = viewModel(
        factory = PeopleViewModel.Factory(app.personRepository)
    )

    LaunchedEffect(initialAction, initialAddress) {
        if (initialAction == "EDIT_PERSON" && !initialAddress.isNullOrBlank()) {
            navController.navigate(Screen.PersonEdit.createRoute(0L, initialAddress)) {
                popUpTo(Screen.Home.route) { inclusive = false }
            }
        }
    }

    // Se já houver usuário autenticado no Firebase, inicia direto na Home
    val startDestination = if (app.authRepository.currentUser != null) {
        Screen.Home.route
    } else {
        Screen.Auth.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onContinueOffline = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                authViewModel = authViewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Screen.DeliveryMode.route) {
            DeliveryModeScreen(
                viewModel = deliveryModeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Screen.PeopleList.route) {
            PeopleListScreen(
                viewModel = peopleViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = Screen.PersonEdit.route,
            arguments = listOf(
                navArgument("personId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("initialAddress") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getLong("personId") ?: 0L
            val initialAddress = backStackEntry.arguments?.getString("initialAddress") ?: ""
            PersonEditScreen(
                personId = personId,
                initialAddress = initialAddress,
                viewModel = peopleViewModel,
                onNavigate = { route -> navController.navigate(route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Signature.route,
            arguments = listOf(
                navArgument("deliveryId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("personId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val deliveryId = backStackEntry.arguments?.getLong("deliveryId") ?: 0L
            val personId = backStackEntry.arguments?.getLong("personId") ?: 0L
            SignatureScreen(
                deliveryId = deliveryId,
                personId = personId,
                signatureRepository = app.signatureRepository,
                onSignatureConfirmed = { signatureData ->
                    if (deliveryId > 0) {
                        deliveryModeViewModel.setSignature(signatureData)
                    } else {
                        peopleViewModel.setTempSignature(signatureData)
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AutomationLab.route) {
            AutomationLabScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AccessibilitySettings.route) {
            AccessibilitySettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OverlaySettings.route) {
            OverlaySettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Diagnostic.route) {
            DiagnosticScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                settingsRepository = app.settingsRepository,
                firebaseSyncRepository = app.firebaseSyncRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
