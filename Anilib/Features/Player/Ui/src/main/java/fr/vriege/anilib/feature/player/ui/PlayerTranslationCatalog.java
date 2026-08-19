package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class PlayerTranslationCatalog {
    private PlayerTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.player", Map.ofEntries(
                Map.entry("Episodes", "Épisodes"),
                Map.entry("Choose them from the episode screen", "Les choisir depuis l’écran de l’épisode"),
                Map.entry("No media backend is available.", "Aucun moteur multimédia n’est disponible."),
                Map.entry("Player", "Lecteur vidéo"),
                Map.entry("Playback behavior and per-episode controls",
                        "Lecture vidéo et réglages propres à chaque épisode"),
                Map.entry("Playback position is retained per episode", "La position est conservée pour chaque épisode"),
                Map.entry("Quality", "Qualité"),
                Map.entry("Resume", "Reprendre"),
                Map.entry("Search episodes", "Rechercher des épisodes"),
                Map.entry("Skip intro", "Passer l’introduction"),
                Map.entry("Skip outro", "Passer le générique de fin"),
                Map.entry("Subtitles", "Sous-titres"),
                Map.entry("Video quality", "Qualité vidéo"),
                Map.entry("Volume", "Volume"),
                Map.entry("Watch", "Regarder"),
                Map.entry("Watched", "Vus")
        ));
    }
}
