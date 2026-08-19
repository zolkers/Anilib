package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.SharedPreferences;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.SourcePreferences;
import fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference.PreferenceScreen;

public interface ConfigurableSource extends MangaSource {
    default SharedPreferences getSourcePreferences() {
        return SourcePreferences.forSource(getId());
    }

    void setupPreferenceScreen(PreferenceScreen screen);
}
