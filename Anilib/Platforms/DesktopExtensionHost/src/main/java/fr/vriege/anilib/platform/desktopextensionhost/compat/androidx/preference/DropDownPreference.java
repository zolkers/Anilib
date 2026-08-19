package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public final class DropDownPreference extends ListPreference {
    public DropDownPreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public DropDownPreference(Context context) { super(context); }
}
