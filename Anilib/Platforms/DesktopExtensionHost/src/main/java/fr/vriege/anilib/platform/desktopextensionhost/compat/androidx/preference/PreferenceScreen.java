package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public final class PreferenceScreen extends PreferenceGroup {
    public PreferenceScreen(Context context, AttributeSet attributes) { super(context, attributes); }
    public PreferenceScreen(Context context) { super(context); }
    public PreferenceScreen() { super(null); }
}
