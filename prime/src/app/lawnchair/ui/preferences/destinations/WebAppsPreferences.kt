package app.lawnchair.ui.preferences.destinations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout

/**
 * Entry point for managing Web Apps in Prime Launcher.
 *
 * Browser-installed WebAPKs are already ordinary Android launcher apps.
 * Shortcut-backed Web Apps will be integrated with the existing launcher model
 * in a later step, without replacing their browser runtime.
 */
@Composable
fun WebAppsPreferences() {
    PreferenceLayout(
        label = "Applications Web (PWA)",
        backArrowVisible = true,
    ) {
        PreferenceGroup {
            Text("Gestion des applications Web")
            Text(
                "Les applications Web installées comme applications Android " +
                    "(WebAPK) apparaissent déjà dans le tiroir."
            )
            Text(
                "La gestion des raccourcis Web dans le tiroir sera ajoutée ici, " +
                    "en conservant leur mode de lancement d’origine."
            )
        }
    }
}
