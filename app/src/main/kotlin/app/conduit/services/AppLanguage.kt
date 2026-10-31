// Port of: Conduit/Services/AppLanguage.swift @6ddae94
package app.conduit

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

data class AppLocalizations(val source: String, val shipped: List<String>) {
    companion object {
        const val draftLanguagesInfoKey = "ConduitDraftLanguages"
        var main = AppLocalizations(localizations = listOf("en"), source = "en", drafts = emptyList())
            internal set
        private fun normalized(identifier: String) = identifier.replace('_', '-').lowercase(Locale.ROOT)
    }

    constructor(localizations: List<String>, source: String, drafts: List<String> = emptyList()) : this(
        source,
        listOf(source) + localizations.filter {
            normalized(it) !in (drafts + "Base" + source).map(::normalized)
        }.distinctBy(::normalized).sorted(),
    )

    fun match(identifier: String): String? {
        if (identifier in shipped) return identifier
        var candidate = normalized(identifier)
        while (candidate.isNotEmpty()) {
            shipped.firstOrNull { normalized(it) == candidate }?.let { return it }
            val cut = candidate.lastIndexOf('-')
            if (cut < 0) return null
            candidate = candidate.substring(0, cut)
        }
        return null
    }
}

sealed interface AppLanguage : Identifiable<String> {
    data object system : AppLanguage
    data class localization(val identifier: String) : AppLanguage

    val rawValue: String get() = when (this) { system -> systemRawValue; is localization -> identifier }
    override val id: String get() = rawValue
    val localizationIdentifier: String? get() = when (this) {
        system -> null
        is localization -> AppLocalizations.main.match(identifier)
    }
    val locale: Locale? get() = localizationIdentifier?.let(Locale::forLanguageTag)
    val displayName: String get() = when (this) {
        system -> AppLocalization.string("System Default")
        is localization -> {
            val name = AppLocalization.string("Name of this language", language = this)
            if (localizationIdentifier != null && name != "Name of this language") name
            else Locale.forLanguageTag(identifier).let { it.getDisplayName(it) }.ifEmpty { identifier }
        }
    }

    companion object {
        private const val systemRawValue = "system"
        operator fun invoke(rawValue: String): AppLanguage? = if (rawValue == systemRawValue) system
            else AppLocalizations.main.match(rawValue)?.let(::localization)
        val selectable: List<AppLanguage> get() = listOf(system) + AppLocalizations.main.shipped.map(::localization)
        val source: AppLanguage get() = localization(AppLocalizations.main.source)
        val current: AppLanguage get() = UserDefaults.standard.string(AppLanguageStore.defaultsKey)?.let { invoke(it) } ?: system
    }
}

class AppLanguageStore(private val defaults: UserDefaults = UserDefaults.standard) {
    var selection by mutableStateOf(defaults.string(defaultsKey)?.let { AppLanguage(it) } ?: AppLanguage.system)
        private set

    fun select(language: AppLanguage) {
        val normalized = AppLanguage(language.rawValue) ?: AppLanguage.system
        if (normalized == selection) return
        selection = normalized
        defaults.set(normalized.rawValue, defaultsKey)
        // android: per-app locale updates system-owned controls and recreates the Activity.
        platformSelect?.invoke(normalized.localizationIdentifier)
    }

    val resolvedLocale: Locale get() = selection.locale ?: Locale.getDefault()

    companion object {
        const val defaultsKey = "conduit.appLanguage"
        val shared by lazy { AppLanguageStore() }
        internal var platformSelect: ((String?) -> Unit)? = null
    }
}

object AppLocalization {
    private var localizationBundles: Map<String, JsonObject> = emptyMap()
    private var platformLocale: (() -> Locale)? = null
    private var pluralCategory: ((Locale, Double) -> String)? = null

    // android: catalogs replace immutable lproj bundles; initialized before any view is composed.
    internal fun install(context: Context) {
        val index = Json.parseToJsonElement(context.assets.open("l10n/index.json").bufferedReader().use { it.readText() }).jsonObject
        val shipped = index.getValue("shipped").jsonArray.map { it.jsonPrimitive.content }
        AppLocalizations.main = AppLocalizations(shipped, index.getValue("source").jsonPrimitive.content, emptyList())
        localizationBundles = shipped.associateWith { lang ->
            Json.parseToJsonElement(context.assets.open("l10n/$lang.json").bufferedReader().use { it.readText() }).jsonObject
        }
        platformLocale = { context.resources.configuration.locales[0] }
        pluralCategory = { locale, count -> android.icu.text.PluralRules.forLocale(locale).select(count) }
        AppLanguageStore.platformSelect = { identifier ->
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                identifier?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        }
    }

    private fun shownIdentifier(): String {
        val locale = platformLocale?.invoke() ?: Locale.getDefault()
        return AppLocalizations.main.match(locale.toLanguageTag()) ?: AppLocalizations.main.source
    }

    val formattingLocale: Locale get() {
        AppLanguage.current.locale?.let { return it }
        val shown = shownIdentifier()
        val device = platformLocale?.invoke() ?: Locale.getDefault()
        val ui = Locale.forLanguageTag(shown)
        // android: ICU expands likely scripts, matching Foundation's maximal language identifiers.
        val sameLanguage = device.language == ui.language
        val sameScript = if (device.language == "zh") {
            (device.script.ifEmpty { if (device.country in listOf("TW", "HK", "MO")) "Hant" else "Hans" }) == ui.script
        } else device.script == ui.script || ui.script.isEmpty()
        return if (sameLanguage && sameScript) device else ui
    }

    fun string(keyAndValue: String, vararg arguments: Any?, table: String? = null, language: AppLanguage? = null): String {
        val selected = language ?: AppLanguage.current
        val identifier = selected.localizationIdentifier ?: shownIdentifier()
        val entry = (localizationBundles[identifier]?.get(table ?: "Localizable") as? JsonObject)?.get(keyAndValue)
        val locale = Locale.forLanguageTag(identifier)
        val template = when (entry) {
            is JsonPrimitive -> entry.content
            is JsonObject -> {
                val plurals = entry["plural"] as? JsonObject
                val count = (arguments.firstOrNull { it is Number } as? Number)?.toDouble() ?: 0.0
                val category = pluralCategory?.invoke(locale, count) ?: "other"
                (plurals?.get(category) ?: plurals?.get("other"))?.jsonPrimitive?.content ?: keyAndValue
            }
            else -> keyAndValue
        }
        return format(template, arguments, locale)
    }

    internal fun format(template: String, arguments: Array<out Any?>, locale: Locale): String {
        // Catalog output is already converted; also convert source-key fallbacks.
        val converted = swiftFormat.replace(template) { match ->
            "%" + match.groupValues[1] + match.groupValues[2] + when (match.groupValues[4]) {
                "@" -> "s"
                "i", "u" -> "d"
                else -> match.groupValues[4]
            }
        }
        return if (arguments.isEmpty()) converted.replace("%%", "%") else String.format(locale, converted, *arguments)
    }

    private val swiftFormat = Regex("%(\\d+\\$)?([-+ #0,(]*\\d*(?:\\.\\d+)?)(ll|l)?([@diufFeEgGs%])")
}
