@file:Suppress("MatchingDeclarationName", "MagicNumber")

package com.faheem.habits.core.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/** Friendly identity for an installed Android app. */
data class InstalledAppIdentity(
    val label: String,
    val icon: ImageBitmap?,
    val packageName: String = ""
)

private const val ICON_SIZE_PX = 144
private const val MAX_CACHE_ENTRIES = 512
private val identityCache = ConcurrentHashMap<String, InstalledAppIdentity>()

/**
 * Resolves an app's display name and launcher icon.
 *
 * Android 11+ hides other packages unless the manifest declares visibility, so
 * this falls back to the installed-app list and finally to a readable name
 * derived from the package id. Icons are rasterised at a fixed size because
 * adaptive icons report an intrinsic size of -1 and would otherwise draw blank.
 */
fun installedAppIdentity(context: Context, packageName: String): InstalledAppIdentity {
    identityCache[packageName]?.let { return it }
    val packageManager = context.packageManager
    val info = runCatching {
        packageManager.getApplicationInfo(packageName, 0)
    }.getOrNull() ?: runCatching {
        packageManager.getInstalledApplications(0).firstOrNull { it.packageName == packageName }
    }.getOrNull()

    val label = info?.let { applicationInfo ->
        runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }.getOrNull()
    }?.takeIf { it.isNotBlank() && it != packageName }
        ?: prettyAppLabel(packageName)
    val icon = info?.let { applicationInfo ->
        runCatching { applicationInfo.loadIcon(packageManager).toImageBitmap(ICON_SIZE_PX) }.getOrNull()
    }
    val identity = InstalledAppIdentity(label, icon, packageName)
    if (identityCache.size >= MAX_CACHE_ENTRIES) identityCache.clear()
    identityCache[packageName] = identity
    return identity
}

/** Best-effort display name used when Android cannot resolve an app label. */
fun prettyAppLabel(packageName: String): String = KNOWN_APP_LABELS[packageName] ?: run {
    val parts = packageName.split('.').filter { it.isNotBlank() }
    if (parts.isEmpty()) {
        packageName
    } else {
        val meaningful = parts
            .dropWhile { it.lowercase() in PACKAGE_PREFIXES }
            .filterNot { it.lowercase() in GENERIC_SEGMENTS }
            .ifEmpty { parts }
        val vendor = meaningful.firstOrNull()?.lowercase()?.let(VENDOR_NAMES::get)
        val tail = if (vendor == null) meaningful else meaningful.drop(1)
        val core = tail.firstOrNull() ?: meaningful.first()
        val words = core.split('_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }
        when {
            vendor == null -> words.ifBlank { packageName }
            words.isBlank() || words.equals(vendor, ignoreCase = true) -> vendor
            else -> "$vendor $words"
        }
    }
}

private fun Drawable.toImageBitmap(size: Int): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

private val PACKAGE_PREFIXES = setOf("com", "org", "net", "io", "co", "me", "app", "apps", "www")

private val GENERIC_SEGMENTS = setOf(
    "android", "app", "apps", "mobile", "client", "release", "debug", "internal",
    "production", "prod", "stable", "main", "base", "core", "common", "gms", "gsf",
    "service", "services", "platform", "application", "activity", "ui", "katana",
    "orca", "androidclient", "w4b"
)

private val VENDOR_NAMES = mapOf(
    "google" to "Google",
    "samsung" to "Samsung",
    "sec" to "Samsung",
    "miui" to "Xiaomi",
    "xiaomi" to "Xiaomi",
    "oppo" to "OPPO",
    "coloros" to "OPPO",
    "vivo" to "vivo",
    "oneplus" to "OnePlus",
    "huawei" to "Huawei",
    "honor" to "Honor",
    "hihonor" to "Honor",
    "motorola" to "Motorola",
    "sony" to "Sony",
    "lge" to "LG",
    "htc" to "HTC",
    "asus" to "ASUS",
    "zte" to "ZTE",
    "realme" to "realme",
    "transsion" to "Transsion",
    "nothing" to "Nothing"
)

private val KNOWN_APP_LABELS = mapOf(
    "com.facebook.katana" to "Facebook",
    "com.facebook.orca" to "Messenger",
    "com.facebook.lite" to "Facebook Lite",
    "com.instagram.android" to "Instagram",
    "com.whatsapp" to "WhatsApp",
    "com.whatsapp.w4b" to "WhatsApp Business",
    "com.google.android.youtube" to "YouTube",
    "com.google.android.apps.youtube.music" to "YouTube Music",
    "com.google.android.gm" to "Gmail",
    "com.android.chrome" to "Chrome",
    "org.chromium.chrome" to "Chrome",
    "com.google.android.apps.maps" to "Google Maps",
    "com.google.android.apps.photos" to "Google Photos",
    "com.google.android.apps.docs" to "Google Drive",
    "com.google.android.apps.nbu.files" to "Files by Google",
    "com.google.android.gms" to "Google Play services",
    "com.google.android.gsf" to "Google Services Framework",
    "com.android.vending" to "Google Play Store",
    "com.google.android.dialer" to "Phone",
    "com.google.android.contacts" to "Contacts",
    "com.google.android.deskclock" to "Clock",
    "com.google.android.calendar" to "Calendar",
    "com.google.android.keep" to "Google Keep",
    "com.google.android.apps.wellbeing" to "Digital Wellbeing",
    "com.google.android.apps.messaging" to "Google Messages",
    "com.google.android.projection.gearhead" to "Android Auto",
    "com.spotify.music" to "Spotify",
    "com.netflix.mediaclient" to "Netflix",
    "com.zhiliaoapp.musically" to "TikTok",
    "com.ssandroid.ssinstagram" to "Instagram",
    "org.telegram.messenger" to "Telegram",
    "org.telegram.messenger.web" to "Telegram",
    "com.snapchat.android" to "Snapchat",
    "com.twitter.android" to "X",
    "com.discord" to "Discord",
    "com.reddit.frontpage" to "Reddit",
    "com.linkedin.android" to "LinkedIn",
    "com.microsoft.teams" to "Microsoft Teams",
    "com.microsoft.office.outlook" to "Outlook",
    "com.android.settings" to "Settings",
    "com.android.systemui" to "System UI",
    "com.android.providers.downloads" to "Downloads",
    "com.android.camera" to "Camera",
    "com.google.android.GoogleCamera" to "Camera"
)
