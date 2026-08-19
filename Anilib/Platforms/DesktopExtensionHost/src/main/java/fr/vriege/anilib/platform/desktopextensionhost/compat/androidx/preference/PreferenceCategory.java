package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public final class PreferenceCategory extends PreferenceGroup {
    public PreferenceCategory(Context context, AttributeSet attributes) { super(context, attributes); }
    public PreferenceCategory(Context context) { super(context); }
}
