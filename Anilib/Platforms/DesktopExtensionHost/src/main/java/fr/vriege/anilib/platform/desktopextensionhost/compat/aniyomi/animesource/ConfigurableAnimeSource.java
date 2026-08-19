package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.SharedPreferences;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.SourcePreferences;
import fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference.PreferenceScreen;

public interface ConfigurableAnimeSource extends AnimeSource {
    default SharedPreferences getSourcePreferences() {
        return SourcePreferences.forSource(getId());
    }

    void setupPreferenceScreen(PreferenceScreen screen);
}
