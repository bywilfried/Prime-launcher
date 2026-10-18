package app.lawnchair.prime.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Single permission policy shared by Prime Search results and settings. */
object PrimeSearchPermissions {
    fun required(category: PrimeSearchResultPreferences.Category): Boolean = when (category) {
        PrimeSearchResultPreferences.Category.CONTACTS,
        PrimeSearchResultPreferences.Category.CALENDAR,
        PrimeSearchResultPreferences.Category.REMINDERS,
        PrimeSearchResultPreferences.Category.FILES,
        PrimeSearchResultPreferences.Category.NOTIFICATIONS -> true
        else -> false
    }

    fun runtimePermissions(category: PrimeSearchResultPreferences.Category): Array<String> = when (category) {
        PrimeSearchResultPreferences.Category.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
        PrimeSearchResultPreferences.Category.CALENDAR,
        PrimeSearchResultPreferences.Category.REMINDERS -> arrayOf(Manifest.permission.READ_CALENDAR)
        PrimeSearchResultPreferences.Category.FILES -> if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        else -> emptyArray()
    }

    fun granted(context: Context, category: PrimeSearchResultPreferences.Category): Boolean {
        if (category == PrimeSearchResultPreferences.Category.NOTIFICATIONS) {
            return PrimeNotificationHistory.isGranted(context)
        }
        return runtimePermissions(category).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun specialAccessIntent(category: PrimeSearchResultPreferences.Category): Intent? =
        if (category == PrimeSearchResultPreferences.Category.NOTIFICATIONS) {
            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        } else null

    fun explanation(category: PrimeSearchResultPreferences.Category): String = when (category) {
        PrimeSearchResultPreferences.Category.CONTACTS -> "Autoriser l'accès aux contacts pour les rechercher."
        PrimeSearchResultPreferences.Category.CALENDAR -> "Autoriser la lecture de l'agenda pour rechercher les événements."
        PrimeSearchResultPreferences.Category.REMINDERS -> "Autoriser la lecture du calendrier pour rechercher les rappels."
        PrimeSearchResultPreferences.Category.FILES -> "Autoriser l'accès aux médias pour rechercher les fichiers compatibles."
        PrimeSearchResultPreferences.Category.NOTIFICATIONS -> "Activer l'accès aux notifications dans les paramètres Android pour enregistrer leur historique."
        else -> ""
    }
}
