package fr.vriege.anilib.platform.compose

import fr.vriege.anilib.feature.applicationupdate.ui.ApplicationUpdateTranslationCatalog
import fr.vriege.anilib.feature.backup.ui.BackupTranslationCatalog
import fr.vriege.anilib.feature.discovery.ui.DiscoveryTranslationCatalog
import fr.vriege.anilib.feature.downloads.ui.DownloadsTranslationCatalog
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryTranslationCatalog
import fr.vriege.anilib.feature.library.ui.LibraryTranslationCatalog
import fr.vriege.anilib.feature.player.ui.PlayerTranslationCatalog
import fr.vriege.anilib.feature.reader.ui.ReaderTranslationCatalog
import fr.vriege.anilib.feature.settings.LanguagePack
import fr.vriege.anilib.feature.settings.ui.SettingsTranslationCatalog
import fr.vriege.anilib.feature.tracker.ui.TrackerTranslationCatalog
import fr.vriege.anilib.feature.updates.ui.UpdatesTranslationCatalog
import fr.vriege.anilib.framework.localization.TranslationCatalog
import fr.vriege.anilib.framework.localization.Translator
import java.util.Locale

internal object UiTranslations {
    private val translator = Translator(
        listOf(
            ApplicationUpdateTranslationCatalog.catalog(),
            BackupTranslationCatalog.catalog(),
            DiscoveryTranslationCatalog.catalog(),
            DownloadsTranslationCatalog.catalog(),
            ExtensionRepositoryTranslationCatalog.catalog(),
            LibraryTranslationCatalog.catalog(),
            PlayerTranslationCatalog.catalog(),
            ReaderTranslationCatalog.catalog(),
            SettingsTranslationCatalog.catalog(),
            TrackerTranslationCatalog.catalog(),
            UpdatesTranslationCatalog.catalog(),
            TranslationCatalog.resources(
                "platform.compose",
                UiTranslations::class.java,
                "META-INF/anilib/i18n/platform-compose",
            ),
        ),
    )

    fun translate(keyOrEnglishMessage: String, configured: LanguagePack): String {
        if (keyOrEnglishMessage.isBlank()) return keyOrEnglishMessage
        return translator.translate(resolve(configured), keyOrEnglishMessage)
    }

    fun format(key: String, configured: LanguagePack, vararg arguments: Any?): String =
        translator.format(resolve(configured), key, arguments.map { it?.toString().orEmpty() })

    private fun resolve(configured: LanguagePack): String = when {
        configured == LanguagePack.FRENCH -> "fr"
        configured == LanguagePack.ENGLISH -> "en"
        Locale.getDefault().language.equals("fr", ignoreCase = true) -> "fr"
        else -> "en"
    }
}
