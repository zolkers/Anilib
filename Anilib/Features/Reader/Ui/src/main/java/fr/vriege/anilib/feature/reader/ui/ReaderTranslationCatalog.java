package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.framework.localization.TranslationCatalog;

import java.util.Map;

public final class ReaderTranslationCatalog {
    private ReaderTranslationCatalog() {
    }

    public static TranslationCatalog catalog() {
        return TranslationCatalog.french("feature.reader", Map.ofEntries(
                Map.entry("Chapters", "Chapitres"),
                Map.entry("Choose LTR, RTL, vertical, or webtoon in Reader",
                        "Choisir gauche-droite, droite-gauche, vertical ou webtoon dans le lecteur"),
                Map.entry("Close reader", "Fermer le lecteur"),
                Map.entry("Direction", "Direction"),
                Map.entry("Next chapter", "Chapitre suivant"),
                Map.entry("Next page", "Page suivante"),
                Map.entry("Managed by the shared Reader pipeline", "Géré par le moteur de lecture partagé"),
                Map.entry("Previous chapter", "Chapitre précédent"),
                Map.entry("Previous page", "Page précédente"),
                Map.entry("Read", "Lire"),
                Map.entry("Reader", "Lecteur"),
                Map.entry("Reader menu", "Menu du lecteur"),
                Map.entry("Reading behavior and per-title controls", "Lecture et réglages propres à chaque titre"),
                Map.entry("Reading direction", "Sens de lecture")
        ));
    }
}
