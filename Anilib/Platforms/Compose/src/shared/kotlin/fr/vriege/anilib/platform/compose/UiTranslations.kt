package fr.vriege.anilib.platform.compose

import fr.vriege.anilib.feature.settings.LanguagePack
import java.util.IdentityHashMap
import java.util.Locale

internal object UiTranslations {
    fun translate(text: String, configured: LanguagePack): String {
        val language = resolve(configured)
        if (language == LanguagePack.ENGLISH || text.isBlank()) return text
        translations[language]?.get(text)?.let { return it }
        return when (language) {
            LanguagePack.FRENCH -> translateFrenchDynamic(text)
            LanguagePack.SYSTEM, LanguagePack.ENGLISH -> text
        }
    }

    private fun resolve(configured: LanguagePack): LanguagePack {
        if (configured != LanguagePack.SYSTEM) return configured
        return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "fr" -> LanguagePack.FRENCH
            else -> LanguagePack.ENGLISH
        }
    }

    private val translations = mapOf(
        LanguagePack.FRENCH to identityMap(
            "Add" to "Ajouter",
            "All" to "Tous",
            "Anime" to "Anime",
            "Back" to "Retour",
            "Backup and restore" to "Sauvegarde et restauration",
            "Browse" to "Parcourir",
            "Cancel" to "Annuler",
            "Categories" to "Catégories",
            "Check for updates" to "Rechercher des mises à jour",
            "Close" to "Fermer",
            "Connected" to "Connecté",
            "Delete" to "Supprimer",
            "Disconnect" to "Déconnecter",
            "Description" to "Description",
            "Download" to "Télécharger",
            "Downloads" to "Téléchargements",
            "Edit" to "Modifier",
            "Episodes" to "Épisodes",
            "Extensions" to "Extensions",
            "Favorites" to "Favoris",
            "Filters" to "Filtres",
            "Help" to "Aide",
            "History" to "Historique",
            "Local progress stays on this device, but synchronization with the service stops." to
                "La progression locale reste sur cet appareil, mais la synchronisation avec le service s’arrête.",
            "Import" to "Importer",
            "Language" to "Langue",
            "Library" to "Bibliothèque",
            "Loading…" to "Chargement…",
            "Manga" to "Manga",
            "More" to "Plus",
            "Next" to "Suivant",
            "Privacy" to "Confidentialité",
            "Project" to "Projet",
            "Reader" to "Lecteur",
            "Remove" to "Retirer",
            "Restore" to "Restaurer",
            "Retry" to "Réessayer",
            "Save" to "Enregistrer",
            "Search" to "Rechercher",
            "Search anime extensions" to "Rechercher des extensions dâ€™anime",
            "Search manga extensions" to "Rechercher des extensions de manga",
            "Settings" to "Paramètres",
            "Share" to "Partager",
            "Statistics" to "Statistiques",
            "Tracking" to "Suivi",
            "Updates" to "Mises à jour",
            "Watch" to "Regarder",
            "A cross-platform anime and manga library built from explicit, removable feature bundles." to
                "Une bibliothèque d’anime et de manga multiplateforme composée de fonctionnalités " +
                "explicites et amovibles.",
            "Add repository" to "Ajouter un dépôt",
            "Add to Library" to "Ajouter à la bibliothèque",
            "Add tracking" to "Ajouter un suivi",
            "Advanced" to "Avancé",
            "All categories" to "Toutes les catégories",
            "All chapters" to "Tous les chapitres",
            "All completed files and partial jobs for this title will be removed." to
                "Tous les fichiers terminés et travaux partiels de ce titre seront supprimés.",
            "Always include" to "Toujours inclure",
            "Anilib ships with no source catalogue. Add only repository URLs and publisher keys you trust." to
                "Anilib ne fournit aucun catalogue de sources. Ajoutez uniquement des dépôts et clés de confiance.",
            "Apply to this title" to "Appliquer à ce titre",
            "Artists" to "Artistes",
            "Artwork URL" to "URL de l’illustration",
            "Authors" to "Auteurs",
            "Automatic" to "Automatique",
            "Automatic backup folder" to "Dossier de sauvegarde automatique",
            "Automatic downloads" to "Téléchargements automatiques",
            "Automatic source updates" to "Mises à jour automatiques des sources",
            "Automatic synchronization" to "Synchronisation automatique",
            "Back to library" to "Retour à la bibliothèque",
            "Backup policy" to "Politique de sauvegarde",
            "Backup schedule, content, retention, and destination" to
                "Planification, contenu, conservation et destination des sauvegardes",
            "Backups to keep (1–100)" to "Sauvegardes à conserver (1–100)",
            "Base64 X.509 public key" to "Clé publique X.509 en Base64",
            "Batch migration" to "Migration par lot",
            "Browser settings" to "Paramètres du navigateur",
            "Calculating statistics…" to "Calcul des statistiques…",
            "Category" to "Catégorie",
            "Category name" to "Nom de la catégorie",
            "Category rules" to "Règles de catégorie",
            "Change source" to "Changer de source",
            "Chapter limit" to "Limite de chapitres",
            "Chapters" to "Chapitres",
            ("Changing this path validates the destination, copies every indexed page, " +
                "then removes the old managed copies.") to
                ("Changer ce chemin valide la destination, copie chaque page indexée, puis supprime " +
                    "les anciennes copies gérées."),
            "Check" to "Vérifier",
            "Checks every 6 hours; only the same package and signing key update silently." to
                "Vérifie toutes les 6 heures ; seuls le même paquet et la même clé sont mis à jour silencieusement.",
            "Choose a target source" to "Choisir une source cible",
            "Clean now" to "Nettoyer maintenant",
            "Cleanup policy" to "Politique de nettoyage",
            "Clear" to "Effacer",
            "Clear cache and reports" to "Effacer le cache et les rapports",
            "Clear title override" to "Effacer le réglage propre au titre",
            "Cleartext network" to "Réseau non chiffré",
            "Completed files and partial data will be permanently removed." to
                "Les fichiers terminés et données partielles seront définitivement supprimés.",
            "Completed items retained per title" to "Éléments terminés conservés par titre",
            "Confirm safe reset" to "Confirmer la réinitialisation sûre",
            "Copyright 2026 Victor Riegert · Apache License 2.0" to
                "Copyright 2026 Victor Riegert · Licence Apache 2.0",
            "Create a local backup of your library, history, progress, and source settings." to
                "Créer une sauvegarde locale de la bibliothèque, de l’historique, de la progression et des sources.",
            "Create backup" to "Créer une sauvegarde",
            "Custom player buttons" to "Boutons personnalisés du lecteur vidéo",
            "Default" to "Par défaut",
            "Delete all" to "Tout supprimer",
            "Delete all downloads?" to "Supprimer tous les téléchargements ?",
            "Delete backup?" to "Supprimer la sauvegarde ?",
            "Desktop player controls" to "Commandes du lecteur vidéo de bureau",
            "Direction" to "Direction",
            "Dismiss" to "Ignorer",
            "Display" to "Affichage",
            "DNS-over-HTTPS URL (optional)" to "URL DNS-over-HTTPS (facultative)",
            "Done" to "Terminé",
            "Down" to "Descendre",
            "Download chapter" to "Télécharger le chapitre",
            "Download queue" to "File de téléchargement",
            "Download storage" to "Stockage des téléchargements",
            "Edit title" to "Modifier le titre",
            "Episode limit" to "Limite d’épisodes",
            "Exclude titles" to "Exclure des titres",
            "Existing entries are updated; newer entries not in this backup are kept." to
                "Les entrées existantes sont mises à jour ; les entrées plus récentes sont conservées.",
            "Existing titles from the same source are merged; other Anilib titles are kept." to
                "Les titres de la même source sont fusionnés ; les autres titres Anilib sont conservés.",
            "Export" to "Exporter",
            "Export diagnostics" to "Exporter les diagnostics",
            "Export uses the native document picker (including Android SAF)." to
                "L’exportation utilise le sélecteur de documents natif, y compris Android SAF.",
            "Extension languages" to "Langues des extensions",
            "Extension repositories" to "Dépôts d’extensions",
            "Favorite" to "Ajouter aux favoris",
            "Finish date (YYYY-MM-DD, optional)" to "Date de fin (AAAA-MM-JJ, facultative)",
            "Forget" to "Oublier",
            "Forget trust" to "Retirer la confiance",
            "Genres" to "Genres",
            "GitHub repository or index URL" to "URL du dépôt GitHub ou de l’index",
            "HTTP proxy (optional)" to "Proxy HTTP (facultatif)",
            "HTTPS endpoint" to "Point d’accès HTTPS",
            "Import Aniyomi backup" to "Importer une sauvegarde Aniyomi",
            "Import Aniyomi backup?" to "Importer la sauvegarde Aniyomi ?",
            "Included content" to "Contenu inclus",
            "Install" to "Installer",
            "Install APK" to "Installer l’APK",
            "Installed" to "Installé",
            "Interactions" to "Interactions",
            "Intro ends after (seconds)" to "Fin de l’introduction après (secondes)",
            "Keep local" to "Conserver la version locale",
            "Keep remote" to "Conserver la version distante",
            "Key ID" to "Identifiant de clé",
            "Languages" to "Langues",
            "Latest" to "Derniers ajouts",
            "Library update schedule" to "Planification des mises à jour de la bibliothèque",
            "Licence" to "Licence",
            "Manage repositories" to "Gérer les dépôts",
            "Migrate" to "Migrer",
            "Migrate previewed titles" to "Migrer les titres prévisualisés",
            "Migration preview" to "Aperçu de la migration",
            "Migration results" to "Résultats de la migration",
            "Network" to "Réseau",
            "Network policy" to "Politique réseau",
            "New category" to "Nouvelle catégorie",
            "Next chapter" to "Chapitre suivant",
            "Next frame" to "Image suivante",
            "No chapter list is available." to "Aucune liste de chapitres n’est disponible.",
            "No media backend is available." to "Aucun moteur multimédia n’est disponible.",
            "No sensitive permissions" to "Aucune autorisation sensible",
            "Off" to "Désactivé",
            "Offline mode" to "Mode hors ligne",
            "One per line: category:episodes:chapters" to "Une par ligne : catégorie:épisodes:chapitres",
            "Only import an Ed25519 key fingerprinted by a publisher you trust." to
                "Importez uniquement une clé Ed25519 identifiée par un éditeur de confiance.",
            "Open in WebView" to "Ouvrir dans la WebView",
            "Open release" to "Ouvrir la version",
            "Open source web" to "Ouvrir le site de la source",
            "Open title web" to "Ouvrir la page web du titre",
            "Outro duration (seconds)" to "Durée du générique de fin (secondes)",
            "Paste a trusted GitHub repository URL or a direct HTTPS JSON index URL." to
                "Collez l’URL d’un dépôt GitHub de confiance ou celle d’un index JSON HTTPS direct.",
            "Pause" to "Mettre en pause",
            "Permissions" to "Autorisations",
            "Player" to "Lecteur vidéo",
            "Player preferences" to "Préférences du lecteur vidéo",
            "Popular" to "Populaires",
            "Preferred audio language" to "Langue audio préférée",
            "Preferred subtitle language" to "Langue de sous-titres préférée",
            "Previous" to "Précédent",
            "Previous chapter" to "Chapitre précédent",
            "Private entry" to "Entrée privée",
            "Quality" to "Qualité",
            "Read" to "Lire",
            "Reader settings" to "Paramètres du lecteur",
            "Refresh" to "Actualiser",
            "Refresh bound titles after library activity and account sign-in." to
                "Actualiser les titres liés après une activité ou une connexion au compte.",
            "Related titles" to "Titres associés",
            "Rename" to "Renommer",
            "Rename category" to "Renommer la catégorie",
            "Repair download index" to "Réparer l’index des téléchargements",
            "Reset" to "Réinitialiser",
            "Reset settings" to "Réinitialiser les paramètres",
            "Restart" to "Redémarrer",
            "Restore backup?" to "Restaurer la sauvegarde ?",
            "Resume" to "Reprendre",
            "Retry" to "Réessayer",
            "Run diagnostic" to "Exécuter le diagnostic",
            "Run now" to "Exécuter maintenant",
            "Schedule" to "Planification",
            "Search all sources" to "Rechercher dans toutes les sources",
            "Search episodes" to "Rechercher des épisodes",
            "Search history" to "Rechercher dans l’historique",
            "Search library" to "Rechercher dans la bibliothèque",
            "Sort library" to "Trier la bibliothèque",
            "Change library layout" to "Changer la disposition de la bibliothèque",
            "Select library titles" to "Sélectionner des titres de la bibliothèque",
            "Clear history" to "Effacer l’historique",
            "Toggle favorite" to "Ajouter ou retirer des favoris",
            "Remove history entry" to "Retirer l’entrée de l’historique",
            "Add a repository to discover extensions." to
                "Ajoutez un dépôt pour découvrir des extensions.",
            "Search settings" to "Rechercher dans les paramètres",
            "Search title" to "Rechercher un titre",
            "Search your library" to "Rechercher dans votre bibliothèque",
            "Sign in" to "Se connecter",
            "Sign out" to "Se déconnecter",
            "Skip intro" to "Passer l’introduction",
            "Skip outro" to "Passer le générique de fin",
            "Source ID" to "Identifiant de source",
            "Source languages" to "Langues des sources",
            "Source settings" to "Paramètres de la source",
            "Start date (YYYY-MM-DD, optional)" to "Date de début (AAAA-MM-JJ, facultative)",
            "Status" to "Statut",
            "Storage" to "Stockage",
            "Storage and diagnostics" to "Stockage et diagnostics",
            "Storage directory" to "Dossier de stockage",
            "Subtitles" to "Sous-titres",
            "Sync" to "Synchroniser",
            "Synchronization conflicts" to "Conflits de synchronisation",
            "Synchronize now" to "Synchroniser maintenant",
            "Text zoom (50–200%)" to "Zoom du texte (50–200 %)",
            "The remote list entry will be deleted before the local binding is removed." to
                "L’entrée distante sera supprimée avant le retrait de la liaison locale.",
            "Third-party notices" to "Mentions de tiers",
            "This page could not be displayed." to "Cette page n’a pas pu être affichée.",
            "This removes the selected titles from your library." to
                "Cette action retire les titres sélectionnés de votre bibliothèque.",
            "This title is no longer in the library." to "Ce titre n’est plus dans la bibliothèque.",
            "Timeout in seconds" to "Délai d’attente en secondes",
            "Title" to "Titre",
            "Titles stay in the library and lose this category assignment." to
                "Les titres restent dans la bibliothèque et perdent cette catégorie.",
            "Trust" to "Faire confiance",
            "Trust certificate" to "Faire confiance au certificat",
            "Trust key" to "Faire confiance à la clé",
            "Trust publisher key" to "Faire confiance à la clé de l’éditeur",
            "Unfavorite" to "Retirer des favoris",
            "Unread" to "Non lus",
            "Unwatched" to "Non vus",
            "Up" to "Monter",
            "Update" to "Mettre à jour",
            "Update categories" to "Mettre à jour les catégories",
            "Update extension" to "Mettre à jour l’extension",
            "Use only for this title" to "Utiliser uniquement pour ce titre",
            "User agent" to "Agent utilisateur",
            "Username" to "Nom d’utilisateur",
            "Video quality" to "Qualité vidéo",
            "Volume" to "Volume",
            "Watched" to "Vus",
            "WebView unavailable" to "WebView indisponible",
            "When both sides changed" to "Lorsque les deux côtés ont changé",
            "Mark all read" to "Tout marquer comme lu",
            "Close browser" to "Fermer le navigateur",
            "The embedded browser is unavailable on Windows ARM64. Source browsing and downloads remain available." to
                "Le navigateur intégré n’est pas disponible sur Windows ARM64. " +
                "La navigation dans les sources et les téléchargements restent disponibles.",
            "Previous page" to "Page précédente",
            "Next page" to "Page suivante",
            "Reload" to "Recharger",
            "Check web challenge" to "Vérifier le défi web",
            "Pause all" to "Tout mettre en pause",
            "Resume all" to "Tout reprendre",
            "Close search" to "Fermer la recherche",
            "Manage extension repositories" to "Gérer les dépôts d’extensions",
            "Global search" to "Recherche globale",
            "Source actions" to "Actions de la source",
            "Rescan source" to "Analyser de nouveau la source",
            "Open source website" to "Ouvrir le site de la source",
            "Display mode" to "Mode d’affichage",
            "Extension actions" to "Actions de l’extension",
            "Extension icon" to "Icône de l’extension",
            "Extension icon unavailable" to "Icône de l’extension indisponible",
            "Close reader" to "Fermer le lecteur",
            "Reader menu" to "Menu du lecteur",
            "Refresh repositories" to "Actualiser les dépôts",
            "Delete backup" to "Supprimer la sauvegarde",
            "About" to "À propos",
            "About Anilib" to "À propos d’Anilib",
            "Accent color" to "Couleur d’accentuation",
            "Android and desktop use the same stream and subtitle policy." to
                "Android et le bureau utilisent la même politique de flux et de sous-titres.",
            "Anime / Manga" to "Anime / Manga",
            "Appearance" to "Apparence",
            "Appearance changes apply immediately on Android and desktop." to
                "Les changements d’apparence s’appliquent immédiatement sur Android et le bureau.",
            "Application data" to "Données de l’application",
            "Application" to "Application",
            "Add a compatible repository, then install an extension for this platform." to
                "Ajoutez un dépôt compatible, puis installez une extension adaptée à cette plateforme.",
            "Add compatible extension repositories and install sources" to
                "Ajouter des dépôts d’extensions compatibles et installer des sources",
            "Android-only extension" to "Extension réservée à Android",
            "Android-only extension · install it from Anilib on Android" to
                "Extension réservée à Android · installez-la depuis Anilib sur Android",
            "Browse extensions" to "Parcourir les extensions",
            ("Choose an extension below and select Install on Android. " +
                "Portable Anilib Bundles work on every platform.") to
                "Choisissez une extension ci-dessous puis Installer sur Android. " +
                "Les Bundles Anilib portables fonctionnent sur toutes les plateformes.",
            "Create or restore a local backup" to "Créer ou restaurer une sauvegarde locale",
            "Downloaded only" to "Téléchargés uniquement",
            "Install on Android" to "Installer sur Android",
            "Install for desktop" to "Installer sur le bureau",
            "Open title website" to "Ouvrir le site du titre",
            "Installed · sources active in Browse" to
                "Installée · sources actives dans Parcourir",
            "Installing APK in desktop engine" to "Installation de l’APK dans le moteur bureau",
            "Downloading verified desktop compatibility" to
                "Téléchargement de la compatibilité bureau vérifiée",
            "Desktop APK compatibility is ready to install. Select Install for desktop on any APK source." to
                "La compatibilité APK sur bureau est prête. Sélectionnez Installer sur le bureau sur une source APK.",
            ("Existing manga and anime APK extensions run in Anilib's isolated desktop engine. " +
                "Newly installed sources activate immediately.") to
                ("Les extensions APK manga et anime fonctionnent dans le moteur bureau isolé d’Anilib. " +
                    "Les nouvelles sources s’activent immédiatement."),
            "Library and reading activity" to "Bibliothèque et activité de lecture",
            "Manage external tracking accounts" to "Gérer les comptes de suivi externes",
            "No extensions installed" to "Aucune extension installée",
            "No pending downloads" to "Aucun téléchargement en attente",
            "Organize anime and manga in your library" to
                "Organiser les anime et manga de votre bibliothèque",
            "Pause reading and watching history" to "Suspendre l’historique de lecture et de visionnage",
            "Pin" to "Épingler",
            "Pin extension" to "Épingler l’extension",
            "Quick filters" to "Filtres rapides",
            "This device installs portable Anilib Bundles. APK-only entries require Anilib on Android." to
                "Cet appareil installe les Bundles Anilib portables. Les entrées APK nécessitent Anilib sur Android.",
            ("This repository entry contains an APK. Open the same repository in Anilib on Android to install it, " +
                "or use a repository that publishes portable Anilib Bundles.") to
                ("Cette entrée contient un APK. Ouvrez le même dépôt dans Anilib sur Android pour l’installer, " +
                    "ou utilisez un dépôt publiant des Bundles Anilib portables."),
            "Use downloaded content without the online fallback" to
                "Utiliser le contenu téléchargé sans recours au réseau",
            "Unpin" to "Désépingler",
            "Unpin extension" to "Désépingler l’extension",
            "Automatic challenge retry" to "Nouvelle tentative automatique après un défi",
            "Average title progress" to "Progression moyenne des titres",
            "Average tracker score" to "Note moyenne des services de suivi",
            "Backup" to "Sauvegarde",
            "Changelog" to "Journal des modifications",
            "Clean database" to "Nettoyer la base de données",
            "Clear cookies" to "Effacer les cookies",
            "Clear network cache" to "Effacer le cache réseau",
            "Clear WebView data" to "Effacer les données WebView",
            "Content" to "Contenu",
            "Content and privacy" to "Contenu et confidentialité",
            "Data and storage" to "Données et stockage",
            "Direction and position are retained per title." to
                "La direction et la position sont conservées pour chaque titre.",
            "DOM storage" to "Stockage DOM",
            "File chooser" to "Sélecteur de fichiers",
            "General" to "Général",
            "Incognito mode" to "Mode navigation privée",
            "JavaScript" to "JavaScript",
            "Known episode duration" to "Durée connue des épisodes",
            "Last 30 days" to "30 derniers jours",
            "Last 365 days" to "365 derniers jours",
            "Last 7 days" to "7 derniers jours",
            "Library and media" to "Bibliothèque et médias",
            "Library and updates" to "Bibliothèque et mises à jour",
            "Library updates" to "Mises à jour de la bibliothèque",
            "Manage downloads" to "Gérer les téléchargements",
            "Navigation" to "Navigation",
            "Network and browser" to "Réseau et navigateur",
            "No log or crash report is available." to
                "Aucun journal ni rapport de plantage n’est disponible.",
            "Per-source diagnostic" to "Diagnostic par source",
            "Platforms" to "Plateformes",
            "Player behavior" to "Comportement du lecteur vidéo",
            "Pop-ups" to "Fenêtres contextuelles",
            "Prefetch and retry" to "Préchargement et nouvelle tentative",
            "Quality and subtitles" to "Qualité et sous-titres",
            "Queue and storage" to "File d’attente et stockage",
            "Reader behavior" to "Comportement du lecteur",
            "Reading direction" to "Sens de lecture",
            "Reduce motion" to "Réduire les animations",
            "Reports" to "Rapports",
            "Response cache" to "Cache des réponses",
            "Runtime" to "Environnement d’exécution",
            "Schedule and skip controls remain available on the Updates screen." to
                "La planification et les exclusions restent disponibles sur l’écran des mises à jour.",
            "Services" to "Services",
            "Show adult content" to "Afficher le contenu pour adultes",
            "Source format" to "Format des sources",
            "Sources and repositories" to "Sources et dépôts",
            "Start screen" to "Écran de démarrage",
            "Start screen changes apply the next time Anilib opens." to
                "Le changement d’écran de démarrage s’appliquera au prochain lancement d’Anilib.",
            "Started" to "Commencés",
            "Theme" to "Thème",
            "Theme family" to "Famille de thèmes",
            "Titles" to "Titres",
            "Typography" to "Typographie",
            "Update channel" to "Canal de mise à jour",
            "Version" to "Version",
            "Wi-Fi only downloads" to "Téléchargements uniquement en Wi-Fi",
            "Wi-Fi only updates" to "Mises à jour uniquement en Wi-Fi",
        ),
    )

    private fun identityMap(vararg entries: Pair<String, String>): Map<String, String> =
        IdentityHashMap<String, String>().apply { entries.forEach { put(it.first, it.second) } }

    private fun translateFrenchDynamic(text: String): String {
        val exactPatterns = listOf(
            Regex("^(\\d+) titles$") to "\$1 titres",
            Regex("^(\\d+) selected$") to "\$1 sélectionnés",
            Regex("^(\\d+) episodes$") to "\$1 épisodes",
            Regex("^(\\d+) entries$") to "\$1 entrées",
            Regex("^Page (\\d+)$") to "Page \$1",
            Regex("^Retry (\\d+) failed$") to "Réessayer les \$1 échecs",
            Regex("^Update all \\((\\d+)\\)$") to "Tout mettre à jour (\$1)",
            Regex("^Preview (\\d+) selected$") to "Aperçu des \$1 éléments sélectionnés",
            Regex("^Skipped \\((\\d+)\\)$") to "Ignorés (\$1)",
            Regex("^Anime: (\\d+)$") to "Anime : \$1",
            Regex("^Manga: (\\d+)$") to "Manga : \$1",
            Regex("^Categories: (\\d+)$") to "Catégories : \$1",
            Regex("^History entries: (\\d+)$") to "Entrées d’historique : \$1",
            Regex("^Titles with progress: (\\d+)$") to "Titres avec progression : \$1",
            Regex("^(\\d+) pending downloads$") to "\$1 téléchargements en attente",
            Regex("^(\\d+) feature bundles active · appearance and app behavior$") to
                "\$1 bundles fonctionnels actifs · apparence et comportement de l’application",
        )
        exactPatterns.firstOrNull { it.first.matches(text) }?.let { return it.first.replace(text, it.second) }
        return when {
            text.startsWith("Status: ") -> "Statut : " + text.removePrefix("Status: ")
            text.startsWith("Progress: ") -> "Progression : " + text.removePrefix("Progress: ")
            text.startsWith("Source ") -> "Source " + text.removePrefix("Source ")
            text.startsWith("Provider: ") -> "Fournisseur : " + text.removePrefix("Provider: ")
            text.startsWith("Quality: ") -> "Qualité : " + text.removePrefix("Quality: ")
            text.startsWith("Decoder: ") -> "Décodeur : " + text.removePrefix("Decoder: ")
            text.startsWith("Subtitles: ") -> "Sous-titres : " + text.removePrefix("Subtitles: ")
            text.startsWith("Scale: ") -> "Échelle : " + text.removePrefix("Scale: ")
            text.startsWith("Rotation: ") -> "Rotation : " + text.removePrefix("Rotation: ")
            text.startsWith("Transition: ") -> "Transition : " + text.removePrefix("Transition: ")
            text.startsWith("Orientation: ") -> "Orientation : " + text.removePrefix("Orientation: ")
            text.startsWith("Brightness: ") -> "Luminosité : " + text.removePrefix("Brightness: ")
            text.startsWith("Color filter: ") -> "Filtre de couleur : " + text.removePrefix("Color filter: ")
            text.startsWith("Resume at ") -> "Reprendre à " + text.removePrefix("Resume at ")
            text.startsWith("Remote page: ") -> "Page distante : " + text.removePrefix("Remote page: ")
            text.startsWith("Page failed to load ") ->
                "Échec du chargement de la page " + text.removePrefix("Page failed to load ")
            text.startsWith("Required Source API ") -> "API Source requise " + text.removePrefix("Required Source API ")
            text.startsWith("Downloaded ") -> "Téléchargé : " + text.removePrefix("Downloaded ")
            text.startsWith("Available: ") -> "Disponible : " + text.removePrefix("Available: ")
            text.startsWith("Current: ") -> "Actuelle : " + text.removePrefix("Current: ")
            text.startsWith("Target: ") -> "Cible : " + text.removePrefix("Target: ")
            text.startsWith("Match: ") -> "Correspondance : " + text.removePrefix("Match: ")
            text.startsWith("Installed publisher identity: ") ->
                "Identité de l’éditeur installé : " + text.removePrefix("Installed publisher identity: ")
            text.startsWith("Sign in to ") -> "Se connecter à " + text.removePrefix("Sign in to ")
            text.startsWith("Delete downloads for ") ->
                "Supprimer les téléchargements de " + text.removePrefix("Delete downloads for ")
            text.startsWith("Delete ") && text.endsWith(" titles?") ->
                "Supprimer " + text.removePrefix("Delete ").removeSuffix(" titles?") + " titres ?"
            text.startsWith("Track ") && text.endsWith("?") ->
                "Suivre " + text.removePrefix("Track ")
            text.startsWith("Trust ") && text.endsWith("?") ->
                "Faire confiance à " + text.removePrefix("Trust ")
            text.startsWith("Remove ") && text.endsWith(" tracking?") ->
                "Retirer le suivi de " + text.removePrefix("Remove ").removeSuffix(" tracking?") + " ?"
            text.startsWith("Edit ") && text.endsWith(" tracking") ->
                "Modifier le suivi de " + text.removePrefix("Edit ").removeSuffix(" tracking")
            text.startsWith("Search in ") -> "Rechercher dans " + text.removePrefix("Search in ")
            text.startsWith("Actions for ") -> "Actions pour " + text.removePrefix("Actions for ")
            text.endsWith(" artwork") -> "Illustration de " + text.removeSuffix(" artwork")
            else -> text
        }
    }
}
