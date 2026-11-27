package com.faheem.habits.app

import com.faheem.habits.core.ui.AppModule

/** Single source of truth for in-app destinations and `momentum://` deep links. */
object MomentumRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MODULE_PATTERN = "module/{module}?itemId={itemId}&requestToken={requestToken}"
    const val MODULE_PREFIX = "module/"

    const val ARG_MODULE = "module"
    const val ARG_ITEM_ID = "itemId"
    const val ARG_REQUEST_TOKEN = "requestToken"

    const val DEEP_LINK_SCHEME = "momentum"
    const val DEEP_LINK_MODULE_HOST = "module"
    const val DEEP_LINK_AUTH_HOST = "auth"

    fun module(
        module: AppModule,
        itemId: Int? = null,
        requestToken: Long? = null
    ): String = buildString {
        append(MODULE_PREFIX)
        append(module.name.lowercase())
        if (itemId != null && itemId > 0) append("?$ARG_ITEM_ID=$itemId")
        if (requestToken != null && requestToken > 0L) {
            append(if (itemId != null && itemId > 0) "&" else "?")
            append("$ARG_REQUEST_TOKEN=$requestToken")
        }
    }

    fun moduleDeepLink(
        module: AppModule,
        itemId: Int? = null,
        requestToken: Long? = null
    ): String = buildString {
        append("$DEEP_LINK_SCHEME://$DEEP_LINK_MODULE_HOST/${module.name.lowercase()}")
        if (itemId != null && itemId > 0) append("?$ARG_ITEM_ID=$itemId")
        if (requestToken != null && requestToken > 0L) {
            append(if (itemId != null && itemId > 0) "&" else "?")
            append("$ARG_REQUEST_TOKEN=$requestToken")
        }
    }

    /** Module names are case-insensitive because they arrive from intents and deep links. */
    fun parseModule(value: String?): AppModule? =
        value?.takeIf { it.isNotBlank() }?.let { name ->
            AppModule.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        }
}

/** Where a module entry came from; system entries may bypass hidden-module state. */
enum class ModuleEntrySource { NORMAL, DEEP_LINK, NOTIFICATION, SYSTEM }

/**
 * Hidden modules must not be reachable through ordinary navigation, deep links,
 * or notification taps. Profile and Settings remain reachable because they are
 * needed to sign in, restore access, and change module visibility.
 */
fun isModuleEntryAllowed(
    module: AppModule,
    isVisible: (AppModule) -> Boolean,
    source: ModuleEntrySource
): Boolean = when (source) {
    ModuleEntrySource.SYSTEM -> true
    ModuleEntrySource.NORMAL,
    ModuleEntrySource.DEEP_LINK,
    ModuleEntrySource.NOTIFICATION ->
        module == AppModule.Profile || module == AppModule.Settings || isVisible(module)
}
