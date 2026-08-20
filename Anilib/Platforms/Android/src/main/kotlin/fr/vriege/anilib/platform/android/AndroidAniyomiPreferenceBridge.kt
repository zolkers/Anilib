package fr.vriege.anilib.platform.android

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiSourcePreferences
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition
import fr.vriege.anilib.feature.source.SourcePreferenceType
import java.lang.reflect.InvocationTargetException
import java.util.function.Consumer
import java.lang.reflect.Method

internal class AndroidAniyomiPreferenceBridge(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    fun project(source: Any): AniyomiSourcePreferences {
        val setup = source.javaClass.methods.singleOrNull { method ->
                method.name == "setupPreferenceScreen" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isAssignableFrom(PreferenceScreen::class.java)
        } ?: return AniyomiSourcePreferences.empty()
        val sourcePreferences = sourcePreferences(source)
        val screen = preferenceScreen()
        invokeSetup(setup, source, screen)
        val definitions = controls(screen).mapNotNull { preference ->
            definition(preference, sourcePreferences)
        }
        require(definitions.map(SourcePreferenceDefinition::id).toSet().size == definitions.size) {
            "Aniyomi source preference keys must be unique"
        }
        val byId = definitions.associateBy(SourcePreferenceDefinition::id)
        return AniyomiSourcePreferences(
            definitions,
            Consumer { values -> writeValues(sourcePreferences, byId, values) },
        )
    }

    private fun preferenceScreen(): PreferenceScreen {
        val managerClass = Class.forName("androidx.preference.PreferenceManager")
        val manager = managerClass.getConstructor(Context::class.java).newInstance(applicationContext)
        return managerClass
            .getMethod("createPreferenceScreen", Context::class.java)
            .invoke(manager, applicationContext) as PreferenceScreen
    }

    private fun sourcePreferences(source: Any): SharedPreferences {
        val direct = source.javaClass.methods.singleOrNull { method ->
            method.name == "getSourcePreferences" && method.parameterCount == 0
        }
        if (direct != null) {
            runCatching { direct.invoke(source) as SharedPreferences }
                .getOrNull()
                ?.let { return it }
        }
        val sourceId = source.javaClass.methods
            .single { it.name == "getId" && it.parameterCount == 0 }
            .invoke(source)
        return applicationContext.getSharedPreferences("source_$sourceId", Context.MODE_PRIVATE)
    }

    private fun invokeSetup(method: Method, source: Any, screen: Any) {
        try {
            method.invoke(source, screen)
        } catch (failure: InvocationTargetException) {
            throw failure.cause ?: failure
        }
    }

    private fun controls(group: PreferenceGroup): List<Preference> = buildList {
        repeat(group.preferenceCount) { index ->
            val preference = group.getPreference(index)
            if (preference is PreferenceGroup) {
                addAll(controls(preference))
            } else {
                add(preference)
            }
        }
    }

    private fun definition(
        preference: Preference,
        values: SharedPreferences,
    ): SourcePreferenceDefinition? {
        val key = preference.key?.trim().orEmpty()
        if (key.isBlank()) {
            return null
        }
        val title = preference.title?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: key
        val summary = preference.summary?.toString().orEmpty()
        return when (preference) {
            is SwitchPreferenceCompat -> SourcePreferenceDefinition(
                key,
                title,
                summary,
                SourcePreferenceType.SWITCH,
                emptyList(),
                values.getBoolean(key, preference.isChecked).toString(),
                false,
            )
            is CheckBoxPreference -> SourcePreferenceDefinition(
                key,
                title,
                summary,
                SourcePreferenceType.SWITCH,
                emptyList(),
                values.getBoolean(key, preference.isChecked).toString(),
                false,
            )
            is EditTextPreference -> SourcePreferenceDefinition(
                key,
                title,
                summary,
                SourcePreferenceType.TEXT,
                emptyList(),
                values.getString(key, preference.text.orEmpty()).orEmpty(),
                false,
            )
            is ListPreference -> listDefinition(preference, values, key, title, summary)
            else -> null
        }
    }

    private fun listDefinition(
        preference: ListPreference,
        values: SharedPreferences,
        key: String,
        title: String,
        summary: String,
    ): SourcePreferenceDefinition? {
        val options = preference.entryValues?.map(CharSequence::toString).orEmpty()
        if (options.isEmpty() || options.toSet().size != options.size) {
            return null
        }
        val fallback = preference.value?.takeIf(options::contains) ?: options.first()
        val selected = values.getString(key, fallback)?.takeIf(options::contains) ?: fallback
        return SourcePreferenceDefinition(
            key,
            title,
            summary,
            SourcePreferenceType.SELECT,
            options,
            selected,
            false,
        )
    }

    private fun writeValues(
        preferences: SharedPreferences,
        definitions: Map<String, SourcePreferenceDefinition>,
        values: Map<String, String>,
    ) {
        val editor = preferences.edit()
        values.forEach { (key, value) ->
            when (definitions[key]?.type()) {
                SourcePreferenceType.SWITCH -> editor.putBoolean(key, value.toBooleanStrict())
                SourcePreferenceType.TEXT,
                SourcePreferenceType.SELECT,
                -> editor.putString(key, value)
                null -> Unit
            }
        }
        check(editor.commit()) { "Unable to persist Aniyomi source preferences" }
    }
}
