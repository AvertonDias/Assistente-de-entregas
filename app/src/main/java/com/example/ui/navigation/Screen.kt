package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Auth : Screen("auth", "Autenticação")
    object Home : Screen("home", "Início")
    object DeliveryMode : Screen("delivery_mode", "Modo Entrega")
    object PeopleList : Screen("people_list", "Destinatários")
    object PersonEdit : Screen("person_edit/{personId}?initialAddress={initialAddress}", "Cadastro") {
        fun createRoute(personId: Long = 0, initialAddress: String = "") = "person_edit/$personId?initialAddress=${android.net.Uri.encode(initialAddress)}"
    }
    object Signature : Screen("signature/{deliveryId}?personId={personId}", "Assinatura") {
        fun createRoute(deliveryId: Long = 0, personId: Long = 0) = "signature/$deliveryId?personId=$personId"
    }
    object AutomationLab : Screen("automation_lab", "Laboratório de Automação")
    object AccessibilitySettings : Screen("accessibility_settings", "Acessibilidade")
    object OverlaySettings : Screen("overlay_settings", "Sobreposição")
    object Diagnostic : Screen("diagnostic", "Diagnóstico")
    object Settings : Screen("settings", "Configurações")
}
